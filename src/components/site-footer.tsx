'use client';

import { useEffect, useState } from 'react';
import { useAuth } from '@/components/auth';

/** 本项目仓库（静观 TV），页脚链接与更新检测都指向这里 */
const REPO_URL = 'https://github.com/wangshaojie/LibreTV-App';

/**
 * 本仓库的最新桌面版 tag（形如 v2.19.2-desktop.9），用于页脚的更新检测。
 *
 * 仓库里同时有 Android 版 tag（android-v2.0.x），那只跟手机端有关，
 * 桌面版页脚不该拿它来比，所以按标签形状过滤。
 */
const REPO_TAGS_API = 'https://api.github.com/repos/wangshaojie/LibreTV-App/tags?per_page=100';
const DESKTOP_TAG = /^v\d+\.\d+\.\d+-desktop\.\d+$/;

/** 页脚版本号的更新检测结果；null 表示无需检测（本地 dev 构建或非桌面版） */
type RepoUpdate = { status: 'checking' | 'latest' | 'newer'; latest?: string };

/** 点分版本号比较：a 大于 b 返回正数 */
function compareVersions(a: string, b: string): number {
  const pa = a.split('.').map((n) => parseInt(n, 10) || 0);
  const pb = b.split('.').map((n) => parseInt(n, 10) || 0);
  for (let i = 0; i < Math.max(pa.length, pb.length); i++) {
    const diff = (pa[i] || 0) - (pb[i] || 0);
    if (diff !== 0) return diff;
  }
  return 0;
}

/** 检测本仓库是否有更新的桌面版；检测失败或非桌面版时静默返回 null */
function useRepoUpdate(current: string | null): RepoUpdate | null {
  const [state, setState] = useState<RepoUpdate | null>({ status: 'checking' });
  useEffect(() => {
    // 只有桌面版才有独立的版本号与对应的 tag；Web 版沿用上游版本号，不做检测
    if (!current || current === 'dev' || !/desktop\./.test(current)) {
      setState(null);
      return;
    }
    // 按当前版本隔离缓存到 sessionStorage：一次会话最多请求一次 GitHub API
    const cacheKey = `repo-latest-tag:${current}`;
    const cached = sessionStorage.getItem(cacheKey);
    if (cached) {
      setState(
        compareVersions(cached, current) > 0
          ? { status: 'newer', latest: cached }
          : { status: 'latest' },
      );
      return;
    }
    let alive = true;
    fetch(REPO_TAGS_API)
      .then((r) => (r.ok ? r.json() : []))
      .then((tags: { name?: string }[]) => {
        const name = tags.map((t) => t.name || '').find((n) => DESKTOP_TAG.test(n))?.replace(/^v/, '');
        if (!name || !alive) return;
        try {
          sessionStorage.setItem(cacheKey, name);
        } catch {
          /* 隐私模式等场景下缓存不可写，忽略即可 */
        }
        setState(
          compareVersions(name, current) > 0
            ? { status: 'newer', latest: name }
            : { status: 'latest' },
        );
      })
      .catch(() => {
        /* 网络受限 / 触发 GitHub 限流时静默跳过 */
        if (alive) setState(null);
      });
    return () => {
      alive = false;
    };
  }, [current]);
  return state;
}

/** 页脚仓库链接上的悬停提示：显示更新检测结果 */
function UpdateTip({ update }: { update: RepoUpdate | null }) {
  const tip =
    update === null
      ? null
      : update.status === 'newer'
        ? `发现新版本 v${update.latest}`
        : update.status === 'latest'
          ? '已是最新版本'
          : '正在检测更新…';
  if (!tip) return null;
  return (
    <span className="pointer-events-none absolute bottom-full left-1/2 z-10 mb-1.5 hidden -translate-x-1/2 whitespace-nowrap bg-chip px-2 py-1 text-faint group-hover:block">
      {tip}
    </span>
  );
}

/** 全站统一页脚：仓库链接 + 版本号（悬停显示本仓库更新检测结果） */
export function SiteFooter() {
  const { version } = useAuth();
  const repoUpdate = useRepoUpdate(version);
  return (
    <footer className="border-t border-line py-4">
      <p className="text-center text-xs text-faint">
        <a
          href={REPO_URL}
          target="_blank"
          rel="noopener noreferrer"
          className="group relative inline-block cursor-help hover:text-accent"
        >
          静观 TV{version ? ` v${version}` : ''}
          {version && <UpdateTip update={repoUpdate} />}
        </a>
        {' · '}
        AGPL-3.0 License
      </p>
    </footer>
  );
}
