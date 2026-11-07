'use client';

import { useEffect, useMemo, useState } from 'react';
import { useLiveQuery } from 'dexie-react-hooks';
import Link from 'next/link';
import { Drawer } from './drawer';
import { ConfirmDialog } from './confirm-dialog';
import { EmptyState, LoadingState } from './states';
import { Icon } from './icon';
import { SmartImage } from './smart-image';
import {
  db,
  clearAllHistory,
  removeHistory,
  upsertHistory,
  MAX_HISTORY,
  type HistoryEntry,
} from '@/lib/db';
import { buildWatchUrl, cn, formatRelativeTime, formatTime } from '@/lib/utils';
import { useToast } from './toast';
import { resolveSource, useAppStore } from '@/lib/store';

/**
 * 观看历史面板（IndexedDB 实时查询）。
 * 与旧版的区别：播放前按需重新拉取剧集详情，不再依赖历史记录里冗余存储的全集 URL。
 * 破坏性操作分级：清空全部走确认弹窗，删除单条支持撤销。
 */

/** 未搜索时列表默认展示的条数（DB 实存 MAX_HISTORY 条，其余靠搜索查阅） */
const VISIBLE_LIMIT = 50;

export function HistoryPanel({ open, onClose }: { open: boolean; onClose: () => void }) {
  // 全量读取（上限即 DB 淘汰上限）：默认只渲染最近若干条，搜索时覆盖更早的记录
  const history = useLiveQuery(
    () => db.history.orderBy('timestamp').reverse().limit(MAX_HISTORY).toArray(),
    [open]
  );
  const { toast } = useToast();
  const [confirmClear, setConfirmClear] = useState(false);
  const [query, setQuery] = useState('');

  // 关闭后重置搜索词：下次打开回到完整列表，不残留上次的过滤状态
  useEffect(() => {
    if (!open) setQuery('');
  }, [open]);

  const keyword = query.trim().toLowerCase();
  const visible = useMemo(() => {
    const all = history ?? [];
    if (!keyword) return all.slice(0, VISIBLE_LIMIT);
    return all.filter((h) => h.title.toLowerCase().includes(keyword));
  }, [history, keyword]);

  const total = history?.length ?? 0;

  return (
    <Drawer
      open={open}
      onClose={onClose}
      title="观看历史"
      width="max-w-lg"
      // 搜索框放在固定副标题栏：历史较长时滚动列表仍可随时改关键字
      subheader={
        total > 0 ? (
          <div className="flex items-center gap-2">
            <div className="relative flex-1">
              <Icon
                name="search"
                className="absolute left-2.5 top-1/2 -translate-y-1/2 w-4 h-4 text-faint pointer-events-none"
              />
              <input
                type="text"
                className="w-full h-9 pl-8 pr-8 bg-chip text-sm text-content placeholder:text-faint focus:outline-none focus:ring-2 focus:ring-accent"
                placeholder="搜索观看历史..."
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                aria-label="搜索观看历史"
              />
              {query && (
                <button
                  type="button"
                  className="absolute right-1.5 top-1/2 -translate-y-1/2 p-1 rounded-full text-faint hover:text-content hover:bg-hover transition-colors"
                  onClick={() => setQuery('')}
                  aria-label="清空搜索"
                >
                  <Icon name="close" className="w-3.5 h-3.5" />
                </button>
              )}
            </div>
            <button
              className="shrink-0 px-2 py-1.5 text-xs text-faint hover:text-danger hover:bg-hover transition-colors"
              onClick={() => setConfirmClear(true)}
            >
              清空历史
            </button>
          </div>
        ) : undefined
      }
    >
      {!history ? (
        <LoadingState />
      ) : total === 0 ? (
        <EmptyState variant="plain" title="暂无观看记录" />
      ) : visible.length === 0 ? (
        <EmptyState variant="plain" title={`没有匹配「${query.trim()}」的观看记录`} />
      ) : (
        <>
          {!keyword && total > VISIBLE_LIMIT && (
            <p className="text-xs text-faint mb-2">
              共 {total} 条记录，仅显示最近 {VISIBLE_LIMIT} 条，可用上方搜索查找更早的记录
            </p>
          )}
          <ul className="space-y-2">
            {visible.map((item) => (
              <HistoryItem key={`${item.sourceKey}_${item.vodId}`} item={item} />
            ))}
          </ul>
        </>
      )}

      {/* 重操作：清空不可撤销，先确认并说明数量 */}
      <ConfirmDialog
        open={confirmClear}
        danger
        title="清空全部观看历史？"
        message={history ? `将删除 ${history.length} 条观看记录，此操作不可撤销。` : undefined}
        confirmLabel="清空"
        onCancel={() => setConfirmClear(false)}
        onConfirm={() => {
          void clearAllHistory().then(() => toast('观看历史已清空', 'success'));
          setConfirmClear(false);
        }}
      />
    </Drawer>
  );
}

