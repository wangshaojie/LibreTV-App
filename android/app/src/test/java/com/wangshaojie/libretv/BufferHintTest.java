package com.wangshaojie.libretv;

import org.junit.Test;
import androidx.media3.common.Player;
import static org.junit.Assert.*;

/**
 * 「缓冲中」提示的判定逻辑。
 *
 * 用户反馈：缓冲的时候画面不动，也没有任何示意在缓冲加载中。
 * 静止的画面 + 零提示，用户只会以为应用死了或者电视坏了。
 *
 * 后来又反馈：视频正常播放中，还是会冒出「缓冲中」。
 * 根因是老实现把 player.isLoading() 当成了缓冲信号 ——
 * ExoPlayer 正常播放时本来就在一边播一边往后取数据，这个标志一直是真，
 * 于是画面好好放着也会在 800ms 后被判定成缓冲，还反复闪烁。
 * 现在判据换成「画面停没停」（播放头走没走），见 {@link BufferHint}。
 *
 * 这些判定抽成了纯类 {@link BufferHint}（不碰任何控件），所以能直接单测。
 * 其中最要紧的一条是「连续轮询不能把计时重置」——
 * 那是一个真实踩过的坑：老实现每 400ms 轮询到一次「还在加载」，
 * 就把「800ms 后亮转圈」这个延时任务取消再重排一次，
 * 轮询间隔比延时短，于是任务被无限重置，转圈永远不出现。
 */
public class BufferHintTest {

    private static final long DELAY = 800L;
    /** 轮询间隔，和 PlayerActivity 里的 bufferWatch 一致。 */
    private static final long TICK = 400L;

    /**
     * 卡住的判定：看画面停没停，不看播放器在不在取数据。
     */
    @Test
    public void stalledIsDecidedByStateAndPlayhead() {
        assertTrue("没有数据可播必然停住", BufferHint.stalled(Player.STATE_BUFFERING, true, false));
        assertTrue("就绪、也在播，播放头却不走 = 卡住", BufferHint.stalled(Player.STATE_READY, true, false));
        assertFalse("就绪、也在播、播放头在走 = 正常播放", BufferHint.stalled(Player.STATE_READY, true, true));
        assertFalse("用户按了暂停，位置不动是正常的", BufferHint.stalled(Player.STATE_READY, false, false));
        assertFalse("还没开始播不算卡", BufferHint.stalled(Player.STATE_IDLE, true, false));
        assertFalse("放完了不算卡", BufferHint.stalled(Player.STATE_ENDED, true, false));
    }

    /**
     * 核心回归：正常播放时永远不该提示「缓冲中」。
     *
     * 这正是老实现误报的场景 —— 播放器一直在往后取数据（isLoading() 为真），
     * 但画面好好地在动。现在只看播放头有没有往前走，所以整段播放都不会亮转圈。
     */
    @Test
    public void normalPlaybackNeverShowsTheHint() {
        BufferHint hint = new BufferHint(DELAY);
        long t = 0L;
        for (int i = 0; i < 50; i++) {
            int action = hint.update(Player.STATE_READY, true, true, t + TICK * i);
            assertEquals("正常播放中不该提示缓冲", BufferHint.NONE, action);
        }
        assertFalse(hint.isShown());
        assertFalse(hint.isStalled());
    }

    /**
     * 核心回归：一直卡着，转圈必须真的亮起来。
     *
     * 老实现这里永远等不到 —— 计时被每轮轮询重置了。
     */
    @Test
    public void hintAppearsWhileStuckKeepsGoing() {
        BufferHint hint = new BufferHint(DELAY);
        long t = 1_000_000L;
        assertEquals("刚开始卡不该立刻提示", BufferHint.NONE, hint.update(Player.STATE_BUFFERING, true, false, t));
        assertEquals("卡 400ms 还不够 800ms，不提示", BufferHint.NONE, hint.update(Player.STATE_BUFFERING, true, false, t + TICK));
        assertEquals("卡满 800ms 必须亮转圈", BufferHint.SHOW, hint.update(Player.STATE_BUFFERING, true, false, t + TICK * 2));
        assertTrue(hint.isShown());
    }

    /** 连续相同状态的轮询不能反复触发 SHOW，也不能把计时重置掉。 */
    @Test
    public void repeatedPollsDoNotResetTheTimer() {
        BufferHint hint = new BufferHint(DELAY);
        long t = 0L;
        hint.update(Player.STATE_BUFFERING, true, false, t);
        // 每 400ms 轮询一次，一直卡着 —— 老实现每次都会重排延时任务
        for (int i = 1; i <= 10; i++) {
            int action = hint.update(Player.STATE_BUFFERING, true, false, t + TICK * i);
            if (i < 2) {
                assertEquals("还没到 800ms，不该提示", BufferHint.NONE, action);
            } else if (i == 2) {
                assertEquals("到 800ms 的那一刻要亮转圈", BufferHint.SHOW, action);
            } else {
                assertEquals("亮过之后每轮都应该是 NONE（只提示一次）", BufferHint.NONE, action);
            }
        }
        assertTrue("转圈必须已经亮着", hint.isShown());
    }

    /** 短暂卡顿不提示 —— 闪一下比不提示更烦。 */
    @Test
    public void briefStutterNeverShowsTheHint() {
        BufferHint hint = new BufferHint(DELAY);
        long t = 0L;
        assertEquals(BufferHint.NONE, hint.update(Player.STATE_BUFFERING, true, false, t));
        assertEquals("卡了一下就恢复了，不提示", BufferHint.NONE, hint.update(Player.STATE_READY, true, true, t + 300));
        assertFalse(hint.isShown());
    }

