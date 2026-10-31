'use client';

import { useEffect, useMemo, useRef, useState } from 'react';
import type { QualityEntry, SearchResultItem, SourceConfig } from '@/lib/types';
import { api } from '@/lib/client-api';
import { useAppStore, readQualities } from '@/lib/store';
import { QUALITY_FAIL_TTL_MS, qualityKey, qualityLabel } from '@/lib/quality';
import { formatRemarks, inferGroupRemarks } from '@/lib/remarks';
import { cn } from '@/lib/utils';
import { SmartImage } from './smart-image';

// —— 跨源同名聚合 ——

export interface AggregatedGroup {
  key: string;
  name: string;
  year?: string;
  typeName?: string;
  pic?: string;
  remarks?: string;
  items: SearchResultItem[];
}

function buildGroup(name: string, year: string | undefined, items: SearchResultItem[]): AggregatedGroup {
  return {
    key: `${name}|${year ?? ''}|${items.map((i) => `${i.sourceKey}_${i.vodId}`).join(',')}`,
    name,
    year,
    typeName: items.find((i) => i.typeName)?.typeName,
    pic: items.find((i) => i.pic)?.pic,
    // 同一部剧各源文案详略不同（「全34集」/「完结」/「更新至34集」），推到统一结论，
    // 否则同一部剧的来源列表会看起来状态各异
    remarks: inferGroupRemarks(items.map((i) => i.remarks)),
    items,
  };
}

/**
 * 把扁平的跨源搜索结果按「同名影片」聚合：
 * - 以名称分桶；桶内年份不一致时（同名翻拍）按年份拆分，无年份的条目并入年份桶；
 * - 保持传入顺序（搜索结果已按名称排序）。
 */
export function aggregateResults(list: SearchResultItem[]): AggregatedGroup[] {
  const byName = new Map<string, SearchResultItem[]>();
  for (const item of list) {
    const k = (item.name || '').trim();
    if (!k) continue;
    if (!byName.has(k)) byName.set(k, []);
    byName.get(k)!.push(item);
  }
  const groups: AggregatedGroup[] = [];
  for (const [name, items] of byName) {
    const years = [...new Set(items.map((i) => i.year).filter(Boolean))] as string[];
    if (years.length > 1) {
      for (const y of years) {
        groups.push(buildGroup(name, y, items.filter((i) => i.year === y)));
      }
      const noYear = items.filter((i) => !i.year);
      if (noYear.length) groups.push(buildGroup(name, undefined, noYear));
    } else {
      groups.push(buildGroup(name, years[0], items));
    }
  }
  return groups;
}

/**
 * 画质徽章。
 * 四态：探测中 → 「…」；源站没写画质信息 → 「未知」；探测本身失败 → 「未探到」；
 * 尚未探测 → 不渲染（展开即发起探测，未探测态几乎不可见）。
 *
 * 后两种在数值上完全一样（都没有 height），但成因完全不同：前者是源站是单码率流、
 * 服务端无论如何测不出；后者是请求被拒/超时。文案与 tooltip 分开，否则用户无法判断
 * 是该等一等重试，还是只能播一次才知道。
 */
function QualityBadge({ entry, loading }: { entry?: QualityEntry; loading?: boolean }) {
  if (loading) {
    return <span className="tag bg-chip text-faint shrink-0 animate-pulse">…</span>;
  }
  if (!entry) return null;
  const label = qualityLabel(entry.height);
  if (!label) {
    return (
      <span
        className="tag bg-chip text-faint shrink-0"
        title={
          entry.error
            ? `画质探测失败：${entry.error}（可能是防盗链或源站超时）`
            : '单码率或直链，播放列表里没有画质信息；播放一次后会记录实测分辨率'
        }
      >
        {entry.error ? '未探到' : '未知'}
      </span>
    );
  }
  return (
    <span
      className={cn(
        'tag font-medium shrink-0',
        (entry.height ?? 0) >= 1080 ? 'bg-accent/15 text-accent' : 'bg-chip text-muted'
      )}
      title={entry.from === 'play' ? '播放时实测的真实分辨率' : '解析播放列表得到的档位'}
    >
      {label}
    </span>
  );
}

