'use client';

/**
 * 鼠标停在播放器底部控制区时，不要让控制栏自动收起。
 *
 * ArtPlayer 的自动隐藏只看一个计时器：鼠标停下来几秒后，它就把
 * `art-control-show` 摘掉，整条控制层淡出 —— 哪怕指针正停在控制栏上。
 * 想保留「鼠标指着就看得见」这个直觉，得自己接管这一段。
 *
 * 实现上不监听控制栏自己的 mouseenter/mouseleave：
 * 控制栏淡出时会被 translateY 挪到画面下方几十像素，指针位置和它错开，
 * mouseenter/mouseleave 会反复横跳。改成监听播放器上的 mousemove，
 * 按指针的绝对位置判断「是否在底部控制区」，这个判断跟控制栏显隐无关。
 *
 * 指针静止后不再有 mousemove，但标志会一直留着 —— 这正是要的：
 * 指针停在控制栏上，控制栏就一直显示；指针移出底部区域才交还给 ArtPlayer。
 */

/** 底部控制区的高度（像素）。控制栏 + 进度条大致占这么多。 */
const CONTROL_ZONE = 120;

/**
 * 给播放器根节点挂上「指针悬停控制区」的行为。
 * @param root ArtPlayer 的根元素（.art-video-player）
 * @returns 卸载时执行的清理函数
 */
export function keepControlsWhileHovered(root: HTMLElement | null): () => void {
  if (!root) return () => {};

  let hovering = false;
  const setHover = (on: boolean) => {
    if (on === hovering) return;
    hovering = on;
    root.classList.toggle('art-ctl-hover', on);
  };

  const onMove = (e: MouseEvent) => {
    const r = root.getBoundingClientRect();
    if (!r.height) return;
    setHover(e.clientY >= r.bottom - CONTROL_ZONE && e.clientY <= r.bottom + 8);
  };
  const onLeave = () => setHover(false);

  root.addEventListener('mousemove', onMove);
  root.addEventListener('mouseleave', onLeave);

  return () => {
    root.removeEventListener('mousemove', onMove);
    root.removeEventListener('mouseleave', onLeave);
    root.classList.remove('art-ctl-hover');
  };
}
