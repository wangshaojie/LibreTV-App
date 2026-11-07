package com.wangshaojie.libretv;
import android.content.*;
import org.json.*;
import java.util.*;
import java.io.*;

public final class Store {
    private final SharedPreferences prefs;
    public Store(Context context){
        this(context.getSharedPreferences("libretv",Context.MODE_PRIVATE),context);
    }
    /**
     * 直接给定 SharedPreferences 的构造入口。
     *
     * 只给单元测试用：单测跑在 JVM 上，没有真实的 Android Context，
     * 传一个内存实现就能验证删除/清空这类纯数据逻辑。
     * 测试路径没有 assets，preset 迁移因此跳过 —— 它只影响内置源清单，与记录无关。
     */
    Store(SharedPreferences prefs){
        this(prefs,null);
    }
    private Store(SharedPreferences prefs,Context context){
        this.prefs=prefs;
        // context 为 null 表示走的是单测路径：没有 assets，跳过内置源迁移
        if(context==null)return;
        try {
            if(!prefs.contains("sources"))prefs.edit().putString("sources",asset(context,"default-sources.json").toString()).putBoolean("presets-1.0.1",true).apply();
            else if(!prefs.getBoolean("presets-1.0.1",false)){
                JSONArray current=array("sources"),added=asset(context,"new-sources-1.0.1.json");
                Set<String> seen=new HashSet<>();for(int n=0;n<current.length();n++){JSONObject s=current.optJSONObject(n);if(s!=null)seen.add(sourceKey(s));}
                for(int n=0;n<added.length();n++){JSONObject s=added.getJSONObject(n);if(seen.add(sourceKey(s)))current.put(s);}
                prefs.edit().putString("sources",current.toString()).putBoolean("presets-1.0.1",true).apply();
            }
        }catch(Exception ignored){/* Leave the migration unmarked so a later initialization can retry. */}
    }
    private static String sourceKey(JSONObject s){return s.optString("type")+":"+s.optString("url").replaceAll("/+$","");}
    private static JSONArray asset(Context context,String name)throws Exception{try(InputStream in=context.getAssets().open(name)){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return new JSONArray(out.toString("UTF-8"));}}
    public JSONArray array(String name){try{return new JSONArray(prefs.getString(name,"[]"));}catch(Exception e){return new JSONArray();}}
    public void put(String name,JSONArray data){prefs.edit().putString(name,data.toString()).apply();}
    public JSONObject object(String name){try{return new JSONObject(prefs.getString(name,"{}"));}catch(Exception e){return new JSONObject();}}
    public void putObject(String name,JSONObject data){prefs.edit().putString(name,data.toString()).apply();}
    public void remove(String name){prefs.edit().remove(name).apply();}
    public void cache(String key,List<Catalog.Item> items){JSONArray a=new JSONArray();for(Catalog.Item i:items)a.put(i.json());try{putObject(key,new JSONObject().put("time",System.currentTimeMillis()).put("items",a));}catch(Exception ignored){}}
    public List<Catalog.Item> cached(String key){List<Catalog.Item> out=new ArrayList<>();JSONArray a=object(key).optJSONArray("items");if(a!=null)for(int n=0;n<a.length();n++)if(a.optJSONObject(n)!=null)out.add(Catalog.Item.from(a.optJSONObject(n)));return out;}
    public void searchHistory(String term){JSONArray next=new JSONArray().put(term),old=array("searches");for(int n=0;n<old.length()&&next.length()<12;n++)if(!term.equals(old.optString(n)))next.put(old.optString(n));put("searches",next);}
    /** 删掉一条搜索历史：命中的那一条移除，其余保持原顺序 */
    public boolean removeSearchHistory(String term){JSONArray old=array("searches"),next=new JSONArray();boolean removed=false;for(int n=0;n<old.length();n++){String value=old.optString(n);if(!removed&&value.equals(term)){removed=true;continue;}next.put(value);}if(removed)put("searches",next);return removed;}
    /** 清空搜索历史 */
    public void clearSearchHistory(){put("searches",new JSONArray());}
    public static String itemKey(Catalog.Item item){return item.url.isEmpty()?item.source+":"+item.vodId:item.url;}
    public long position(String url){return prefs.getLong("position:"+url,0);}
    public JSONObject exportPositions(){JSONObject out=new JSONObject();for(Catalog.Item item:items("history"))try{if(!item.url.isEmpty())out.put(item.url,position(item.url));}catch(Exception ignored){}return out;}
    public void importPositions(JSONObject data){if(data==null)return;SharedPreferences.Editor editor=prefs.edit();for(Iterator<String> keys=data.keys();keys.hasNext();){String url=keys.next();if(Catalog.httpUrl(url,null)!=null)editor.putLong("position:"+url,Math.max(0,data.optLong(url)));}editor.apply();}
    public void remember(Catalog.Item item,long position){
        prefs.edit().putLong("position:"+item.url,item.live?0:position).apply();JSONArray next=new JSONArray();next.put(item.json());JSONArray previous=array("history");for(int n=0;n<previous.length()&&next.length()<60;n++){JSONObject j=previous.optJSONObject(n);if(j!=null&&!j.optString("url").equals(item.url))next.put(j);}put("history",next);
    }
    public boolean favorite(Catalog.Item item){JSONArray a=array("favorites");for(int n=0;n<a.length();n++)if(a.optJSONObject(n)!=null&&itemKey(Catalog.Item.from(a.optJSONObject(n))).equals(itemKey(item)))return true;return false;}
    public void toggleFavorite(Catalog.Item item){JSONArray a=array("favorites"),next=new JSONArray();boolean found=false;for(int n=0;n<a.length();n++){JSONObject j=a.optJSONObject(n);if(j==null)continue;if(itemKey(Catalog.Item.from(j)).equals(itemKey(item)))found=true;else next.put(j);}if(!found)next.put(item.json());put("favorites",next);}
    /**
     * 删除一条观看记录。
     *
     * 记录与它的续播进度是一体的：只删记录不清进度的话，
     * 从别的入口重新打开同一部片子会接着上次的进度跳，用户会以为「记录没删掉」。
     * 所以这里连带把 position: 键一起清掉。
     */
    public boolean removeHistory(String url){JSONArray old=array("history"),next=new JSONArray();boolean removed=false;for(int n=0;n<old.length();n++){JSONObject j=old.optJSONObject(n);if(j==null)continue;if(!removed&&j.optString("url").equals(url)){removed=true;continue;}next.put(j);}if(removed){put("history",next);prefs.edit().remove("position:"+url).apply();}return removed;}
    /** 清空全部观看记录（连带清掉这些记录的续播进度） */
    public int clearHistory(){JSONArray old=array("history");SharedPreferences.Editor editor=prefs.edit();for(int n=0;n<old.length();n++){JSONObject j=old.optJSONObject(n);if(j!=null&&!j.optString("url").isEmpty())editor.remove("position:"+j.optString("url"));}editor.putString("history","[]").apply();return old.length();}
    public List<Catalog.Item> items(String key){List<Catalog.Item> out=new ArrayList<>();JSONArray a=array(key);for(int n=0;n<a.length();n++)if(a.optJSONObject(n)!=null)out.add(Catalog.Item.from(a.optJSONObject(n)));return out;}
}
