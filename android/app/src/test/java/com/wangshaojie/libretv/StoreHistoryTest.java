package com.wangshaojie.libretv;

import android.content.SharedPreferences;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 观看记录 / 搜索记录的删除逻辑。
 *
 * 这些都是纯数据操作，用内存版 SharedPreferences 就能在 JVM 上跑完，
 * 不需要真机或 Robolectric。
 */
public class StoreHistoryTest {

    /** 内存版 SharedPreferences：够用即可，只实现读写与编辑器。 */
    static final class MemPrefs implements SharedPreferences {
        final Map<String, Object> map = new HashMap<>();

        @Override public Map<String, ?> getAll() { return new HashMap<>(map); }
        @Override public String getString(String key, String def) { Object v = map.get(key); return v instanceof String ? (String) v : def; }
        @Override public Set<String> getStringSet(String key, Set<String> def) { Object v = map.get(key); return v instanceof Set ? (Set<String>) v : def; }
        @Override public int getInt(String key, int def) { Object v = map.get(key); return v instanceof Integer ? (Integer) v : def; }
        @Override public long getLong(String key, long def) { Object v = map.get(key); return v instanceof Long ? (Long) v : def; }
        @Override public float getFloat(String key, float def) { Object v = map.get(key); return v instanceof Float ? (Float) v : def; }
        @Override public boolean getBoolean(String key, boolean def) { Object v = map.get(key); return v instanceof Boolean ? (Boolean) v : def; }
        @Override public boolean contains(String key) { return map.containsKey(key); }
        @Override public Editor edit() { return new MemEditor(); }
        @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) { }
        @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) { }

        final class MemEditor implements Editor {
            final Map<String, Object> pending = new HashMap<>();
            final Set<String> removed = new HashSet<>();
            boolean clearAll = false;

            @Override public Editor putString(String key, String value) { pending.put(key, value); removed.remove(key); return this; }
            @Override public Editor putStringSet(String key, Set<String> value) { pending.put(key, new HashSet<>(value)); removed.remove(key); return this; }
            @Override public Editor putInt(String key, int value) { pending.put(key, value); removed.remove(key); return this; }
            @Override public Editor putLong(String key, long value) { pending.put(key, value); removed.remove(key); return this; }
            @Override public Editor putFloat(String key, float value) { pending.put(key, value); removed.remove(key); return this; }
            @Override public Editor putBoolean(String key, boolean value) { pending.put(key, value); removed.remove(key); return this; }
            @Override public Editor remove(String key) { pending.remove(key); removed.add(key); return this; }
            @Override public Editor clear() { clearAll = true; pending.clear(); removed.clear(); return this; }
            @Override public boolean commit() { apply(); return true; }
            @Override public void apply() {
                if (clearAll) map.clear();
                for (String key : removed) map.remove(key);
                map.putAll(pending);
            }
        }
    }

    private static Catalog.Item item(String title, String url) {
        Catalog.Item i = new Catalog.Item();
        i.title = title;
        i.url = url;
        return i;
    }

    @Test public void removeHistoryDropsOnlyThatRecord() {
        Store store = new Store(new MemPrefs());
        store.remember(item("甲", "https://a.example/1.m3u8"), 100);
        store.remember(item("乙", "https://b.example/2.m3u8"), 200);

        assertTrue(store.removeHistory("https://a.example/1.m3u8"));

        List<Catalog.Item> left = store.items("history");
        assertEquals(1, left.size());
        assertEquals("乙", left.get(0).title);
        // 其余记录的续播进度必须还在
        assertEquals(200, store.position("https://b.example/2.m3u8"));
    }

    @Test public void removeHistoryAlsoClearsItsProgress() {
        Store store = new Store(new MemPrefs());
        store.remember(item("甲", "https://a.example/1.m3u8"), 120);

        assertTrue(store.removeHistory("https://a.example/1.m3u8"));

        // 记录删了，进度也必须一起没 —— 否则从别的入口重开同一部片子会接着跳
        assertEquals(0, store.position("https://a.example/1.m3u8"));
        assertEquals(0, store.items("history").size());
    }

    @Test public void removeUnknownHistoryChangesNothing() {
        Store store = new Store(new MemPrefs());
        store.remember(item("甲", "https://a.example/1.m3u8"), 100);

        assertFalse(store.removeHistory("https://zzz.example/none.m3u8"));

        assertEquals(1, store.items("history").size());
        assertEquals(100, store.position("https://a.example/1.m3u8"));
    }

    @Test public void clearHistoryRemovesEverythingAndAllProgress() {
        Store store = new Store(new MemPrefs());
        store.remember(item("甲", "https://a.example/1.m3u8"), 100);
        store.remember(item("乙", "https://b.example/2.m3u8"), 200);

        int count = store.clearHistory();

        assertEquals(2, count);
        assertEquals(0, store.items("history").size());
        assertEquals(0, store.position("https://a.example/1.m3u8"));
        assertEquals(0, store.position("https://b.example/2.m3u8"));
    }

    @Test public void clearHistoryOnEmptyListIsHarmless() {
        Store store = new Store(new MemPrefs());
        assertEquals(0, store.clearHistory());
        assertEquals(0, store.items("history").size());
    }

    @Test public void removeSearchHistoryKeepsOtherTermsInOrder() {
        Store store = new Store(new MemPrefs());
        store.searchHistory("无声");
        store.searchHistory("海岸");
        store.searchHistory("沙丘");

        assertTrue(store.removeSearchHistory("海岸"));

        // 搜索历史是最新在前：删掉中间的「海岸」后，前后两条的相对顺序不能变
        List<String> terms = new ArrayList<>();
        for (int n = 0; n < store.array("searches").length(); n++) terms.add(store.array("searches").optString(n));
        assertEquals(2, terms.size());
        assertEquals("沙丘", terms.get(0));
        assertEquals("无声", terms.get(1));
    }

    @Test public void removeUnknownSearchHistoryChangesNothing() {
        Store store = new Store(new MemPrefs());
        store.searchHistory("无声");
        assertFalse(store.removeSearchHistory("不存在"));
        assertEquals(1, store.array("searches").length());
    }

    @Test public void clearSearchHistoryEmptiesTheList() {
        Store store = new Store(new MemPrefs());
        store.searchHistory("无声");
        store.searchHistory("海岸");

        store.clearSearchHistory();

        assertEquals(0, store.array("searches").length());
    }

    @Test public void searchHistoryKeepsMostRecentFirst() {
        Store store = new Store(new MemPrefs());
        store.searchHistory("旧");
        store.searchHistory("新");
        assertEquals("新", store.array("searches").optString(0));
        assertEquals("旧", store.array("searches").optString(1));
    }
}
