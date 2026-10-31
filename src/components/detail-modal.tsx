'use client';

import { useEffect, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import { api } from '@/lib/client-api';
import type { SearchResultItem, VideoDetail } from '@/lib/types';
import { buildWatchUrl } from '@/lib/utils';
import { formatRemarks } from '@/lib/remarks';
import { SmartImage } from './smart-image';
import { useAppStore, resolveSource } from '@/lib/store';
import { useToast } from './toast';
import { cn } from '@/lib/utils';
import { addSearchHistory } from '@/lib/db';
import { copyToClipboard } from '@/lib/clipboard';
import { EmptyState, ErrorState, LoadingState } from './states';
import { useFocusTrap } from './use-focus-trap';
import { Icon } from './icon';

/**
 * 详情弹窗：剧集列表 + 排序 + 复制链接。
 * 剧集点击 → /watch 路由（URL 携带定位参数，可分享、可后退）。
 */

export function DetailModal({ item, onClose }: { item: SearchResultItem | null; onClose: () => void }) {
  const store = useAppStore();
  const router = useRouter();
  const { toast } = useToast();
  const [detail, setDetail] = useState<VideoDetail | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [reversed, setReversed] = useState(false);
  const [posterFailed, setPosterFailed] = useState(false);
  /** 简介展开态：默认只显示 3 行 */
  const [descExpanded, setDescExpanded] = useState(false);
  const abortRef = useRef<AbortController | null>(null);
  const panelRef = useRef<HTMLDivElement>(null);

  // 打开时把焦点移入弹窗、Tab 圈闭在弹窗内、关闭后归还焦点
  useFocusTrap(Boolean(item), panelRef);

  useEffect(() => {
    setPosterFailed(false);
    setDescExpanded(false);
  }, [item?.pic, item?.vodId, store.imageProxyMode, store.customImageProxy]);

  // 弹窗打开期间锁定背景滚动
  useEffect(() => {
    if (!item) return;
    const prev = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.body.style.overflow = prev;
    };
  }, [item]);

  useEffect(() => {
    if (!item) {
      setDetail(null);
      setError('');
      return;
    }
    const source = resolveSource(store, item.sourceKey, {
      url: item.sourceUrl,
      name: item.sourceName,
    });
    if (!source) {
      setError('点播源配置不存在（可能已被删除），请在设置中重新添加');
      return;
    }
    setLoading(true);
    setError('');
    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;
    api
      .detail(item.vodId, source, controller.signal)
      .then((d) => setDetail(d))
      .catch((err) => {
        if (err instanceof DOMException && err.name === 'AbortError') return;
        setError(err instanceof Error ? err.message : '获取详情失败');
      })
      .finally(() => setLoading(false));
    return () => controller.abort();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [item]);

  useEffect(() => {
    if (!item) return;
    const handler = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [item, onClose]);

  if (!item) return null;

  const episodes = detail ? (reversed ? [...detail.episodes].reverse() : detail.episodes) : [];
  const info = detail?.videoInfo;

  const play = (index: number) => {
    if (!detail || !detail.episodes[index]) return;
    addSearchHistory(item.name).catch(() => {});
    router.push(
      buildWatchUrl({
        sourceKey: item.sourceKey,
        vodId: item.vodId,
        index,
        title: item.name,
        sourceUrl: item.sourceUrl,
      })
    );
  };

  const copyLinks = async () => {
    if (!detail) return;
    const ok = await copyToClipboard(detail.episodes.join('\n'));
    if (ok) toast('播放链接已复制', 'success');
    else toast('复制失败，请检查浏览器权限', 'error');
  };

  // 备注与卡片同源，同样归一化，避免同一部剧在两处显示不同文案
  const remarks = formatRemarks(info?.remarks);
  // 主演单独一行展示（可换行不截断），不放进 metaRows
  const metaRows = [
    info?.typeName && ['类型', info.typeName],
    info?.year && ['年份', info.year],
    info?.area && ['地区', info.area],
    info?.director && ['导演', info.director],
    remarks && ['备注', remarks],
  ].filter(Boolean) as [string, string][];

  return (
    <div
      className="fixed inset-0 z-[60] flex items-start justify-center overflow-y-auto bg-black/80 py-10 px-4 animate-fade-in"
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div
        ref={panelRef}
        tabIndex={-1}
        className="bg-surface-raised rounded-2xl w-full max-w-3xl shadow-2xl animate-slide-up outline-none overflow-hidden"
        role="dialog"
        aria-modal="true"
        aria-label={`${item.name} 详情`}
      >
        {/* 顶部：海报 + 标题 + 来源 + 关闭 */}
        <div className="flex items-start gap-4 p-5 pb-4">
          {item?.pic && !posterFailed && (
            <SmartImage
              url={item.pic}
              mode={store.imageProxyMode}
              customProxy={store.customImageProxy}
              alt={item.name}
              className="w-28 sm:w-32 aspect-[2/3] object-cover rounded-xl bg-chip shrink-0 border border-line"
              loading="eager"
              onExhausted={() => setPosterFailed(true)}
            />
          )}
          <div className="min-w-0 flex-1">
            <div className="flex items-start justify-between gap-3">
              <h2 className="text-xl font-semibold text-content break-words leading-snug">{item.name}</h2>
              {/* 与播放页同一套返回入口：左箭头关闭弹窗回到上一层，home 直达首页 */}
              <div className="flex items-center gap-1.5 shrink-0 -mt-1 -mr-1">
                <button
                  className="btn-icon"
                  onClick={onClose}
                  aria-label="返回上一页"
                  title="返回上一页"
                >
                  <Icon name="arrowLeft" className="w-5 h-5" />
                </button>
                <button
                  className="btn-icon"
                  onClick={() => {
                    onClose();
                    router.push('/');
                  }}
                  aria-label="回首页"
                  title="回首页"
                >
                  <Icon name="home" className="w-5 h-5" />
                </button>
              </div>
            </div>
            <p className="text-sm text-faint mt-1.5">{item.sourceName}</p>
          </div>
        </div>

        <div className="px-5 pb-5">
          {loading && <LoadingState label="正在获取剧集信息..." />}

          {!loading && error && <ErrorState message={error || '获取失败'} />}

          {!loading && !error && detail && (
            <>
              {/* 元信息：类型/年份/地区/导演 做成 pill，可快速扫读 */}
              {metaRows.length > 0 && (
                <div className="flex flex-wrap gap-1.5 mb-3">
                  {metaRows.map(([k, v]) => (
                    <span key={k} className="tag bg-chip text-muted px-2.5 py-1">
                      <span className="text-faint mr-1">{k}</span>
                      {v}
                    </span>
                  ))}
                </div>
              )}

              {/* 主演：单独一行，可换行，不截断 —— 演员名是识别影片的关键信息 */}
              {info?.actor && (
                <p className="text-sm text-muted leading-relaxed mb-3">
                  <span className="text-faint mr-1.5">主演</span>
                  <span className="text-content">{info.actor}</span>
                </p>
              )}

              {/* 简介：默认 3 行，可展开 */}
              {info?.desc && (
                <div className="mb-4">
                  <p
                    className={cn(
                      'text-sm text-muted leading-relaxed',
                      !descExpanded && 'line-clamp-3'
                    )}
                  >
                    {info.desc}
                  </p>
                  {info.desc.length > 80 && (
                    <button
                      className="text-xs text-accent hover:underline mt-1"
                      onClick={() => setDescExpanded((v) => !v)}
                    >
                      {descExpanded ? '收起' : '展开'}
                    </button>
                  )}
                </div>
              )}

              {episodes.length > 0 ? (
                <>
                  <div className="border-t border-line pt-4">
                    <div className="flex flex-wrap items-center justify-between gap-2 mb-3">
                      <div className="flex items-center gap-2.5">
                        <h3 className="text-sm font-semibold text-content">剧集</h3>
                        <span className="text-xs text-faint">共 {episodes.length} 集</span>
                        {episodes.length > 1 && (
                          <button
                            className="text-xs text-muted hover:text-content transition-colors"
                            onClick={() => setReversed((v) => !v)}
                            aria-label={reversed ? '切换为正序排列' : '切换为倒序排列'}
                            title="调整剧集列表的排列顺序"
                          >
                            {reversed ? '正序' : '倒序'}
                          </button>
                        )}
                      </div>
                      <button className="btn-outline btn-sm" onClick={copyLinks}>
                        <Icon name="link" className="w-3.5 h-3.5" />
                        复制链接
                      </button>
                    </div>
                    {/* 单集影片不铺网格：直接一个主按钮，避免「孤零零一个 1」的怪异观感 */}
                    {episodes.length === 1 ? (
                      <button
                        className="btn-primary w-full h-11 font-medium"
                        onClick={() => play(0)}
                      >
                        <Icon name="bolt" className="w-4 h-4" />
                        立即播放
                      </button>
                    ) : (
                      <div className="grid grid-cols-4 sm:grid-cols-6 md:grid-cols-8 gap-2 max-h-[40vh] overflow-y-auto scrollbar-thin pr-1">
                        {episodes.map((_, i) => {
                          const realIndex = reversed ? detail.episodes.length - 1 - i : i;
                          return (
                            <button
                              key={realIndex}
                              className="h-10 rounded-lg border border-line bg-chip text-muted text-sm transition-colors hover:border-accent hover:text-accent"
                              onClick={() => play(realIndex)}
                            >
                              {realIndex + 1}
                            </button>
                          );
                        })}
                      </div>
                    )}
                  </div>
                </>
              ) : (
                <EmptyState
                  icon="alert"
                  title="未找到播放资源"
                  description="该视频可能暂时无法播放，请尝试其他视频"
                />
              )}
            </>
          )}
        </div>
      </div>
    </div>
  );
}