/**
 * 聚合影片卡片：
 * - 单源：点击直接打开该源详情（与旧体验一致）；
 * - 多源：显示「N 个来源」徽章，点击展开各源列表，选择具体源后打开详情。
 */
export function AggregatedCard({
  group,
  onOpen,
  sources,
}: {
  group: AggregatedGroup;
  onOpen: (item: SearchResultItem) => void;
  /** 完整源配置：探测画质需要 source.url / source.detail，搜索结果里只带 sourceUrl */
  sources?: SourceConfig[];
}) {
  const imageProxyMode = useAppStore((s) => s.imageProxyMode);
  const customImageProxy = useAppStore((s) => s.customImageProxy);
  const [imgFailed, setImgFailed] = useState(false);
  const [expanded, setExpanded] = useState(false);
  const showImg = !!group.pic && !imgFailed;

  // 画质状态：按 `sourceKey:vodId` 存放。探测完成后一定会有条目
  // （height 缺失代表「测不出」），所以「无条目」= 尚未探测。
  const [qualities, setQualities] = useState<Record<string, QualityEntry>>({});
  const [probing, setProbing] = useState<Record<string, true>>({});
  // 正在探测中的键：只用于防同一个键并发重复请求，拿到结果或被掐掉就移除
  const inflightRef = useRef<Set<string>>(new Set());
  // 拿到终态结果（有画质 / 测不出 / 失败）的键 → 时间。折叠再展开不重复打源站；
  // 失败是瞬时的，永久锁死会让偶发失败的源再也拿不到画质，所以冷却满 QUALITY_FAIL_TTL_MS 才重试。
  // 只记「拿到结果」的键：请求被掐掉时没有终态，记下来会让该源被锁到冷却结束
  const settledRef = useRef<Map<string, number>>(new Map());
  // 当前批次的键：被下一批掐掉时，靠它把未完成的键解锁
  const batchRef = useRef<Set<string>>(new Set());
  const abortRef = useRef<AbortController | null>(null);

  useEffect(() => () => abortRef.current?.abort(), []);

  /**
   * 展开时懒探测各来源画质。
   * 不做全页预探测：一次搜索可能几十部片 × 十几个源，全量打会把源站和服务端都压垮，
   * 而用户真正关心的只有他点开的那一张卡片。
   */
  const startQualityProbe = () => {
    const cached = readQualities(group.items.map((i) => ({ sourceKey: i.sourceKey, vodId: i.vodId })));
    setQualities((prev) => ({ ...cached, ...prev }));

    const finish = (targets: string[]) => {
      setProbing((prev) => {
        const next = { ...prev };
        for (const k of targets) delete next[k];
        return next;
      });
    };

    const now = Date.now();
    const selectPending = () =>
      group.items.filter((i) => {
        const key = qualityKey(i.sourceKey, i.vodId);
        const known = cached[key] ?? qualities[key];
        // 已有结论就不再探：成功和「测不出」都是终态，重探也还是同一个结果
        if (known && !known.error) return false;
        // 已在飞：等这一批的结果，不重复打
        if (inflightRef.current.has(key)) return false;
        const last = settledRef.current.get(key);
        if (last === undefined) return true;
        // 探测失败过：冷却满 QUALITY_FAIL_TTL_MS 才重试，
        // 否则每次展开都要重打一遍挂掉的源，白等一轮超时
        return now - last > QUALITY_FAIL_TTL_MS;
      });

    // 没有新要探的键：让上一批跑完，不打断它（结果回来照样更新徽章）
    if (selectPending().length === 0) return;

    // 掐掉上一批：本轮要重发，留着只会互相打断。
    // 同时把它未拿到结果的键解锁——否则这些键既不在飞、又因 inflight 选不进本批，
    // 徽章会一直空着，直到冷却结束才有机会重探。
    // probing 必须在同一段同步解锁：上一批的 finally 会因下方守卫跳过清理，
    // 不在这里清的话，被本批重新调度的键会一直挂着「探测中」标记
    const previous = batchRef.current;
    if (previous.size > 0) {
      for (const k of previous) inflightRef.current.delete(k);
      finish([...previous]);
    }
    batchRef.current.clear();
    abortRef.current?.abort();

    const items = selectPending()
      .map((i) => {
        const key = qualityKey(i.sourceKey, i.vodId);
        const source =
          sources?.find((s) => s.key === i.sourceKey) ??
          // 源配置缺失时用搜索结果自带的地址兜底（缺 detail，走不了详情页降级）
          (i.sourceUrl ? { key: i.sourceKey, name: i.sourceName, url: i.sourceUrl } : undefined);
        return source ? { key, item: { source, id: i.vodId } } : undefined;
      })
      .filter((v): v is { key: string; item: { source: SourceConfig; id: string } } => Boolean(v));

    if (items.length === 0) return;
    const keys = items.map((i) => i.key);
    // 探测结果先攒在这里，流结束时一次性写全局缓存：逐条写会让 persist
    // 把整个状态（含最多 400 条画质记录）序列化十几遍
    const collected: Record<string, QualityEntry> = {};
    const batch = new Set(keys);
    for (const k of keys) inflightRef.current.add(k);
    batchRef.current = batch;
    setProbing((prev) => {
      const next = { ...prev };
      for (const k of keys) next[k] = true;
      return next;
    });

    const controller = new AbortController();
    abortRef.current = controller;

    api
      .qualityProbeStream(
        items.map((i) => i.item),
        (result) => {
          const key = qualityKey(result.sourceKey, result.vodId);
          const entry: QualityEntry = {
            height: result.height,
            from: 'probe',
            error: result.error,
            timestamp: Date.now(),
          };
          // 逐条更新本卡片的徽章（流式效果保留）
          setQualities((prev) => ({ ...prev, [key]: entry }));
          collected[key] = entry;
          // 拿到终态：解锁并发标记，并记下冷却起点
          inflightRef.current.delete(key);
          settledRef.current.set(key, Date.now());
          finish([key]);
        },
        controller.signal
      )
      .catch(() => {
        // 探测整批失败（离线/未登录）或被新一轮掐掉：保持「未知」，不打断选源
      })
      .finally(() => {
        // 一次性写入全局缓存：同一部剧在别处（换源面板、再次搜索）也能复用。
        // 中断时也会走到这里，已拿到的结果不会丢
        if (Object.keys(collected).length > 0) useAppStore.getState().setQuality(collected);
        // 被新一批接管时跳过清理：未完成的键已由新批的同步段解锁并重新调度，
        // 此时新批可能已把同名键加回 inflight / probing，再动会误清新批的标记
        // （finally 是 microtask，必然晚于新批的同步段执行）
        if (batchRef.current !== batch) return;
        // 本批未拿到终态的键一律解锁，下次展开可以立刻重试
        for (const k of batch) inflightRef.current.delete(k);
        batchRef.current.clear();
        finish(keys);
      });
  };

  const multi = group.items.length > 1;
  const adult = useMemo(() => group.items.some((i) => i.isAdult), [group.items]);

  const activate = () => {
    if (multi) {
      const next = !expanded;
      setExpanded(next);
      if (next) startQualityProbe();
    } else {
      onOpen(group.items[0]);
    }
  };

  return (
    <div className={cn('card', !multi && 'hover:scale-[1.02] hover:shadow-md', multi && expanded && 'ring-1 ring-accent/40')}>
      {/* 不用 h-full：展开面板需要撑高卡片，等高裁切会让面板不可见 */}
      <div
        className="flex h-full cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-accent/60"
        role="button"
        tabIndex={0}
        aria-expanded={multi ? expanded : undefined}
        onClick={activate}
        onKeyDown={(e) => {
          if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            activate();
          }
        }}
      >
        {showImg ? (
          <div className="relative flex-shrink-0 w-[105px] sm:w-[120px] aspect-[2/3] bg-chip">
            <SmartImage
              url={group.pic}
              mode={imageProxyMode}
              customProxy={customImageProxy}
              alt={group.name}
              className="absolute inset-0 h-full w-full object-cover"
              onExhausted={() => setImgFailed(true)}
            />
            <div className="absolute inset-0 bg-gradient-to-r from-black/30 to-transparent" />
            {multi && (
              <span className="absolute top-1.5 left-1.5 tag bg-black/70 text-accent font-medium">
                {group.items.length} 源
              </span>
            )}
          </div>
        ) : (
          <div className="relative flex-shrink-0 w-[105px] sm:w-[120px] aspect-[2/3] bg-chip flex items-center justify-center">
            <svg className="w-8 h-8 text-faint" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M7 4v16m10-16v16M3 6a1 1 0 011-1h1a1 1 0 011 1v12a1 1 0 01-1 1H4a1 1 0 01-1-1V6zm4 0a1 1 0 011-1h1a1 1 0 011 1v12a1 1 0 01-1 1h-1a1 1 0 01-1-1V6zm8 0a1 1 0 011-1h1a1 1 0 011 1v12a1 1 0 01-1 1h-1a1 1 0 01-1-1V6zm4 0a1 1 0 011-1h1a1 1 0 011 1v12a1 1 0 01-1 1h-1a1 1 0 01-1-1V6z" />
            </svg>
            {multi && (
              <span className="absolute top-1.5 left-1.5 tag bg-black/70 text-accent font-medium">
                {group.items.length} 源
              </span>
            )}
          </div>
        )}

        <div className="p-3.5 flex flex-col flex-grow min-w-0">
          <div className="flex-grow">
            <h3 className="font-semibold text-[15px] text-content mb-2 line-clamp-2" title={group.name}>
              {group.name}
            </h3>
            <div className="flex flex-wrap gap-1.5 mb-2">
              {group.typeName && (
                <span className="tag bg-accent/15 text-accent">{group.typeName}</span>
              )}
              {group.year && (
                <span className="tag bg-chip text-muted">{group.year}</span>
              )}
              {adult && <span className="tag bg-pink-500/15 text-pink-600 dark:text-pink-400">(18+)</span>}
            </div>
            <p className="text-xs text-muted line-clamp-2 mb-2.5 leading-relaxed">
              {group.remarks || '暂无介绍'}
            </p>
          </div>
          <div className="flex items-center justify-between mt-auto pt-2.5 border-t border-line">
            <span className="tag bg-chip text-muted truncate max-w-[80%]">
              {multi ? `${group.items.length} 个来源` : group.items[0].sourceName}
            </span>
            {multi && (
              <svg
                className={cn('w-4 h-4 text-faint transition-transform', expanded && 'rotate-180')}
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
                aria-hidden
              >
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
              </svg>
            )}
          </div>
        </div>
      </div>

      {multi && expanded && (
        <div className="border-t border-line p-3.5 animate-fade-in">
          <p className="text-[11px] text-faint mb-2">选择来源播放</p>
          {/*
            一行两个源：用 grid 而不是 flex-wrap —— flex 里为了撑满整行必须加 grow，
            而 grow 在源数为奇数时会把最后一行的那个按钮拉满整行，与上一行对不齐。
            grid 的列宽是固定的两等分，落单的按钮自然停在左列。
            348px 是两列的下限（按钮摊不到约 148px 后源名会被截成「36…」），再窄退回单列整行。
          */}
          <div className="grid grid-cols-1 min-[348px]:grid-cols-2 gap-2">
            {group.items.map((item) => {
              const itemKey = qualityKey(item.sourceKey, item.vodId);
              const remarks = formatRemarks(item.remarks);
              const shownRemarks = (() => {
                if (!remarks) return undefined;
                const epMatch = /^(?:全|更新至)(\d+)集$/.exec(remarks);
                if (epMatch) {
                  // 单集（1集）= 电影/单集影片，没有「更新到第几集」这回事，不显示；
                  // 多集电视剧/综艺才显示集数进度
                  return parseInt(epMatch[1], 10) > 1 ? remarks : undefined;
                }
                // 带「全」「更新至」「更新」「更至」前缀但后面不是集数（如「更新HD」）：
                // 前缀对单集影片没有意义，剥掉后保留实际内容（HD / 高清 这类）
                const prefixMatch = /^(?:全|更(?:新至|新|至))\s*(.+)$/.exec(remarks);
                if (prefixMatch) return prefixMatch[1].trim() || undefined;
                // 非集数备注（电影的正片、HD国语 等）原样显示
                return remarks;
              })();
              return (
                // 两列下一行放不下「源名 + 画质 + 集数 + 图标」，拆成两行：
                // 上行源名与播放图标，下行画质与集数。信息一项不减，按钮高度 +14px 左右
                <button
                  key={`${item.sourceKey}_${item.vodId}`}
                  className={cn(
                    'group flex w-full min-w-0 flex-col gap-1 rounded-lg border px-3 py-2',
                    'text-xs transition-all cursor-pointer',
                    'border-line bg-chip hover:border-accent hover:bg-accent/10',
                    item.isAdult && 'border-pink-500/30'
                  )}
                  onClick={(e) => {
                    e.stopPropagation();
                    onOpen(item);
                  }}
                  aria-label={`使用 ${item.sourceName} 播放`}
                >
                  <span className="flex items-center gap-1.5">
                    <span className="font-medium text-content truncate flex-1 min-w-0">{item.sourceName}</span>
                    <svg
                      className="w-3.5 h-3.5 text-accent shrink-0 transition-transform group-hover:scale-110"
                      fill="currentColor"
                      viewBox="0 0 24 24"
                      aria-hidden
                    >
                      <path d="M8 5v14l11-7z" />
                    </svg>
                  </span>
                  <span className="flex min-w-0 items-center gap-1.5">
                    <QualityBadge entry={qualities[itemKey]} loading={probing[itemKey]} />
                    {/* 该源自己的备注：电视剧是它报的集数，电影是「HD国语」这类 */}
                    {shownRemarks && <span className="text-faint truncate flex-1 min-w-0">{shownRemarks}</span>}
                  </span>
                </button>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}

/** 影片卡片：封面加载失败时逐级降级到占位图（未聚合的单一结果使用） */
export function VideoCard({ item, onClick }: { item: SearchResultItem; onClick: () => void }) {
  const imageProxyMode = useAppStore((s) => s.imageProxyMode);
  const customImageProxy = useAppStore((s) => s.customImageProxy);
  const [imgFailed, setImgFailed] = useState(false);
  const showImg = !!item.pic && !imgFailed;

  return (
    <div
      className="card cursor-pointer hover:scale-[1.02] hover:shadow-md h-full focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent/40"
      role="button"
      tabIndex={0}
      onClick={onClick}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          // 阻止空格触发页面滚动（AggregatedCard 已是此写法，此处对齐）
          e.preventDefault();
          onClick();
        }
      }}
    >
      <div className="flex h-full">
        {showImg ? (
          <div className="relative flex-shrink-0 w-[105px] sm:w-[120px] aspect-[2/3] bg-chip">
            <SmartImage
              url={item.pic}
              mode={imageProxyMode}
              customProxy={customImageProxy}
              alt={item.name}
              className="h-full w-full object-cover"
              onExhausted={() => setImgFailed(true)}
            />
            <div className="absolute inset-0 bg-gradient-to-r from-black/30 to-transparent" />
          </div>
        ) : (
          <div className="flex-shrink-0 w-[105px] sm:w-[120px] aspect-[2/3] bg-chip flex items-center justify-center">
            <svg className="w-8 h-8 text-faint" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M7 4v16m10-16v16M3 6a1 1 0 011-1h1a1 1 0 011 1v12a1 1 0 01-1 1H4a1 1 0 01-1-1V6zm4 0a1 1 0 011-1h1a1 1 0 011 1v12a1 1 0 01-1 1h-1a1 1 0 01-1-1V6zm8 0a1 1 0 011-1h1a1 1 0 011 1v12a1 1 0 01-1 1h-1a1 1 0 01-1-1V6zm4 0a1 1 0 011-1h1a1 1 0 011 1v12a1 1 0 01-1 1h-1a1 1 0 01-1-1V6z" />
            </svg>
          </div>
        )}

        <div className="p-2.5 flex flex-col flex-grow min-w-0">
          <div className="flex-grow">
            <h3 className="font-semibold text-sm text-content mb-1.5 line-clamp-2" title={item.name}>
              {item.name}
            </h3>
            <div className="flex flex-wrap gap-1 mb-1.5">
              {item.typeName && <span className="tag bg-accent/15 text-accent">{item.typeName}</span>}
              {item.year && <span className="tag bg-chip text-muted">{item.year}</span>}
            </div>
            <p className="text-xs text-muted line-clamp-2 mb-2">{formatRemarks(item.remarks) || '暂无介绍'}</p>
          </div>
          <div className="flex items-center justify-between mt-auto pt-1.5 border-t border-line">
            <span className="tag bg-chip text-muted truncate max-w-[80%]">{item.sourceName}</span>
          </div>
        </div>
      </div>
    </div>
  );
}

/** 豆瓣推荐卡片（无来源徽章，点击直接搜索） */
export function DoubanCard({ item, onClick }: { item: { title: string; cover: string; rating?: string }; onClick: () => void }) {
  const imageProxyMode = useAppStore((s) => s.imageProxyMode);
  const customImageProxy = useAppStore((s) => s.customImageProxy);
  const [imgFailed, setImgFailed] = useState(false);
  useEffect(() => setImgFailed(false), [item.cover]);

  return (
    <div
      className="cursor-pointer group focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent/40 rounded-xl"
      role="button"
      tabIndex={0}
      onClick={onClick}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          // 阻止空格触发页面滚动（AggregatedCard 已是此写法，此处对齐）
          e.preventDefault();
          onClick();
        }
      }}
    >
      <div className="relative aspect-[2/3] bg-chip rounded-xl overflow-hidden border border-line transition-transform duration-200 group-hover:scale-[1.03]">
        {item.cover && !imgFailed ? (
          <SmartImage
            url={item.cover}
            mode={imageProxyMode}
            customProxy={customImageProxy}
            alt={item.title}
            className="w-full h-full object-cover"
            onExhausted={() => setImgFailed(true)}
          />
        ) : (
          <div className="w-full h-full flex items-center justify-center bg-chip">
            <svg className="w-8 h-8 text-faint" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M7 4v16m10-16v16M3 6a1 1 0 011-1h1a1 1 0 011 1v12a1 1 0 01-1 1H4a1 1 0 01-1-1V6zm4 0a1 1 0 011-1h1a1 1 0 011 1v12a1 1 0 01-1 1h-1a1 1 0 01-1-1V6zm8 0a1 1 0 011-1h1a1 1 0 011 1v12a1 1 0 01-1 1h-1a1 1 0 01-1-1V6zm4 0a1 1 0 011-1h1a1 1 0 011 1v12a1 1 0 01-1 1h-1a1 1 0 01-1-1V6z" />
            </svg>
          </div>
        )}
        {item.rating && <span className="rating-badge">★ {item.rating}</span>}
      </div>
      <div className="mt-2 px-0.5">
        <div className="text-[13px] font-medium text-content truncate" title={item.title}>
          {item.title}
        </div>
      </div>
    </div>
  );
}