    /** 恢复之后再次卡住，要能再提示一次。 */
    @Test
    public void hintComesBackAfterRecovery() {
        BufferHint hint = new BufferHint(DELAY);
        long t = 0L;
        hint.update(Player.STATE_BUFFERING, true, false, t);
        assertEquals(BufferHint.SHOW, hint.update(Player.STATE_BUFFERING, true, false, t + DELAY));
        assertEquals("恢复了要收掉转圈", BufferHint.HIDE, hint.update(Player.STATE_READY, true, true, t + DELAY + TICK));
        assertFalse(hint.isShown());
        // 第二次卡住：从头计时
        assertEquals(BufferHint.NONE, hint.update(Player.STATE_BUFFERING, true, false, t + DELAY + TICK * 2));
        assertEquals(BufferHint.SHOW, hint.update(Player.STATE_BUFFERING, true, false, t + DELAY + TICK * 4));
    }

    /** 复位（切集 / 出错 / 退出播放页）之后不能留下「上一次还在卡」的残留状态。 */
    @Test
    public void resetClearsEverything() {
        BufferHint hint = new BufferHint(DELAY);
        hint.update(Player.STATE_BUFFERING, true, false, 0L);
        assertEquals(BufferHint.SHOW, hint.update(Player.STATE_BUFFERING, true, false, DELAY));
        hint.reset();
        assertFalse(hint.isShown());
        assertFalse(hint.isStalled());
        // 复位之后重新开始计时：不会拿上一段的时长直接亮
        assertEquals(BufferHint.NONE, hint.update(Player.STATE_BUFFERING, true, false, DELAY));
        assertEquals(BufferHint.SHOW, hint.update(Player.STATE_BUFFERING, true, false, DELAY + DELAY));
    }

    /** 出错时不会去收一个根本没亮过的转圈（否则会误发 HIDE）。 */
    @Test
    public void hideOnlyComesAfterTheHintWasShown() {
        BufferHint hint = new BufferHint(DELAY);
        assertEquals("没亮过就不该有 HIDE", BufferHint.NONE, hint.update(Player.STATE_READY, true, true, 0L));
    }

    /**
     * 端到端走一遍真实节奏：每 400ms 轮询一次，
     * 正常播 2 秒 → 卡住 5 秒 → 恢复 —— 转圈应当恰好亮过一次。
     */
    @Test
    public void wholeTimelineShowsTheHintExactlyOnce() {
        BufferHint hint = new BufferHint(DELAY);
        long t = 0L;
        int shows = 0, hides = 0;
        // 前 2 秒正常播放：播放头一直在走
        for (int i = 0; i < 5; i++) hint.update(Player.STATE_READY, true, true, t + TICK * i);
        // 接着卡住 5 秒：状态进入 BUFFERING，播放头也不动
        for (int i = 5; i < 18; i++) {
            int a = hint.update(Player.STATE_BUFFERING, true, false, t + TICK * i);
            if (a == BufferHint.SHOW) shows++;
        }
        // 恢复
        for (int i = 18; i < 25; i++) {
            int a = hint.update(Player.STATE_READY, true, true, t + TICK * i);
            if (a == BufferHint.HIDE) hides++;
        }
        assertEquals("整段卡顿里只该亮一次转圈", 1, shows);
        assertEquals("恢复时收一次转圈", 1, hides);
    }

    /**
     * 用户按了暂停：播放头不动，但这是用户要的，不该提示缓冲。
     */
    @Test
    public void pausedPlaybackIsNotAStall() {
        BufferHint hint = new BufferHint(DELAY);
        long t = 0L;
        for (int i = 0; i < 10; i++) {
            int action = hint.update(Player.STATE_READY, false, false, t + TICK * i);
            assertEquals("暂停中不该提示缓冲", BufferHint.NONE, action);
        }
        assertFalse(hint.isShown());
    }

    /**
     * 被系统压制（来电、音频焦点丢失）：画面停住不是网络的问题，不该提示缓冲。
     */
    @Test
    public void suppressedPlaybackIsNotAStall() {
        BufferHint hint = new BufferHint(DELAY);
        long t = 0L;
        for (int i = 0; i < 10; i++) {
            int action = hint.update(Player.STATE_READY, false, false, t + TICK * i);
            assertEquals("被系统压制时不该提示缓冲", BufferHint.NONE, action);
        }
        assertFalse(hint.isShown());
    }

    /**
     * 状态还是 READY、但播放头卡住不动：真卡了，要提示。
     *
     * 这条覆盖的是「状态没变但画面停住」的情况 —— 只看 STATE_BUFFERING 会漏掉它。
     */
    @Test
    public void aStalledPlayheadInReadyStateStillShowsTheHint() {
        BufferHint hint = new BufferHint(DELAY);
        long t = 0L;
        assertEquals(BufferHint.NONE, hint.update(Player.STATE_READY, true, false, t));
        assertEquals(BufferHint.NONE, hint.update(Player.STATE_READY, true, false, t + TICK));
        assertEquals("卡满 800ms 要亮转圈", BufferHint.SHOW, hint.update(Player.STATE_READY, true, false, t + TICK * 2));
    }
}
