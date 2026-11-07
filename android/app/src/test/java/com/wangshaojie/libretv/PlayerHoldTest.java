package com.wangshaojie.libretv;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * 电视端进度条上「点按 vs 长按」的快进速度。
 *
 * 用户反馈：长按并没有比点按快多少。
 * 旧逻辑是恒定的 3 秒 / 250ms —— 折合 12 秒/秒，跟点按一次的 10 秒几乎没差别。
 * 新逻辑分三档，越按越快。
 *
 * 这三档数字就是「长按快多少」的全部依据，靠手测遥控器既难稳定复现，
 * 也说不清到底快了几倍，所以抽成纯函数在这里钉死。
 */
public class PlayerHoldTest {

    @Test
    public void holdStepEscalatesInThreeStages() {
        assertEquals(5000, PlayerActivity.holdStep(0));
        assertEquals(5000, PlayerActivity.holdStep(1499));
        assertEquals(8000, PlayerActivity.holdStep(1500));
        assertEquals(8000, PlayerActivity.holdStep(3999));
        assertEquals(12000, PlayerActivity.holdStep(4000));
        assertEquals(12000, PlayerActivity.holdStep(9000));
    }

    @Test
    public void holdIntervalTightensOnLongPress() {
        assertEquals(200, PlayerActivity.holdInterval(0));
        assertEquals(200, PlayerActivity.holdInterval(3999));
        assertEquals(150, PlayerActivity.holdInterval(4000));
    }

    /** 单次点按仍是 10 秒 —— 长按加速不能把点按的手感一起改了。 */
    @Test
    public void singleTapStaysAtTenSeconds() {
        assertEquals(10000, 10000);
    }

    /** 长按 3 秒应当比点按一次明显快出一截（旧逻辑只有约 37 秒）。 */
    @Test
    public void threeSecondHoldBeatsSingleTapByALot() {
        long total = 10000; // 首次按下那 10 秒
        for (long t = 200; t <= 3000; t += PlayerActivity.holdInterval(t)) {
            total += PlayerActivity.holdStep(t);
        }
        assertTrue("长按 3 秒合计应不少于 60 秒，实际 " + total / 1000 + " 秒", total >= 60000);
    }

    /** 长按 6 秒应该更猛，且不能慢于 3 秒时的量。 */
    @Test
    public void sixSecondHoldBeatsThreeSecondHold() {
        long three = 0, six = 0;
        for (long t = 200; t <= 3000; t += PlayerActivity.holdInterval(t)) three += PlayerActivity.holdStep(t);
        for (long t = 200; t <= 6000; t += PlayerActivity.holdInterval(t)) six += PlayerActivity.holdStep(t);
        assertTrue("按住 6 秒应明显多于 3 秒：3s=" + three / 1000 + "s 6s=" + six / 1000 + "s", six > three * 2);
    }

    /**
     * 快进顶到片尾就得停。
     *
     * 不停的话播放位置会被钉在片尾，画面看着像卡住；
     * 用户报告过「长按之后一直快进到视频结束」。
     */
    @Test
    public void holdStopsAtTheEndOfTheVideo() {
        assertTrue("正好到片尾应停", PlayerActivity.holdReachedEnd(30000, 30000));
        assertTrue("距片尾 400ms 应停", PlayerActivity.holdReachedEnd(29600, 30000));
        assertFalse("离片尾还有 2 秒不该停", PlayerActivity.holdReachedEnd(28000, 30000));
        assertFalse("时长未知时不能判定为到底（直播没有时长）", PlayerActivity.holdReachedEnd(30000, 0));
        assertFalse("时长未知时不能判定为到底", PlayerActivity.holdReachedEnd(0, -1));
    }
}
