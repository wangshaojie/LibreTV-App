'use client';

import { useEffect, useRef, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/client-api';
import { useAppStore } from '@/lib/store';
import { cn } from '@/lib/utils';
import type { DoubanItem } from '@/lib/types';
import { DoubanCard } from './video-card';

const MOVIE_TAGS = ['热门', '最新', '经典', '豆瓣高分', '冷门佳片', '华语', '欧美', '韩国', '日本', '动画'];
const TV_TAGS = ['热门', '美剧', '英剧', '韩剧', '日剧', '国产剧', '港剧', '日本动画', '综艺', '纪录片'];
const WEEKDAYS = ['周一', '周二', '周三', '周四', '周五', '周六', '周日'];

/** 推荐区筛选位置的本地存储键（跨路由导航保留） */
const DOUBBAN_VIEW_KEY = 'libretv-douban-view';
const BANGUMI_VIEW_KEY = 'libretv-bangumi-view';
const HOT_LIST_VIEW_KEY = 'libretv-hot-list-view';

/** 首页推荐区：数据源由设置决定（豆瓣热门 / Bangumi 新番放送 / 影视热榜），二选一展示 */
export function RecommendSection({ onPick }: { onPick: (title: string) => void }) {
  const doubanEnabled = useAppStore((s) => s.doubanEnabled);
  const recommendSource = useAppStore((s) => s.recommendSource);

  if (!doubanEnabled) return null;
  if (recommendSource === 'bangumi') return <BangumiView onPick={onPick} />;
  if (recommendSource === 'hot-list') return <HotListView onPick={onPick} />;
  return <DoubanView onPick={onPick} />;
}

/**
 * 豆瓣推荐：影视切换 + 标签筛选。
 * 一次性渲染全部条目：数据本就一次拉全（50 条），卡片图片又是 lazy 加载，
 * 视口外的图不会请求，因此不需要「加载更多」这类本地切片分页来打断浏览。
 *
 * 选中影片会跳到搜索结果页（推荐区整块卸载），返回时组件重新挂载，
 * 因此「电影/剧集」和标签的位置写入 localStorage 保留，而不是留在组件内存里。
 */
function DoubanView({ onPick }: { onPick: (title: string) => void }) {
  const [type, setTypeState] = useState<'movie' | 'tv'>('movie');
  const [tag, setTagState] = useState('热门');
  // 只有用户主动切换过才写回，避免挂载瞬间用默认值覆盖已有记录
  const dirty = useRef(false);
  const setType = (v: 'movie' | 'tv') => { dirty.current = true; setTypeState(v); };
  const setTag = (v: string) => { dirty.current = true; setTagState(v); };

  // 挂载后读取上次位置（不在渲染期读 localStorage，避免 SSR/hydration 不一致）
  useEffect(() => {
    let nextType: 'movie' | 'tv' = 'movie';
    let nextTag = '热门';
    try {
      const raw = localStorage.getItem(DOUBBAN_VIEW_KEY);
      if (raw) {
        const saved = JSON.parse(raw) as { type?: string; tag?: string };
        if (saved.type === 'tv' || saved.type === 'movie') nextType = saved.type;
        const tags = nextType === 'tv' ? TV_TAGS : MOVIE_TAGS;
        if (saved.tag && tags.includes(saved.tag)) nextTag = saved.tag;
      }
    } catch {
      /* 本地记录损坏时忽略，回到默认位置 */
    }
    setTypeState(nextType);
    setTagState(nextTag);
  }, []);

  useEffect(() => {
    if (!dirty.current) return;
    try {
      localStorage.setItem(DOUBBAN_VIEW_KEY, JSON.stringify({ type, tag }));
    } catch {
      /* 隐私模式等无法写入时忽略 */
    }
  }, [type, tag]);

  const query = useQuery({
    queryKey: ['douban', type, tag],
    queryFn: ({ signal }) => api.douban(type, tag, 0, 50, signal),
  });

  const tags = type === 'movie' ? MOVIE_TAGS : TV_TAGS;
  const items = query.data?.items ?? [];

  const switchType = (t: 'movie' | 'tv') => {
    if (t === type) return;
    setType(t);
    // 标签在新类型下依然存在就保留，否则回到「热门」
    const nextTags = t === 'tv' ? TV_TAGS : MOVIE_TAGS;
    if (!nextTags.includes(tag)) setTag('热门');
  };

  return (
    <section aria-label="影视推荐">
      <div className="flex items-center justify-between mb-3">
        <div className="seg" role="tablist" aria-label="影视类型">
          <button
            role="tab"
            aria-selected={type === 'movie'}
            className={cn('seg-item', type === 'movie' ? 'seg-item-active' : 'seg-item-idle')}
            onClick={() => switchType('movie')}
          >
            电影
          </button>
          <button
            role="tab"
            aria-selected={type === 'tv'}
            className={cn('seg-item', type === 'tv' ? 'seg-item-active' : 'seg-item-idle')}
            onClick={() => switchType('tv')}
          >
            剧集
          </button>
        </div>
      </div>

      <div className="flex flex-wrap gap-2 mb-5">
        {tags.map((t) => (
          <button
            key={t}
            className={cn(
              'chip px-3 py-1.5 text-xs',
              t === tag ? 'bg-accent text-on-accent font-medium' : 'bg-chip text-muted hover:text-content hover:bg-hover'
            )}
            onClick={() => setTag(t)}
          >
            {t}
          </button>
        ))}
      </div>

      {query.isError ? (
        <p className="text-center text-sm text-faint py-8">推荐内容加载失败，可稍后重试或在设置中关闭</p>
      ) : query.isLoading ? (
        <GridSkeleton />
      ) : items.length === 0 ? (
        <p className="text-center text-sm text-faint py-8">该分类暂无推荐内容</p>
      ) : (
        <div className="grid grid-cols-3 sm:grid-cols-4 md:grid-cols-6 lg:grid-cols-8 gap-2.5">
          {items.map((item) => (
            <DoubanCard key={item.id} item={item} onClick={() => onPick(item.title)} />
          ))}
        </div>
      )}
    </section>
  );
}

/** Bangumi 每日放送：星期筛选，一次拉全量无分页 */
function BangumiView({ onPick }: { onPick: (title: string) => void }) {
  const [weekday, setWeekdayState] = useState<number | 'all'>('all');
  const dirty = useRef(false);
  const setWeekday = (v: number | 'all') => { dirty.current = true; setWeekdayState(v); };

  // 同豆瓣推荐：返回首页时保留上次选的星期
  useEffect(() => {
    let next: number | 'all' = 'all';
    try {
      const raw = localStorage.getItem(BANGUMI_VIEW_KEY);
      if (raw) {
        const saved = JSON.parse(raw) as { weekday?: number | string };
        if (saved.weekday === 'all') next = 'all';
        else if (typeof saved.weekday === 'number' && saved.weekday >= 1 && saved.weekday <= 7) next = saved.weekday;
      }
    } catch {
      /* 本地记录损坏时忽略 */
    }
    setWeekdayState(next);
  }, []);

  useEffect(() => {
    if (!dirty.current) return;
    try {
      localStorage.setItem(BANGUMI_VIEW_KEY, JSON.stringify({ weekday }));
    } catch {
      /* 隐私模式等无法写入时忽略 */
    }
  }, [weekday]);

  const query = useQuery({
    queryKey: ['bangumi-calendar'],
    queryFn: ({ signal }) => api.bangumiCalendar(signal),
  });

  let items: DoubanItem[] = [];
  if (query.data) {
    items = weekday === 'all'
      ? query.data.days.flatMap((d) => d.items)
      : (query.data.days.find((d) => d.weekday === weekday)?.items ?? []);
  }

  return (
    <section aria-label="新番放送推荐">
      <div className="flex flex-wrap gap-1.5 mb-4">
        <ChipButton active={weekday === 'all'} onClick={() => setWeekday('all')}>
          全部
        </ChipButton>
        {WEEKDAYS.map((name, i) => (
          <ChipButton key={name} active={weekday === i + 1} onClick={() => setWeekday(i + 1)}>
            {name}
          </ChipButton>
        ))}
      </div>

      {query.isError ? (
        <p className="text-center text-sm text-faint py-8">推荐内容加载失败，可稍后重试或在设置中关闭</p>
      ) : query.isLoading ? (
        <GridSkeleton />
      ) : (
        <div className="grid grid-cols-3 sm:grid-cols-4 md:grid-cols-6 lg:grid-cols-8 gap-2.5">
          {items.map((item) => (
            <DoubanCard key={item.id} item={item} onClick={() => onPick(item.title)} />
          ))}
        </div>
      )}
    </section>
  );
}

const HOT_LISTS = [
  { id: 'douban_movie_weekly', label: '电影周榜' },
  { id: 'douban_tv_chinese', label: '国产剧周榜' },
  { id: 'douban_tv_global', label: '海外剧周榜' },
  { id: 'douban_show_chinese', label: '国内综艺' },
  { id: 'douban_show_global', label: '海外综艺' },
  { id: 'baidu_teleplay', label: '百度热播剧' },
];

/** 影视热榜（60s API）：豆瓣五个周榜 + 百度热播剧，chips 切换 */
function HotListView({ onPick }: { onPick: (title: string) => void }) {
  const [listId, setListIdState] = useState(HOT_LISTS[0].id);
  const dirty = useRef(false);
  const setListId = (v: string) => { dirty.current = true; setListIdState(v); };

  // 同豆瓣推荐：返回首页时保留上次选的榜单
  useEffect(() => {
    let next = HOT_LISTS[0].id;
    try {
      const raw = localStorage.getItem(HOT_LIST_VIEW_KEY);
      if (raw) {
        const saved = JSON.parse(raw) as { listId?: string };
        if (saved.listId && HOT_LISTS.some((l) => l.id === saved.listId)) next = saved.listId;
      }
    } catch {
      /* 本地记录损坏时忽略 */
    }
    setListIdState(next);
  }, []);

  useEffect(() => {
    if (!dirty.current) return;
    try {
      localStorage.setItem(HOT_LIST_VIEW_KEY, JSON.stringify({ listId }));
    } catch {
      /* 隐私模式等无法写入时忽略 */
    }
  }, [listId]);

  const query = useQuery({
    queryKey: ['hot-list', listId],
    queryFn: ({ signal }) => api.hotList(listId, signal),
  });

  const items = query.data?.items ?? [];

  return (
    <section aria-label="影视榜单推荐">
      <div className="flex flex-wrap gap-1.5 mb-4">
        {HOT_LISTS.map((l) => (
          <ChipButton key={l.id} active={listId === l.id} onClick={() => setListId(l.id)}>
            {l.label}
          </ChipButton>
        ))}
      </div>

      {query.isError ? (
        <p className="text-center text-sm text-faint py-8">推荐内容加载失败，可稍后重试或在设置中关闭</p>
      ) : query.isLoading ? (
        <GridSkeleton />
      ) : (
        <div className="grid grid-cols-3 sm:grid-cols-4 md:grid-cols-6 lg:grid-cols-8 gap-2.5">
          {items.map((item) => (
            <DoubanCard key={item.id} item={item} onClick={() => onPick(item.title)} />
          ))}
        </div>
      )}
    </section>
  );
}

function GridSkeleton() {
  return (
    <div className="grid grid-cols-3 sm:grid-cols-4 md:grid-cols-6 lg:grid-cols-8 gap-2.5">
      {Array.from({ length: 16 }).map((_, i) => (
        <div key={i}>
          <div className="aspect-[2/3] bg-chip animate-pulse" />
          <div className="h-3 mt-2 rounded bg-chip animate-pulse" />
        </div>
      ))}
    </div>
  );
}

function ChipButton({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button
      className={cn(
        'chip px-3 py-1.5 text-xs',
        active ? 'bg-accent text-on-accent font-medium' : 'bg-chip text-muted hover:text-content hover:bg-hover'
      )}
      onClick={onClick}
    >
      {children}
    </button>
  );
}
