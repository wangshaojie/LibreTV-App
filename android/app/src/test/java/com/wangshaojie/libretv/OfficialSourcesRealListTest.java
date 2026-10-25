package com.wangshaojie.libretv;
import org.json.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

/** 用真实清单内容验证解析与合并。 */
public class OfficialSourcesRealListTest {
    private static final String REAL="{\n  \"name\": \"LibreTV-SourceList\",\n  \"version\": 2,\n  \"exportedAt\": 1792761576893,\n  \"sources\": [\n    {\n      \"name\": \"暴风资源\",\n      \"url\": \"https://bfzyapi.com/api.php/provide/vod\"\n    },\n    {\n      \"name\": \"极速资源\",\n      \"url\": \"https://jszyapi.com/api.php/provide/vod/at/json\"\n    },\n    {\n      \"name\": \"量子\",\n      \"url\": \"https://cj.lziapi.com/api.php/provide/vod\"\n    },\n    {\n      \"name\": \"无尽\",\n      \"url\": \"https://api.wujinapi.me/api.php/provide/vod\"\n    },\n    {\n      \"name\": \"非凡资源\",\n      \"url\": \"https://cj.ffzyapi.com/api.php/provide/vod\"\n    },\n    {\n      \"name\": \"优酷资源\",\n      \"url\": \"https://api.ukuapi88.com/api.php/provide/vod\"\n    },\n    {\n      \"name\": \"光速资源\",\n      \"url\": \"https://api.guangsuapi.com/api.php/provide/vod\"\n    }\n  ],\n  \"liveSources\": [\n    {\n      \"name\": \"直播源\",\n      \"url\": \"https://iptv-org.github.io/iptv/countries/cn.m3u\"\n    },\n    {\n      \"name\": \"直播源\",\n      \"url\": \"https://raw.githubusercontent.com/YanG-1989/m3u/main/Gather.m3u\"\n    },\n    {\n      \"name\": \"电影\",\n      \"url\": \"https://raw.githubusercontent.com/onlinem3u/publicdomainm3u/main/movies.m3u\"\n    },\n    {\n      \"name\": \"剧集\",\n      \"url\": \"https://raw.githubusercontent.com/onlinem3u/publicdomainm3u/main/series.m3u\"\n    },\n    {\n      \"name\": \"vbskycn IPv4\",\n      \"url\": \"https://live.zbds.top/tv/iptv4.m3u\"\n    },\n    {\n      \"name\": \"范明明源镜像\",\n      \"url\": \"https://m3u.ibert.me/fmml_ipv6.m3u\"\n    },\n    {\n      \"name\": \"iptv-org 中国大陆\",\n      \"url\": \"https://raw.githubusercontent.com/iptv-org/iptv/master/streams/cn.m3u\"\n    },\n    {\n      \"name\": \"中文频道\",\n      \"url\": \"https://iptv-org.github.io/iptv/languages/zho.m3u\"\n    },\n    {\n      \"name\": \"aptv 综合\",\n      \"url\": \"https://raw.githubusercontent.com/Kimentanm/aptv/master/m3u/iptv.m3u\"\n    }\n  ]\n}\n";
    @Test public void parsesRealPublishedList()throws Exception {
        List<JSONObject> list=OfficialSources.parse(REAL);
        assertEquals("真实清单应有 7 个点播源",7,count(list,"vod"));
        assertEquals("真实清单应有 9 个直播源",9,count(list,"live"));
        for(JSONObject s:list)assertTrue("地址必须是 http(s): "+s.optString("url"),s.optString("url").startsWith("https://"));
    }
    @Test public void mergesRealListIntoEmptyLocal()throws Exception {
        JSONArray local=new JSONArray();
        OfficialSources.Result r=OfficialSources.merge(local,OfficialSources.parse(REAL),new HashSet<String>());
        assertEquals(16,r.added);
        assertEquals(16,local.length());
    }
    @Test public void mergingTwiceIsIdempotent()throws Exception {
        JSONArray local=new JSONArray();
        OfficialSources.merge(local,OfficialSources.parse(REAL),new HashSet<String>());
        OfficialSources.Result second=OfficialSources.merge(local,OfficialSources.parse(REAL),new HashSet<String>());
        assertEquals("重复同步不应新增源",0,second.added);
        assertEquals(16,local.length());
    }
    private static int count(List<JSONObject> list,String type){int n=0;for(JSONObject s:list)if(type.equals(s.optString("type")))n++;return n;}
}