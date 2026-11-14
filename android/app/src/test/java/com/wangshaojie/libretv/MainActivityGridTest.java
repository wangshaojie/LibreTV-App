package com.wangshaojie.libretv;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * 电视端网格的选中态与自动续页。
 *
 * 用户反馈两条：
 *   1. 选中某个影片卡片后，卡片上的灰字还是灰的，看不清停在哪一张；
 *   2. 片单要手动点「加载更多」，希望滚到底就自动加载。
 *
 * 这两条的判定都抽成纯函数钉在这里 —— 它们决定「哪一张被高亮」和「什么时候再取一批」，
 * 靠肉眼在电视上很难稳定复现。
 */
public class MainActivityGridTest {

    /** 选中的那一张要挑出来，其余一律不高亮（高亮错的那张比不高亮更糟）。 */
    @Test
    public void onlyTheSelectedCardIsHighlighted() {
        assertTrue(MainActivity.cardSelected(3, 3));
        assertFalse(MainActivity.cardSelected(2, 3));
        assertFalse(MainActivity.cardSelected(4, 3));
    }

    /** 还没有选中项时不高亮任何一张（遥控器还没进网格）。 */
    @Test
    public void nothingIsHighlightedWithoutASelection() {
        assertFalse(MainActivity.cardSelected(0, -1));
        assertFalse(MainActivity.cardSelected(5, -1));
    }

    /** 位置没取到（-1）时不能误判成选中。 */
    @Test
    public void unknownPositionIsNeverHighlighted() {
        assertFalse(MainActivity.cardSelected(-1, 2));
    }

    /** 选中项走到末尾就该自动续下一批。 */
    @Test
    public void selectionNearTheEndTriggersTheNextPage() {
        assertTrue(MainActivity.shouldLoadMore(true, 23, 24));
        assertTrue(MainActivity.shouldLoadMore(true, 21, 24));
        assertFalse(MainActivity.shouldLoadMore(true, 20, 24));
    }

    /** 只有片单页有分页；搜索结果、收藏、记录不参与。 */
    @Test
    public void onlyTheCatalogPageLoadsMore() {
        assertFalse(MainActivity.shouldLoadMore(false, 23, 24));
    }

    /** 还没有选中项、或者一页都还没出来时，不该发请求。 */
    @Test
    public void noRequestWithoutASelectionOrItems() {
        assertFalse(MainActivity.shouldLoadMore(true, -1, 24));
        assertFalse(MainActivity.shouldLoadMore(true, 0, 0));
    }

    /** 刚进页面、选中第一张时不该立刻续页（否则一打开就狂拉）。 */
    @Test
    public void firstItemDoesNotTriggerALoad() {
        assertFalse(MainActivity.shouldLoadMore(true, 0, 24));
    }

    /**
     * 结果页要把焦点交给结果网格。
     *
     * 遥控器场景下焦点默认留在左侧导航栏，用户按一次 OK 就被带回首页 ——
     * 表现就是「点了剧集却返回了，并没有去到详情页」。
     */
    @Test
    public void theResultsPageHandsFocusToTheGrid() {
        assertTrue(MainActivity.shouldFocusGrid(true, true, false));
    }

    /** 焦点已经在网格里就别抢：用户翻到第几张就被拽回第一张，比不交还糟。 */
    @Test
    public void focusIsNotStolenFromTheGrid() {
        assertFalse(MainActivity.shouldFocusGrid(true, true, true));
    }

    /** 还没有结果时没什么可聚焦的。 */
    @Test
    public void noFocusWithoutResults() {
        assertFalse(MainActivity.shouldFocusGrid(true, false, false));
    }

    /** 手机端是手指点哪儿就是哪儿，不动焦点。 */
    @Test
    public void thePhoneKeepsItsOwnFocus() {
        assertFalse(MainActivity.shouldFocusGrid(false, true, false));
    }
}
