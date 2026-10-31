'use client';

import { useRouter } from 'next/navigation';
import { Icon } from './icon';
import { cn } from '@/lib/utils';

/**
 * 返回上一级（全局统一入口）。
 *
 * 免安装版没有浏览器地址栏，也没有系统返回手势，任何「回到上一层」都必须有显式入口，
 * 因此所有二级页面/浮层都用同一套返回按钮：描边方块 + 左箭头。
 *
 * 行为：有历史就回退上一级；没有（直接打开链接进入）时回落到首页，避免点了没反应。
 */
export function BackButton({
  fallback = '/',
  label = '返回上一页',
  withText = false,
  /** 自定义行为：传入后不再走 history 回退（例如首页搜索结果要清掉搜索词回推荐区） */
  onClick,
  className,
}: {
  /** 无历史可退时的回落地址 */
  fallback?: string;
  label?: string;
  /** 是否带「返回」文字（图标按钮时只留箭头） */
  withText?: boolean;
  onClick?: () => void;
  className?: string;
}) {
  const router = useRouter();
  const goBack = () => {
    if (onClick) {
      onClick();
      return;
    }
    // 只有确实有上一级时才回退；直接打开链接进入（无历史）则回落到首页
    if (typeof window !== 'undefined' && window.history.length > 1) {
      router.back();
    } else {
      router.push(fallback);
    }
  };

  return (
    <button
      className={cn(withText ? 'btn-outline btn-sm gap-1.5' : 'btn-icon', className)}
      onClick={goBack}
      aria-label={label}
      title={label}
    >
      <Icon name="arrowLeft" className={withText ? 'w-3.5 h-3.5' : 'w-5 h-5'} />
      {withText && <span>返回</span>}
    </button>
  );
}

/** 回首页：与播放页顶栏的 home 按钮同款 */
export function HomeButton({ label = '回首页', className }: { label?: string; className?: string }) {
  const router = useRouter();
  return (
    <button
      className={cn('btn-icon', className)}
      onClick={() => router.push('/')}
      aria-label={label}
      title={label}
    >
      <Icon name="home" className="w-5 h-5" />
    </button>
  );
}