function HistoryItem({ item }: { item: HistoryEntry }) {
  // 精确订阅：整份 useAppStore() 会让每条历史在任意 store 变化（搜索健康度、测活写回等）时重渲染
  const imageProxyMode = useAppStore((s) => s.imageProxyMode);
  const customImageProxy = useAppStore((s) => s.customImageProxy);
  const customAPIs = useAppStore((s) => s.customAPIs);
  const envSources = useAppStore((s) => s.envSources);
  const { toast } = useToast();
  const [imgFailed, setImgFailed] = useState(false);
  const source = resolveSource({ customAPIs, envSources }, item.sourceKey);

  const hasPercent =
    item.playbackPosition > 10 && item.duration > 0 && item.playbackPosition < item.duration * 0.95;
  const percent = hasPercent ? Math.round((item.playbackPosition / item.duration) * 100) : 0;

  const href = buildWatchUrl({
    sourceKey: item.sourceKey,
    vodId: item.vodId,
    index: item.episodeIndex,
    title: item.title,
    sourceUrl: source?.url,
    detail: source?.detail,
  });

  const remove = () => {
    void removeHistory(item.sourceKey, item.vodId).then(() => {
      // 轻操作：给撤销窗口，恢复时写回原记录
      toast('已删除该记录', 'info', {
        action: {
          label: '撤销',
          onClick: () => {
            void upsertHistory({
              sourceKey: item.sourceKey,
              sourceUrl: item.sourceUrl,
              vodId: item.vodId,
              title: item.title,
              pic: item.pic,
              episodeIndex: item.episodeIndex,
              totalEpisodes: item.totalEpisodes,
              playbackPosition: item.playbackPosition,
              duration: item.duration,
              timestamp: item.timestamp,
            }).then(() => toast('已恢复', 'success'));
          },
        },
      });
    });
  };

  return (
    <li className="relative group">
      <Link
        href={href}
        onClick={() => {
          // 点击即同步一次时间戳，置顶最近观看
          upsertHistory({ ...item, timestamp: Date.now() }).catch(() => {});
        }}
        className="block bg-card hover:bg-hover p-3 pr-10 transition-colors"
      >
        <div className="flex items-center gap-3">
          {item.pic && !imgFailed ? (
            <SmartImage
              url={item.pic}
              mode={imageProxyMode}
              customProxy={customImageProxy}
              alt=""
              className="w-10 h-14 object-cover rounded bg-chip"
              onExhausted={() => setImgFailed(true)}
            />
          ) : (
            <div className="w-10 h-14 rounded bg-chip flex items-center justify-center shrink-0">
              <svg className="w-4 h-4 text-faint" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M7 4v16m10-16v16M7 8h10M7 12h10" />
              </svg>
            </div>
          )}
          <div className="min-w-0 flex-1">
            <div className="text-sm font-medium text-content truncate">{item.title}</div>
            <div className="text-xs text-faint mt-0.5 flex items-center gap-1.5 flex-wrap">
              {item.totalEpisodes > 0 && <span>第 {item.episodeIndex + 1}/{item.totalEpisodes} 集</span>}
              {hasPercent && <span>· {formatTime(item.playbackPosition)}</span>}
              <span>· {formatRelativeTime(item.timestamp)}</span>
            </div>
            {hasPercent && (
              <div className="mt-1.5 h-1 bg-hover rounded-full overflow-hidden">
                <div className="h-full bg-accent" style={{ width: `${percent}%` }} />
              </div>
            )}
          </div>
        </div>
      </Link>
      <button
        className={cn(
          'absolute right-2 top-2 p-2 rounded-full text-faint hover:text-danger',
          'history-remove'
        )}
        aria-label="删除记录"
        title="删除记录（可在提示中撤销）"
        onClick={remove}
      >
        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
        </svg>
      </button>
    </li>
  );
}
