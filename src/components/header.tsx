'use client';

import Link from 'next/link';
import { useState } from 'react';
import { usePathname, useRouter } from 'next/navigation';
import { ThemeToggle } from './theme';
import { SourceManagerDrawer } from './source-manager';
import { HistoryPanel } from './history-panel';
import { requestShowDownloadManager } from './download-manager';
import { Icon } from './icon';
import { SearchHistoryDropdown, useSearchHistory } from './search-history';
import { BackButton } from './back-button';
import { cn } from '@/lib/utils';

/**
 * 顶部导航：Logo、搜索框（首页外）、历史、设置。
 * 二级页面统一在左侧给出「返回上一页」与「回首页」两个描边方块按钮，
 * 与播放页、详情弹窗保持同一套样式和位置语义。
 */
export function Header({
  showSearch = false,
  showBack = false,
  onBack,
}: {
  showSearch?: boolean;
  showBack?: boolean;
  onBack?: () => void;
}) {
  const router = useRouter();
  const pathname = usePathname();
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [historyOpen, setHistoryOpen] = useState(false);
  const [query, setQuery] = useState('');
  // 与首页搜索框共用同一套「最近搜索」下拉逻辑
  const searchHistory = useSearchHistory(query);

  const submitSearch = (text: string) => {
    const q = text.trim().slice(0, 100);
    if (!q) return;
    searchHistory.close();
    router.push(`/?s=${encodeURIComponent(q)}`, { scroll: false });
    // 顶栏搜索一并写入最近搜索（此前只有首页会记录）
    searchHistory.record(q);
  };

  const pickHistory = (text: string) => {
    setQuery(text);
    submitSearch(text);
  };

  return (
    <>
      <header className="sticky top-0 z-40 bg-surface/90 backdrop-blur border-b border-line">
        <div className="max-w-6xl mx-auto px-4 h-14 flex items-center gap-3">
          {/* 二级页面显式返回：桌面端没有浏览器地址栏，只能靠这个回到上一层 */}
          {showBack && <BackButton label="返回上一页" onClick={onBack} />}
          <Link href="/" aria-label="静观 TV 首页" className="flex items-center shrink-0">
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img
              src="/icons/icon-512.png"
              alt="静观 TV"
              className="w-8 h-8 rounded-xl border border-line shadow-sm"
            />
          </Link>

          {showSearch && (
            <form
              className="flex-1 max-w-xl hidden sm:block"
              onSubmit={(e) => {
                e.preventDefault();
                submitSearch(query);
              }}
            >
              <div ref={searchHistory.containerRef} className="relative">
                <div
                  className={cn(
                    // 输入框与搜索按钮合并为一个整体：内部无边框，按钮贴住右端
                    'flex items-stretch min-h-10 rounded-lg border bg-chip transition-colors',
                    searchHistory.visible
                      ? 'rounded-t-lg rounded-b-none border-accent border-b-line bg-surface-raised'
                      : 'border-line focus-within:border-accent focus-within:ring-1 focus-within:ring-accent/40'
                  )}
                >
                  <div className="flex items-center pl-3 pr-1">
                    <Icon name="search" className="w-4 h-4 text-faint shrink-0" />
                  </div>
                  <input
                    className="flex-1 min-w-0 bg-transparent text-sm text-content placeholder:text-faint focus:outline-none"
                    aria-label="搜索影片"
                    placeholder="搜索影片..."
                    value={query}
                    maxLength={100}
                    onChange={(e) => {
                      setQuery(e.target.value);
                      searchHistory.resetActive();
                    }}
                    onFocus={searchHistory.onFocus}
                    onKeyDown={(e) => searchHistory.onKeyDown(e, pickHistory)}
                    role="combobox"
                    aria-expanded={searchHistory.visible}
                    aria-controls="header-search-history"
                    aria-autocomplete="list"
                    aria-activedescendant={
                      searchHistory.visible && searchHistory.activeIndex >= 0
                        ? `header-search-history-${searchHistory.activeIndex}`
                        : undefined
                    }
                  />
                  <button
                    type="submit"
                    className="btn-primary shrink-0 !rounded-l-none !rounded-r-lg px-4 font-medium transition-[background-color,filter] active:brightness-90"
                  >
                    搜索
                  </button>
                </div>
                {searchHistory.visible && (
                  <SearchHistoryDropdown
                    id="header-search-history"
                    matches={searchHistory.matches}
                    activeIndex={searchHistory.activeIndex}
                    onPick={pickHistory}
                    onRemove={searchHistory.remove}
                    onClearAll={searchHistory.clearAll}
                  />
                )}
              </div>
            </form>
          )}

          <div className="flex-1 sm:hidden" />

          <nav className="flex items-center gap-1 ml-auto">
            <HeaderLink href="/live" active={pathname === '/live'}>
              直播
            </HeaderLink>
            <HeaderLink href="/about" active={pathname === '/about'}>
              关于
            </HeaderLink>
            <ThemeToggle />
            <IconButton label="观看历史" onClick={() => setHistoryOpen(true)}>
              <Icon name="clock" />
            </IconButton>
            <IconButton label="下载管理" onClick={requestShowDownloadManager}>
              <Icon name="download" />
            </IconButton>
            <IconButton label="设置" onClick={() => setSettingsOpen(true)}>
              <Icon name="gear" />
            </IconButton>
          </nav>
        </div>
      </header>

      <SourceManagerDrawer open={settingsOpen} onClose={() => setSettingsOpen(false)} />
      <HistoryPanel open={historyOpen} onClose={() => setHistoryOpen(false)} />
    </>
  );
}

function HeaderLink({ href, active, children }: { href: string; active: boolean; children: React.ReactNode }) {
  return (
    <Link
      href={href}
      className={cn(
        'px-2.5 py-1.5 rounded-lg text-sm transition-colors',
        active ? 'text-content font-medium' : 'text-muted hover:text-content'
      )}
    >
      {children}
    </Link>
  );
}

function IconButton({ label, onClick, children }: { label: string; onClick: () => void; children: React.ReactNode }) {
  return (
    <button className="btn-icon" title={label} aria-label={label} onClick={onClick}>
      {children}
    </button>
  );
}
