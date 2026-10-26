/**
 * 图片代理的并发闸门与内存缓存。
 *
 * 首页一次要拉几十张封面，全部经 /api/proxy 转发：
 * - 无节制并发会把本机代理/连接池打满，表现为滚动时掉帧；
 * - 同一张图（同一影片在多处出现）会被重复转发多次。
 *
 * 这里做两件事：
 * - 信号量：同时在途的图片请求不超过 PROXY_MAX_CONCURRENT，其余排队；
 * - LRU 缓存：按 URL 缓存小体积图片，重复请求直接命中，不再出网。
 *
 * 只用于图片；视频分片、m3u8、直播流一律不走这里（流式语义与缓存都不适用）。
 */

/** 同时在途的图片代理请求上限 */
const PROXY_MAX_CONCURRENT = 8;

/** 缓存条目数上限（每条都带 buffer，按字节总量另行限制） */
const CACHE_MAX_ENTRIES = 300;

/** 缓存总体积上限：超过后按 LRU 淘汰最旧的条目 */
const CACHE_MAX_BYTES = 48 * 1024 * 1024;

/** 单张图片超过这个体积就不缓存（避免大图占满内存） */
const CACHE_MAX_ITEM_BYTES = 4 * 1024 * 1024;

interface CacheEntry {
  body: ArrayBuffer;
  contentType: string;
  etag: string | null;
  lastModified: string | null;
  at: number;
}

const cache = new Map<string, CacheEntry>();
let cacheBytes = 0;

function evictIfNeeded() {
  while (cache.size > CACHE_MAX_ENTRIES || cacheBytes > CACHE_MAX_BYTES) {
    const oldest = cache.keys().next();
    if (oldest.done) break;
    const entry = cache.get(oldest.value);
    cache.delete(oldest.value);
    cacheBytes -= entry ? entry.body.byteLength : 0;
  }
}

/** 命中缓存则返回条目并把它移到最新（LRU） */
export function getCachedImage(url: string): CacheEntry | undefined {
  const hit = cache.get(url);
  if (!hit) return undefined;
  cache.delete(url);
  cache.set(url, hit);
  return hit;
}

/** 写入缓存；超过单条体积上限的直接忽略 */
export function putCachedImage(
  url: string,
  body: ArrayBuffer,
  contentType: string,
  etag: string | null,
  lastModified: string | null
) {
  if (body.byteLength > CACHE_MAX_ITEM_BYTES) return;
  const existing = cache.get(url);
  if (existing) cacheBytes -= existing.body.byteLength;
  cache.set(url, { body, contentType, etag, lastModified, at: Date.now() });
  cacheBytes += body.byteLength;
  evictIfNeeded();
}

/** 当前缓存统计（便于排查与测试） */
export function imageCacheStats() {
  return { entries: cache.size, bytes: cacheBytes };
}

// —— 并发闸门 ——

let active = 0;
const waiting: Array<() => void> = [];

/** 当前在途的图片代理请求数 */
export function activeImageProxies() {
  return active;
}

/**
 * 在信号量内执行：超出并发上限时排队等待。
 * 无论成功失败都会释放名额，避免异常路径把闸门永久卡死。
 */
export async function withImageProxySlot<T>(fn: () => Promise<T>): Promise<T> {
  if (active >= PROXY_MAX_CONCURRENT) {
    await new Promise<void>((resolve) => waiting.push(resolve));
  }
  active++;
  try {
    return await fn();
  } finally {
    active--;
    const next = waiting.shift();
    if (next) next();
  }
}

/** 判断 URL 是否像图片（用于决定是否走缓存与闸门） */
export function looksLikeImageTarget(url: string): boolean {
  try {
    const { pathname, hostname } = new URL(url);
    if (/\.(jpe?g|png|gif|webp|avif|bmp|svg)(\?|$)/i.test(pathname)) return true;
    // 图床常见路径：/upload/xxx.jpg、/pic/xxx.webp 等，这里再兜一层常见后缀
    return /\.(jpe?g|png|gif|webp|avif|bmp|svg)$/i.test(pathname) || hostname.includes('img');
  } catch {
    return false;
  }
}
