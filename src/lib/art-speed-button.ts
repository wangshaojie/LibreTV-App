'use client';

/**
 * 给 ArtPlayer 的控制栏加一枚「倍速」按钮。
 *
 * ArtPlayer 自带的倍速藏在设置面板里（要开面板 → 找倍速 → 再选一档），
 * 三步才能改一次速度，太深了。主流播放器（Bilibili、腾讯、Plex）都把倍速
 * 直接放在控制栏上，点一下切下一档，当前倍率常驻显示。
 *
 * 倍速走 ArtPlayer 的 `playbackRate` 实例属性（可读可写），它内部会弹一条
 * 「Rate: 1.5x」提示，并且播放中变速不会丢进度。
 */

/** 循环的档位。1x 是常态，排在最后作为回归点。 */
const SPEEDS = [1, 1.25, 1.5, 2, 0.5];

function formatSpeed(v: number): string {
  return `${v % 1 === 0 ? v.toFixed(0) : v.toFixed(2).replace(/0$/, '')}x`;
}

/**
 * 在控制栏右侧插入倍速按钮。
 * @param art ArtPlayer 实例
 * @returns 卸载时执行的清理函数
 */
export function attachSpeedControl(art: any): () => void {
  const root = art?.template?.$player as HTMLElement | undefined;
  if (!root) return () => {};
  const right = root.querySelector('.art-controls-right');
  if (!right) return () => {};

  const btn = document.createElement('div');
  btn.className = 'art-control art-ctl-speed';
  btn.setAttribute('role', 'button');
  btn.setAttribute('title', '播放速度');
  btn.setAttribute('aria-label', '播放速度');
  btn.textContent = formatSpeed(art.playbackRate ?? 1);

  const sync = () => {
    const v = art.playbackRate ?? 1;
    btn.textContent = formatSpeed(v);
    btn.classList.toggle('is-fast', v !== 1);
  };

  const onClick = (e: MouseEvent) => {
    e.stopPropagation();
    e.preventDefault();
    const cur = art.playbackRate ?? 1;
    const idx = SPEEDS.findIndex((s) => Math.abs(s - cur) < 0.001);
    const next = SPEEDS[(idx + 1) % SPEEDS.length] ?? 1;
    try {
      art.playbackRate = next;
    } catch {
      /* 忽略：部分源不支持变速 */
    }
    sync();
  };

  btn.addEventListener('click', onClick);
  // 倍速被外部改过（比如设置面板里选），把按钮上的文字同步过来
  art.on?.('video:ratechange', sync);
  right.insertBefore(btn, right.firstChild);

  return () => {
    btn.removeEventListener('click', onClick);
    btn.remove();
  };
}
