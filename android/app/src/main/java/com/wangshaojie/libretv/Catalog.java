package com.wangshaojie.libretv;

import org.json.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;

/** Portable parsers: shared phone/TV data model, no Android framework dependencies. */
public final class Catalog {
    private Catalog() {}
    public static final class Item {
        public String title="", url="", group="", source="", sourceName="", vodId="",poster="",summary="",rating="",year="",remarks="",category="";
        public boolean live;
        public final Map<String,String> headers = new LinkedHashMap<>();
        public JSONObject json() {
            JSONObject j=new JSONObject();
            try { j.put("title",title).put("url",url).put("group",group).put("source",source).put("sourceName",sourceName).put("vodId",vodId).put("live",live).put("headers",new JSONObject(headers)).put("poster",poster).put("summary",summary).put("rating",rating).put("year",year).put("remarks",remarks).put("category",category); } catch(JSONException e) { throw new IllegalStateException(e); }
            return j;
        }
        public static Item from(JSONObject j) {
            Item i=new Item(); i.title=j.optString("title"); i.url=j.optString("url"); i.group=j.optString("group");
            i.source=j.optString("source");i.sourceName=j.optString("sourceName");i.vodId=j.optString("vodId");i.live=j.optBoolean("live");
            i.poster=j.optString("poster");i.summary=j.optString("summary");i.rating=j.optString("rating");i.year=j.optString("year");i.remarks=j.optString("remarks");i.category=j.optString("category");
            JSONObject h=j.optJSONObject("headers");if(h!=null)for(Iterator<String> k=h.keys();k.hasNext();){String key=k.next();i.headers.put(key,h.optString(key));}return i;
        }
    }
    public static String httpUrl(String raw,String base) {
        try { URI u=base==null?new URI(raw.trim()):new URI(base).resolve(raw.trim());
            String s=u.getScheme();return ("http".equalsIgnoreCase(s)||"https".equalsIgnoreCase(s))&&u.getHost()!=null?u.toASCIIString():null;
        }catch(Exception e){return null;}
    }
    public static List<Item> m3u(String text,String base) {
        List<Item> out=new ArrayList<>(); Set<String> seen=new HashSet<>();
        if(text.contains("#EXT-X-TARGETDURATION")||text.contains("#EXT-X-STREAM-INF")){Item i=new Item();i.title="直接播放";i.url=base;i.live=true;out.add(i);return out;}
        String name="",group="";Map<String,String> headers=new LinkedHashMap<>();
        Pattern attrs=Pattern.compile("([\\w-]+)=\"([^\"]*)\"");
        for(String raw:text.replace("\uFEFF","").split("\\r?\\n")) {
            String line=raw.trim(); if(line.isEmpty())continue;
            if(line.startsWith("#EXTINF")) {
                headers=new LinkedHashMap<>();group="";Matcher m=attrs.matcher(line);
                while(m.find())if(m.group(1).equalsIgnoreCase("group-title"))group=m.group(2);
                String clean=attrs.matcher(line).replaceAll("");int comma=clean.indexOf(',');name=comma>=0?clean.substring(comma+1).trim():"";
            }else if(line.startsWith("#EXTGRP:")){group=line.substring(8).trim();}
            else if(line.startsWith("#EXTVLCOPT:")) {
                int eq=line.indexOf('=');if(eq>0){String key=line.substring(11,eq).toLowerCase(Locale.ROOT);if(key.equals("http-referrer")||key.equals("http-referer"))headers.put("Referer",line.substring(eq+1));if(key.equals("http-user-agent"))headers.put("User-Agent",line.substring(eq+1));}
            }else if(!line.startsWith("#")) {
                int pipe=line.indexOf('|');String address=pipe>=0?line.substring(0,pipe):line;
                if(pipe>=0)for(String pair:line.substring(pipe+1).split("&")){int eq=pair.indexOf('=');if(eq>0)try{String key=URLDecoder.decode(pair.substring(0,eq),"UTF-8");if(key.equalsIgnoreCase("referer"))headers.put("Referer",URLDecoder.decode(pair.substring(eq+1),"UTF-8"));if(key.equalsIgnoreCase("user-agent"))headers.put("User-Agent",URLDecoder.decode(pair.substring(eq+1),"UTF-8"));}catch(Exception ignored){}}
                String url=httpUrl(address,base);if(url!=null&&seen.add(url)){Item i=new Item();i.title=name.isEmpty()?"频道 "+(out.size()+1):name;i.url=url;i.group=group;i.live=true;i.headers.putAll(headers);out.add(i);}
                name="";group="";headers=new LinkedHashMap<>();
            }
        }return out;
    }
    public static List<Item> search(String json,String url,String name)throws JSONException {
        JSONObject data=new JSONObject(json);JSONArray list=data.optJSONArray("list");List<Item> out=new ArrayList<>();
        if(list==null){if(!data.has("list"))throw new JSONException("不是 Apple CMS JSON 接口");return out;}
        for(int n=0;n<list.length();n++){JSONObject v=list.optJSONObject(n);if(v==null)continue;Item i=new Item();i.title=v.optString("vod_name");i.vodId=v.optString("vod_id");i.group=v.optString("vod_remarks");i.source=url;i.sourceName=name;i.poster=v.optString("vod_pic");i.summary=v.optString("vod_content");i.rating=v.optString("vod_douban_score");i.year=v.optString("vod_year");i.remarks=v.optString("vod_remarks");i.category=v.optString("type_name");out.add(i);}return out;
    }
    public static List<Item> recommendations(String json,String type)throws JSONException{
        JSONArray subjects=new JSONObject(json).getJSONArray("subjects");List<Item> out=new ArrayList<>();
        for(int n=0;n<subjects.length();n++){JSONObject s=subjects.getJSONObject(n);Item i=new Item();i.title=s.optString("title");i.poster=s.optString("cover");i.rating=s.optString("rate");i.category=type.equals("tv")?"剧集":"电影";if(!i.title.isEmpty())out.add(i);}return out;
    }
    public static String titleKey(String title){return title.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s·•]","");}
    public static LinkedHashMap<String,List<Item>> episodes(String json)throws JSONException {
        JSONObject data=new JSONObject(json);JSONArray list=data.optJSONArray("list");if(list==null||list.length()==0)throw new JSONException("未获取到影片详情");
        JSONObject v=list.getJSONObject(0);String[] groups=v.optString("vod_play_url").split("\\$\\$\\$");String[] names=v.optString("vod_play_from").split("\\$\\$\\$");
        LinkedHashMap<String,List<Item>> result=new LinkedHashMap<>();
        for(int g=0;g<groups.length;g++){List<Item> items=new ArrayList<>();for(String ep:groups[g].split("#")){int split=ep.indexOf('$');String u=httpUrl(split>=0?ep.substring(split+1):ep,null);if(u==null)continue;Item i=new Item();i.title=v.optString("vod_name")+" · "+(split>=0?ep.substring(0,split):"正片");i.url=u;i.poster=v.optString("vod_pic");i.summary=v.optString("vod_content");i.year=v.optString("vod_year");items.add(i);if(items.size()==500)break;}if(!items.isEmpty())result.put((g<names.length&&!names[g].isEmpty()?names[g]:"线路 "+(g+1)),items);}
        if(result.isEmpty())throw new JSONException("此源没有可直接播放的地址（不支持网页解析线路）");
        // CMS providers often put their HTML player before the actual media route.
        // Keep extensionless streams as fallbacks, but never select them ahead of known media.
        LinkedHashMap<String,List<Item>> ordered=new LinkedHashMap<>();
        for(Map.Entry<String,List<Item>> route:result.entrySet())if(directRoute(route.getValue()))ordered.put(route.getKey(),route.getValue());
        for(Map.Entry<String,List<Item>> route:result.entrySet())if(!ordered.containsKey(route.getKey()))ordered.put(route.getKey(),route.getValue());
        return ordered;
    }
    static boolean directRoute(List<Item> episodes){if(episodes.isEmpty())return false;try{String path=new URI(episodes.get(0).url).getPath().toLowerCase(Locale.ROOT);return path.matches(".*\\.(m3u8|mpd|mp4|m4v|mkv|webm|ts|flv|mov|avi|mp3|aac)$");}catch(Exception e){return false;}}
}
