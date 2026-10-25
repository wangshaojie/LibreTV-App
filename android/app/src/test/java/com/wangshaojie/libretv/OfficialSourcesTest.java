package com.wangshaojie.libretv;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

/**
 * 官方片源清单的解析与合并。
 * 语义对齐网页端 src/lib/source-list.ts 的 parseSourceListPayload：
 * 类型只由所在分组决定（sources=点播、liveSources=直播），点播去尾部斜杠、直播保留。
 */
public class OfficialSourcesTest {
    private static final String LIST="{\"name\":\"静观 TV\",\"version\":2,\"sources\":[{\"name\":\"点播一\",\"url\":\"https://a.example/api.php/provide/vod\"}],\"liveSources\":[{\"name\":\"直播一\",\"url\":\"https://b.example/list.m3u\"}]}";

    @Test public void parsesBothSourceKinds()throws Exception {List<JSONObject> list=OfficialSources.parse(LIST);assertEquals(2,list.size());assertEquals("vod",list.get(0).optString("type"));assertEquals("点播一",list.get(0).optString("name"));assertEquals("live",list.get(1).optString("type"));assertEquals("https://b.example/list.m3u",list.get(1).optString("url"));}

    @Test public void typeComesFromGroupNotAddress()throws Exception {List<JSONObject> list=OfficialSources.parse("{\"sources\":[{\"name\":\"A\",\"url\":\"https://a.example/list.m3u\"}],\"liveSources\":[{\"name\":\"B\",\"url\":\"https://b.example/api.php/provide/vod\"}]}");assertEquals("vod",list.get(0).optString("type"));assertEquals("live",list.get(1).optString("type"));}

    @Test public void acceptsBareArrayAsVodList()throws Exception {List<JSONObject> list=OfficialSources.parse("[{\"name\":\"A\",\"url\":\"https://a.example/api\"}]");assertEquals(1,list.size());assertEquals("vod",list.get(0).optString("type"));}

    @Test public void skipsInvalidEntries()throws Exception {List<JSONObject> list=OfficialSources.parse("{\"sources\":[{\"name\":\"坏\",\"url\":\"file:///etc/passwd\"},{\"name\":\"好\",\"url\":\"https://a.example/api\"}]}");assertEquals(1,list.size());assertEquals("好",list.get(0).optString("name"));}

    @Test public void dedupesWithinGroup()throws Exception {List<JSONObject> list=OfficialSources.parse("{\"sources\":[{\"name\":\"一\",\"url\":\"https://a.example/api\"},{\"name\":\"二\",\"url\":\"https://a.example/api/\"}]}");assertEquals(1,list.size());}

    @Test public void vodTrimsTrailingSlashLiveKeepsIt()throws Exception {List<JSONObject> list=OfficialSources.parse("{\"sources\":[{\"name\":\"V\",\"url\":\"https://a.example/api/\"}],\"liveSources\":[{\"name\":\"L\",\"url\":\"https://b.example/list.m3u/\"}]}");assertEquals("https://a.example/api",list.get(0).optString("url"));assertEquals("https://b.example/list.m3u/",list.get(1).optString("url"));}

    @Test(expected=Exception.class)public void rejectsEmptyList()throws Exception {OfficialSources.parse("{\"sources\":[]}");}

    @Test(expected=Exception.class)public void rejectsMalformedJson()throws Exception {OfficialSources.parse("not json at all");}

    @Test public void addsNewOfficialSources()throws Exception {JSONArray local=new JSONArray();OfficialSources.Result r=OfficialSources.merge(local,OfficialSources.parse(LIST),new HashSet<String>());assertEquals(2,r.added);assertEquals(2,local.length());assertTrue(local.getJSONObject(0).optBoolean("official",false));}

    @Test public void keepsUserDisabledState()throws Exception {JSONArray local=new JSONArray();JSONObject s=new JSONObject();s.put("type","vod");s.put("name","旧名");s.put("url","https://a.example/api.php/provide/vod");s.put("enabled",false);local.put(s);
        OfficialSources.Result r=OfficialSources.merge(local,OfficialSources.parse(LIST),new HashSet<String>());assertEquals(1,r.added);assertEquals(2,local.length());
        JSONObject merged=local.getJSONObject(0);assertFalse("用户手动停用的源不应被重新启用",merged.optBoolean("enabled",true));assertEquals("点播一",merged.optString("name"));}

    @Test public void keepsUserAddedSources()throws Exception {JSONArray local=new JSONArray();JSONObject mine=new JSONObject();mine.put("type","vod");mine.put("name","我的源");mine.put("url","https://mine.example/api");local.put(mine);
        OfficialSources.merge(local,OfficialSources.parse(LIST),new HashSet<String>());assertEquals(3,local.length());boolean kept=false;for(int n=0;n<local.length();n++)if("https://mine.example/api".equals(local.getJSONObject(n).optString("url")))kept=true;assertTrue("用户自己添加的源不应被移除",kept);}

    @Test public void doesNotReAddDismissedSources()throws Exception {JSONArray local=new JSONArray();Set<String> dismissed=new HashSet<>();dismissed.add("vod:https://a.example/api.php/provide/vod");
        OfficialSources.Result r=OfficialSources.merge(local,OfficialSources.parse(LIST),dismissed);assertEquals(1,r.added);assertEquals(1,local.length());assertEquals("https://b.example/list.m3u",local.getJSONObject(0).optString("url"));}

    @Test public void normalizesTrailingSlashForVod()throws Exception {JSONArray local=new JSONArray();JSONObject s=new JSONObject();s.put("type","vod");s.put("name","旧名");s.put("url","https://a.example/api.php/provide/vod/");local.put(s);
        OfficialSources.Result r=OfficialSources.merge(local,OfficialSources.parse(LIST),new HashSet<String>());assertEquals(1,r.added);assertEquals(2,local.length());}

    @Test public void sameAddressAsVodAndLiveStaysSeparate()throws Exception {String body="{\"sources\":[{\"name\":\"V\",\"url\":\"https://same.example/api\"}],\"liveSources\":[{\"name\":\"L\",\"url\":\"https://same.example/api\"}]}";
        JSONArray local=new JSONArray();OfficialSources.Result r=OfficialSources.merge(local,OfficialSources.parse(body),new HashSet<String>());assertEquals(2,r.added);}

    @Test public void resyncIntervalIsEnforced() {
        long now=1_700_000_000_000L;
        assertTrue("从未同步过应立即拉取",OfficialSources.due(0,now));
        assertFalse("刚同步过不应重复拉取",OfficialSources.due(now,now));
        assertFalse(OfficialSources.due(now-OfficialSources.RESYNC_INTERVAL+60_000,now));
        assertTrue(OfficialSources.due(now-OfficialSources.RESYNC_INTERVAL,now));
    }
}
