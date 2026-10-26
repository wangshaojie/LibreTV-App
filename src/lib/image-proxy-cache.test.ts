import { describe, expect, it } from 'vitest';
import {
  activeImageProxies,
  getCachedImage,
  imageCacheStats,
  looksLikeImageTarget,
  putCachedImage,
  withImageProxySlot,
} from './image-proxy-cache';

const bytes = (n: number) => new Uint8Array(n).buffer as ArrayBuffer;

describe('图片代理缓存', () => {
  it('写入后能按 URL 命中', () => {
    const url = 'https://img.example.com/a.jpg';
    putCachedImage(url, bytes(16), 'image/jpeg', 'etag-1', null);
    const hit = getCachedImage(url);
    expect(hit).toBeTruthy();
    expect(hit!.contentType).toBe('image/jpeg');
    expect(hit!.etag).toBe('etag-1');
    expect(hit!.body.byteLength).toBe(16);
  });

  it('未写入的 URL 不命中', () => {
    expect(getCachedImage('https://img.example.com/missing.jpg')).toBeUndefined();
  });

  it('超过单条体积上限的图片不入缓存', () => {
    const url = 'https://img.example.com/huge.jpg';
    putCachedImage(url, bytes(5 * 1024 * 1024), 'image/jpeg', null, null);
    expect(getCachedImage(url)).toBeUndefined();
  });

  it('重复写入同一 URL 不会让体积统计翻倍', () => {
    const url = 'https://img.example.com/dup.jpg';
    putCachedImage(url, bytes(1024), 'image/jpeg', null, null);
    const before = imageCacheStats().bytes;
    putCachedImage(url, bytes(1024), 'image/jpeg', null, null);
    expect(imageCacheStats().bytes).toBe(before);
  });
});

describe('图片代理并发闸门', () => {
  it('同时在途请求不超过上限，其余排队', async () => {
    const limit = 8;
    let peak = 0;
    let active = 0;
    const task = () =>
      withImageProxySlot(async () => {
        active++;
        peak = Math.max(peak, active);
        await new Promise((resolve) => setTimeout(resolve, 5));
        active--;
      });
    await Promise.all(Array.from({ length: limit * 3 }, task));
    expect(peak).toBeLessThanOrEqual(limit);
    expect(activeImageProxies()).toBe(0);
  });

  it('任务抛错后仍会释放名额', async () => {
    await expect(
      withImageProxySlot(async () => {
        throw new Error('boom');
      })
    ).rejects.toThrow('boom');
    expect(activeImageProxies()).toBe(0);
  });
});

describe('图片目标判定', () => {
  it('按扩展名识别图片', () => {
    expect(looksLikeImageTarget('https://x.com/a.jpg')).toBe(true);
    expect(looksLikeImageTarget('https://x.com/a.PNG')).toBe(true);
    expect(looksLikeImageTarget('https://x.com/a.webp?v=2')).toBe(true);
  });

  it('非图片地址不误判', () => {
    expect(looksLikeImageTarget('https://x.com/api.php/provide/vod')).toBe(false);
    expect(looksLikeImageTarget('https://x.com/video.m3u8')).toBe(false);
    expect(looksLikeImageTarget('不是地址')).toBe(false);
  });
});
