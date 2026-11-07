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

    /** 状态判定：BUFFERING 算加载；READY 但还在补数据也算（画面一样是静止的）。 */
    @Test
    public void loadingIsDecidedByStateAndPlayerLoadingFlag() {
        assertTrue(BufferHint.isLoading(androidx.media3.common.Player.STATE_BUFFERING, false));
        assertTrue("状态已就绪但仍在补数据，画面也是静止的，同样要提示",
                BufferHint.isLoading(androidx.media3.common.Player.STATE_READY, true));
        assertFalse(BufferHint.isLoading(androidx.media3.common.Player.STATE_READY, false));
        assertFalse(BufferHint.isLoading(androidx.media3.common.Player.STATE_IDLE, false));
    }

    /**
     * 核心回归：一直加载着，转圈必须真的亮起来。
     *
     * 旧实现这里永远等不到 —— 计时被每轮轮询重置了。
     */
    @Test
    public void hintAppearsWhileLoadingKeepsGoing() {
        BufferHint hint = new BufferHint(DELAY);
        long t = 1_000_000L;
        assertEquals("刚开始加载不该立刻提示", BufferHint.NONE, hint.update(Player.STATE_BUFFERING, false, t));
        assertEquals("加载 400ms 还不够 800ms，不提示", BufferHint.NONE, hint.update(Player.STATE_BUFFERING, false, t + TICK));
        assertEquals("加载满 800ms 必须亮转圈", BufferHint.SHOW, hint.update(Player.STATE_BUFFERING, false, t + TICK * 2));
        assertTrue(hint.isShown());
    }

    /** 连续相同状态的轮询不能反复触发 SHOW，也不能把计时重置掉。 */
    @Test
    public void repeatedPollsDoNotResetTheTimer() {
        BufferHint hint = new BufferHint(DELAY);
        long t = 0L;
        hint.update(Player.STATE_BUFFERING, false, t);
        // 每 400ms 轮询一次，一直加载着 —— 老实现每次都会重排延时任务
        for (int i = 1; i <= 10; i++) {
            int action = hint.update(Player.STATE_BUFFERING, false, t + TICK * i);
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
        assertEquals(BufferHint.NONE, hint.update(Player.STATE_BUFFERING, false, t));
        assertEquals("卡了一下就恢复了，不提示", BufferHint.NONE, hint.update(Player.STATE_READY, false, t + 300));
        assertFalse(hint.isShown());
    }

    /** 恢复之后再次缓冲，要能再提示一次。 */
    @Test
    public void hintComesBackAfterRecovery() {
        BufferHint hint = new BufferHint(DELAY);
        long t = 0L;
        hint.update(Player.STATE_BUFFERING, false, t);
        assertEquals(BufferHint.SHOW, hint.update(Player.STATE_BUFFERING, false, t + DELAY));
        assertEquals("恢复了要收掉转圈", BufferHint.HIDE, hint.update(Player.STATE_READY, false, t + DELAY + TICK));
        assertFalse(hint.isShown());
        // 第二次缓冲：从头计时
        assertEquals(BufferHint.NONE, hint.update(Player.STATE_BUFFERING, false, t + DELAY + TICK * 2));
        assertEquals(BufferHint.SHOW, hint.update(Player.STATE_BUFFERING, false, t + DELAY + TICK * 4));
    }

    /** 复位（切集 / 出错 / 退出播放页）之后不能留下「上一次还在加载」的残留状态。 */
    @Test
    public void resetClearsEverything() {
        BufferHint hint = new BufferHint(DELAY);
        hint.update(Player.STATE_BUFFERING, false, 0L);
        assertEquals(BufferHint.SHOW, hint.update(Player.STATE_BUFFERING, false, DELAY));
        hint.reset();
        assertFalse(hint.isShown());
        assertFalse(hint.isLoading());
        // 复位之后重新开始计时：不会拿上一段的时长直接亮
        assertEquals(BufferHint.NONE, hint.update(Player.STATE_BUFFERING, false, DELAY));
        assertEquals(BufferHint.SHOW, hint.update(Player.STATE_BUFFERING, false, DELAY + DELAY));
    }

    /** 出错时不会去收一个根本没亮过的转圈（否则会误发 HIDE）。 */
    @Test
    public void hideOnlyComesAfterTheHintWasShown() {
        BufferHint hint = new BufferHint(DELAY);
        assertEquals("没亮过就不该有 HIDE", BufferHint.NONE, hint.update(Player.STATE_READY, false, 0L));
    }

    /**
     * 端到端走一遍真实节奏：每 400ms 轮询一次，
     * 加载持续 5 秒后恢复 —— 转圈应当恰好亮过一次。
     */
    @Test
    public void wholeTimelineShowsTheHintExactlyOnce() {
        BufferHint hint = new BufferHint(DELAY);
        long t = 0L;
        int shows = 0, hides = 0;
        // 前 2 秒正常播放
        for (int i = 0; i < 5; i++) hint.update(Player.STATE_READY, false, t + TICK * i);
        // 接着缓冲 5 秒
        for (int i = 5; i < 18; i++) {
            int a = hint.update(Player.STATE_BUFFERING, false, t + TICK * i);
            if (a == BufferHint.SHOW) shows++;
        }
        // 恢复
        for (int i = 18; i < 25; i++) {
            int a = hint.update(Player.STATE_READY, false, t + TICK * i);
            if (a == BufferHint.HIDE) hides++;
        }
        assertEquals("整段缓冲里只该亮一次转圈", 1, shows);
        assertEquals("恢复时收一次转圈", 1, hides);
    }
}
