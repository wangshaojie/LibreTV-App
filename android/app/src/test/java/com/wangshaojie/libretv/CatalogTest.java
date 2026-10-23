package com.wangshaojie.libretv;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public class CatalogTest {
    @Test public void parsesGroupsRelativeUrlsAndDeduplicates(){List<Catalog.Item> list=Catalog.m3u("\uFEFF#EXTM3U\n#EXTINF:-1 group-title=\"央视\",CCTV-1\n/live/1.m3u8\n#EXTINF:-1,重复\n/live/1.m3u8\n#EXTINF:-1,不支持\nudp://239.1.1.1\n","https://example.com/list.m3u");assertEquals(1,list.size());assertEquals("CCTV-1",list.get(0).title);assertEquals("央视",list.get(0).group);assertEquals("https://example.com/live/1.m3u8",list.get(0).url);}
    @Test public void honorsPlaylistHeaders(){List<Catalog.Item> list=Catalog.m3u("#EXTINF:-1,Test\n#EXTVLCOPT:http-referrer=https://example.com/\nhttps://cdn.example.com/a.m3u8|User-Agent=Test%20Agent\n","https://example.com/list.m3u");assertEquals("https://example.com/",list.get(0).headers.get("Referer"));assertEquals("Test Agent",list.get(0).headers.get("User-Agent"));}
    @Test public void standaloneHlsIsNotTreatedAsChannels(){List<Catalog.Item> list=Catalog.m3u("#EXTM3U\n#EXT-X-TARGETDURATION:10\n#EXTINF:10,\nseg.ts","https://example.com/live.m3u8");assertEquals(1,list.size());assertEquals("https://example.com/live.m3u8",list.get(0).url);}
    @Test public void rejectsUnsupportedSchemes(){assertNull(Catalog.httpUrl("file:///etc/passwd",null));assertNull(Catalog.httpUrl("javascript:hello",null));assertEquals("http://192.168.1.2/live",Catalog.httpUrl("http://192.168.1.2/live",null));}
    @Test public void cmsSupportsEmptyAndNormalLists()throws Exception {assertEquals(0,Catalog.search("{\"list\":null}","https://example.com/api","源").size());assertEquals("123",Catalog.search("{\"list\":[{\"vod_id\":123,\"vod_name\":\"电影\"}]}","https://example.com/api","源").get(0).vodId);}
    @Test(expected=org.json.JSONException.class)public void rejectsWrongCmsResponse()throws Exception {Catalog.search("{\"error\":\"not cms\"}","https://example.com","源");}
    @Test public void splitsEpisodeRoutesAndNames()throws Exception {Map<String,List<Catalog.Item>> groups=Catalog.episodes("{\"list\":[{\"vod_name\":\"节目\",\"vod_play_from\":\"HLS$$$备用\",\"vod_play_url\":\"第一集$https://a.example/1.m3u8#第二集$https://a.example/2.m3u8$$$正片$https://b.example/a.mp4\"}]}");assertEquals(2,groups.size());assertEquals(2,groups.get("HLS").size());assertEquals("节目 · 第一集",groups.get("HLS").get(0).title);assertEquals("https://b.example/a.mp4",groups.get("备用").get(0).url);}
    @Test public void itemRoundTripKeepsHeaders(){Catalog.Item i=new Catalog.Item();i.title="频道";i.url="https://example.com/a.m3u8";i.live=true;i.headers.put("Referer","https://example.com/");Catalog.Item j=Catalog.Item.from(i.json());assertTrue(j.live);assertEquals(i.title,j.title);assertEquals(i.headers,j.headers);}
}
