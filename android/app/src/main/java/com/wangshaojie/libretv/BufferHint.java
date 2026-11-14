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
 *   1. 什么时候算「在缓冲」：看画面停没停，不能看播放器在不在取数据。
 *      老实现把 player.isLoading() 也当成缓冲信号，这是个错得很隐蔽的坑：
 *      ExoPlayer 正常播放时本来就在一边播一边往后取数据，
 *      这时 getPlaybackState() 还是 STATE_READY，isLoading() 却为真 ——
 *      照它判，800ms 之后就会在画面好好放着的时候弹出「缓冲中」，
 *      而且播放器一停下来取数据就闪一下、一继续取又亮起来，反复闪烁。
 *      media3 自己的 PlayerView 判转圈也只用 STATE_BUFFERING，不看 isLoading()。
 *
 *      真正能区分「卡了」和「在取数据」的，是画面停没停：
 *        - STATE_BUFFERING：没有数据可播，画面必然停住；
 *        - STATE_READY 但播放头不往前走：卡住了；
 *        - 用户按了暂停、或者被系统压制（来电、音频焦点丢失）：位置不动是正常的，不算卡。
 *
 *   2. 短暂卡顿不提示：只闪一下比不提示更烦，
 *      所以要连续卡住满 delayMs 才亮转圈。
 *
 *   3. 计时只从「进入卡住状态」那一次开始算，不能被每轮轮询重置。
 *      这条是踩得最狠的一个坑：老实现每 400ms 轮询到一次「还在加载」，
 *      就把「800ms 后显示转圈」这个延时任务 removeCallbacks 掉再重新排一次，
 *      而轮询间隔（400ms）比延时（800ms）短 —— 于是这个任务被无限重置，
 *      永远等不到执行，转圈永远不出现（面板文案是另一条路径，所以文案出得来、圆环出不来）。
 *      现在把「进入卡住状态的时刻」记在状态机里，只有状态翻转才重新计时。
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

    /** 画面是不是正停着（真在缓冲）。 */
    private boolean stuck;
    /** 转圈是否已经亮着（避免重复显示，也避免重复触发）。 */
    private boolean shown;
    /** 进入卡住状态的时刻；-1 表示当前没卡住。 */
    private long sinceMs = -1L;

    public BufferHint(long delayMs) {
        this.delayMs = delayMs;
    }

    /**
     * 画面是不是真的停住了。
     *
     * @param playbackState      player.getPlaybackState()
     * @param shouldAdvance      画面「应该」往前走吗（用户要播，且没被系统压制）
     * @param positionAdvancing  这一轮轮询里播放头有没有往前走
     */
    public static boolean stalled(int playbackState, boolean shouldAdvance, boolean positionAdvancing) {
        // 没有数据可播，画面必然停住 —— 这是唯一不用猜的状态。
        if (playbackState == Player.STATE_BUFFERING) return true;
        // IDLE（还没准备）、ENDED（放完了）都不是卡。
        if (playbackState != Player.STATE_READY) return false;
        // 暂停 / 被系统压制（来电、音频焦点丢失）时位置不动是正常的，不算卡。
        if (!shouldAdvance) return false;
        // 就绪、也在播，播放头却不走 —— 这才是真卡住。
        return !positionAdvancing;
    }

    /**
     * 喂一次播放器状态，返回要做的动作。
     *
     * @param playbackState      player.getPlaybackState()
     * @param shouldAdvance      画面「应该」往前走吗（用户要播，且没被系统压制）
     * @param positionAdvancing  这一轮轮询里播放头有没有往前走
     * @param nowMs              单调时钟毫秒（SystemClock.uptimeMillis）
     */
    public int update(int playbackState, boolean shouldAdvance, boolean positionAdvancing, long nowMs) {
        boolean now = stalled(playbackState, shouldAdvance, positionAdvancing);
        if (now != stuck) {
            stuck = now;
            if (now) {
                // 进入卡住状态：从这里开始计时，不是从「下一次轮询」开始。
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
        if (!stuck || shown) return NONE;
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

    /** 画面是不是正停着。 */
    public boolean isStalled() {
        return stuck;
    }

    /**
     * 强制复位：出错、切集、退出播放页时调用。
     *
     * 复位之后下一次卡住会重新从头计时 —— 不会拿上一段的时长来算。
     */
    public void reset() {
        stuck = false;
        shown = false;
        sinceMs = -1L;
    }
}
