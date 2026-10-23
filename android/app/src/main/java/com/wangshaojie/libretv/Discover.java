package com.wangshaojie.libretv;

import android.net.Uri;
import java.util.*;
import java.io.IOException;
import org.json.*;

@androidx.media3.common.util.UnstableApi
final class Discover {
    static String key(String type,String tag){return "discover:"+type+":"+tag;}
    static List<Catalog.Item> load(Store store,String type,String tag,int offset)throws Exception{
        List<Catalog.Item> cached=store.cached(key(type,tag));if(offset==0&&!cached.isEmpty()&&System.currentTimeMillis()-store.object(key(type,tag)).optLong("time")<10*60*1000)return cached;
        Map<String,String> headers=new HashMap<>();headers.put("User-Agent",Net.BROWSER_UA);headers.put("Referer","https://movie.douban.com/");headers.put("Accept","application/json");
        if(!store.object("preferences").optString("discover","douban").equals("cms"))try{List<Catalog.Item> result=Catalog.recommendations(Net.get("https://movie.douban.com/j/search_subjects?type="+type+"&tag="+Uri.encode(tag)+"&sort=recommend&page_limit=24&page_start="+offset,headers),type);if(!result.isEmpty()){if(offset==0)store.cache(key(type,tag),result);return result;}}catch(Exception e){if(Thread.currentThread().isInterrupted())throw e;}
        if(offset>0)return Collections.emptyList();
        JSONArray sources=store.array("sources");List<Catalog.Item> fallback=new ArrayList<>();Set<String> seen=new HashSet<>();int tried=0;
        for(int n=0;n<sources.length()&&tried<3&&fallback.size()<16;n++){
            JSONObject s=sources.optJSONObject(n);if(s==null||!s.optString("type").equals("vod")||!s.optBoolean("enabled",true))continue;tried++;
            try{List<Catalog.Item> list=Catalog.search(Net.get(MainActivity.cmsUrl(s.optString("url"),"videolist",null,null)),s.optString("url"),s.optString("name"));
                for(Catalog.Item item:list){boolean tv=item.category.contains("剧")||item.remarks.contains("集");if(type.equals("tv")!=tv)continue;if(seen.add(Catalog.titleKey(item.title)))fallback.add(item);}
            }catch(Exception e){if(Thread.currentThread().isInterrupted())throw e;}
        }
        if(fallback.isEmpty()){if(!cached.isEmpty())return cached;throw new IOException("推荐暂未加载成功，可重试或直接搜索片名");}store.cache(key(type,tag),fallback);return fallback;
    }
}
