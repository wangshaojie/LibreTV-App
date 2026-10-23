package com.wangshaojie.libretv;
import android.content.*;
import org.json.*;
import java.util.*;
import java.io.*;

public final class Store {
    private final SharedPreferences prefs;
    public Store(Context context){prefs=context.getSharedPreferences("libretv",Context.MODE_PRIVATE);if(!prefs.contains("sources"))try(InputStream in=context.getAssets().open("default-sources.json")){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);put("sources",new JSONArray(out.toString("UTF-8")));}catch(Exception ignored){put("sources",new JSONArray());}}
    public JSONArray array(String name){try{return new JSONArray(prefs.getString(name,"[]"));}catch(Exception e){return new JSONArray();}}
    public void put(String name,JSONArray data){prefs.edit().putString(name,data.toString()).apply();}
    public long position(String url){return prefs.getLong("position:"+url,0);}
    public void remember(Catalog.Item item,long position){
        prefs.edit().putLong("position:"+item.url,item.live?0:position).apply();JSONArray next=new JSONArray();next.put(item.json());JSONArray previous=array("history");for(int n=0;n<previous.length()&&next.length()<60;n++){JSONObject j=previous.optJSONObject(n);if(j!=null&&!j.optString("url").equals(item.url))next.put(j);}put("history",next);
    }
    public boolean favorite(Catalog.Item item){JSONArray a=array("favorites");for(int n=0;n<a.length();n++)if(a.optJSONObject(n)!=null&&a.optJSONObject(n).optString("url").equals(item.url))return true;return false;}
    public void toggleFavorite(Catalog.Item item){JSONArray a=array("favorites"),next=new JSONArray();boolean found=false;for(int n=0;n<a.length();n++){JSONObject j=a.optJSONObject(n);if(j==null)continue;if(j.optString("url").equals(item.url))found=true;else next.put(j);}if(!found)next.put(item.json());put("favorites",next);}
    public List<Catalog.Item> items(String key){List<Catalog.Item> out=new ArrayList<>();JSONArray a=array(key);for(int n=0;n<a.length();n++)if(a.optJSONObject(n)!=null)out.add(Catalog.Item.from(a.optJSONObject(n)));return out;}
}
