package com.wangshaojie.libretv;

import androidx.media3.common.Player;

/**
 * 「缓冲中」提示的状态机 —— 纯逻辑，不碰任何 Android 控件，可以直接单测。
 *
 * 要解决的问题：播放卡顿时画面是静止的，又没有任何提示，
 * 用户只会以为应用死了或者电视坏了。所以画面中央要有转圈 + 文案。
 *
 * 这里把三个判断从 PlayerActivity 里搬出来，理由是它们各自都踩过坑：
 *
 *   1. 什么时候算「在缓冲」：靠播放状态判断，不靠回调。
 *      实测这台设备上的 media3 不会派发 onIsLoadingChanged，
 *      onPlaybackStateChanged 也未必带 STATE_BUFFERING —— 等回调就永远等不到，
 *      所以只能主动问播放器现在什么状态。
 *
 *   2. 短暂卡顿不提示：只闪一下比不提示更烦，
 *      所以要连续加载满 delayMs 才亮转圈。
 *
 *   3. 计时只从「进入加载态」那一次开始算，不能被每轮轮询重置。
 *      这条是踩得最狠的一个坑：老实现每 400ms 轮询到一次「还在加载」，
 *      就把「800ms 后显示转圈」这个延时任务 removeCallbacks 掉再重新排一次，
 *      而轮询间隔（400ms）比延时（800ms）短 —— 于是这个任务被无限重置，
 *      永远等不到执行，转圈永远不出现（面板文案是另一条路径，所以文案出得来、圆环出不来）。
 *      现在把「进入加载态的时刻」记在状态机里，只有状态翻转才重新计时。
 *
 * 用法：每 400ms 喂一次播放器状态，返回 SHOW / HIDE / NONE，
 * PlayerActivity 只负责按动作显示或隐藏那层转圈。
 */
public final class BufferHint {

    /** 动作：什么都不用做。 */
    public static final int NONE = 0;
    /** 动作：该显示转圈了。 */
    public static final int SHOW = 1;
    /** 动作：该把转圈收起来了。 */
    public static final int HIDE = 2;

    private final long delayMs;

    /** 当前是否处于加载态。 */
    private boolean loading;
    /** 转圈是否已经亮着（避免重复显示，也避免重复触发）。 */
    private boolean shown;
    /** 进入加载态的时刻；-1 表示当前不在加载态。 */
    private long sinceMs = -1L;

    public BufferHint(long delayMs) {
        this.delayMs = delayMs;
    }

    /**
     * 播放器当前算不算「在缓冲」。
     *
     * STATE_BUFFERING 一定算；
     * 某些时候状态已经是 READY，但播放器仍在补数据（isLoading 为真），画面一样是静止的，也算。
     */
    public static boolean isLoading(int playbackState, boolean playerLoading) {
        return playbackState == Player.STATE_BUFFERING || playerLoading;
    }

    /**
     * 喂一次播放器状态，返回要做的动作。
     *
     * @param playbackState player.getPlaybackState()
     * @param playerLoading player.isLoading()
     * @param nowMs         单调时钟毫秒（SystemClock.uptimeMillis）
     */
    public int update(int playbackState, boolean playerLoading, long nowMs) {
        boolean load = isLoading(playbackState, playerLoading);
        if (load != loading) {
            loading = load;
            if (load) {
                // 进入加载态：从这里开始计时，不是从「下一次轮询」开始。
                sinceMs = nowMs;
            } else {
                sinceMs = -1L;
                if (shown) {
                    shown = false;
                    return HIDE;
                }
                return NONE;
            }
        }
        if (!loading || shown) return NONE;
        if (sinceMs >= 0L && nowMs - sinceMs >= delayMs) {
            shown = true;
            return SHOW;
        }
        return NONE;
    }

    /** 转圈是不是正亮着。 */
    public boolean isShown() {
        return shown;
    }

    /** 当前是不是在加载态。 */
    public boolean isLoading() {
        return loading;
    }

    /**
     * 强制复位：出错、切集、退出播放页时调用。
     *
     * 复位之后下一次进入加载态会重新从头计时 —— 不会拿上一段的时长来算。
     */
    public void reset() {
        loading = false;
        shown = false;
        sinceMs = -1L;
    }
}
