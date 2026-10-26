'use client';

import { useEffect, useRef, useState } from 'react';

/**
 * 懒挂载容器：视口接近时才渲染内部内容。
 *
 * 首屏之外的区块（推荐网格几十张卡片）提前渲染会带来两笔开销：
 * 一是 DOM 节点与 React 渲染时间，二是这些区块的图片请求会挤占首屏带宽。
 * 这里用 IntersectionObserver 在接近视口时（rootMargin 提前量）才挂载，
 * 挂载后不再卸载，滚动回顶部依然保留内容。
 */
export function LazySection({
  children,
  /** 未挂载时的占位高度，避免布局跳动 */
  minHeight = 320,
  rootMargin = '400px',
}: {
  children: React.ReactNode;
  minHeight?: number;
  rootMargin?: string;
}) {
  const ref = useRef<HTMLDivElement>(null);
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    if (typeof IntersectionObserver === 'undefined') {
      setVisible(true);
      return;
    }
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((e) => e.isIntersecting)) {
          setVisible(true);
          observer.disconnect();
        }
      },
      { rootMargin }
    );
    observer.observe(el);
    return () => observer.disconnect();
  }, [rootMargin]);

  return (
    <div ref={ref} style={visible ? undefined : { minHeight }}>
      {visible ? children : null}
    </div>
  );
}
