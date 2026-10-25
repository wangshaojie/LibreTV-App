package com.wangshaojie.libretv;
import org.json.*;
import java.util.*;

/**
 * 官方片源清单：远程拉取 + 与本地源合并。
 *
 * 清单地址写死在本类，只有仓库维护者能改；用户无法改写该地址。
 * 清单格式为 LibreTV-SourceList JSON：{ name?, version?, sources[], liveSources[] }，
 * 也接受裸数组（此时全部视为点播源）。
 *
 * 合并语义与网页端一致（见 src/lib/store.ts 的 applySubscriptionSources）：
 * - 清单中有的源 → 按 URL 匹配本地源；已存在则只更新名称等元信息，
 *   保留用户的手动停用状态（enabled=false 不被重新勾上）；
 * - 本地有而清单没有的源 → 保留不动（那是用户自己加的源）；
 * - 用户手动删过的官方源 → 不再自动加回（用 dismissed 记录）。
 */
public final class OfficialSources {
    /**
     * 官方清单地址：仅仓库维护者可改（sources/latest.json）。
     *
     * 第一个是主地址，后面是同一份文件的镜像。主地址在部分网络（尤其是没有代理、
     * 直连受限的电视）下拉不到，那时依次尝试镜像；全都失败就沿用本地已有配置。
     * 顺序即优先级，任何一个能通即可完成同步。
     */
    public static final String[] URLS={
        "https://raw.githubusercontent.com/wangshaojie/LibreTV-App/main/sources/latest.json",
        "https://fastly.jsdelivr.net/gh/wangshaojie/LibreTV-App@main/sources/latest.json",
        "https://gcore.jsdelivr.net/gh/wangshaojie/LibreTV-App@main/sources/latest.json",
        "https://cdn.jsdelivr.net/gh/wangshaojie/LibreTV-App@main/sources/latest.json",
        "https://ghproxy.net/https://raw.githubusercontent.com/wangshaojie/LibreTV-App/main/sources/latest.json"
    };
    /** 主地址，供需要单一地址的地方使用。 */
    public static final String URL=URLS[0];
    /** 距上次成功同步超过该时长才在启动时再拉一次（6 小时）。 */
    public static final long RESYNC_INTERVAL=6*60*60*1000L;
    /** 本地记录上次成功同步时间的键。 */
    public static final String SYNCED_AT="official-sources-synced-at";
    /** 本地记录用户已删除的官方源地址的键。 */
    public static final String DISMISSED="official-sources-dismissed";

    private OfficialSources() {}

    /** 归一化地址：去掉尾部斜杠，避免同一源因写法不同被当成两个。 */
    public static String key(String url){return url==null?"":url.trim().replaceAll("/+$","");}

    /**
     * 解析清单文本为源条目列表。
     * 返回的每个条目含 type（vod/live）、name、url；非法条目被跳过。
     */
    /**
     * 解析清单文本为源条目列表。
     * 返回的每个条目含 type（vod/live）、name、url；非法条目被跳过。
     *
     * 语义与网页端一致（src/lib/source-list.ts 的 parseSourceListPayload）：
     * - 类型只由所在分组决定：sources 是点播、liveSources 是直播，不按地址猜；
     * - 点播源去尾部斜杠、直播源保留尾部斜杠（直播列表地址的尾斜杠可能有意义）；
     * - 同组内地址重复的条目只取第一条。
     */
    public static List<JSONObject> parse(String text)throws Exception {
        List<JSONObject> out=new ArrayList<>();
        if(text==null||text.trim().isEmpty())throw new Exception("清单内容为空");
        String body=text.trim();
        if(body.startsWith("\uFEFF"))body=body.substring(1);
        JSONArray vodList=null,liveList=null;
        try {
            if(body.startsWith("[")){vodList=new JSONArray(body);}
            else {
                JSONObject root=new JSONObject(body);
                vodList=root.optJSONArray("sources");
                liveList=root.optJSONArray("liveSources");
            }
        }catch(JSONException e){throw new Exception("清单格式不正确");}
        collect(out,vodList,"vod",true);
        collect(out,liveList,"live",false);
        if(out.isEmpty())throw new Exception("清单里没有可用源");
        return out;
    }

    /**
     * 依次尝试各个清单地址，返回第一个能拉到的内容。
     *
     * 主地址在部分网络（尤其是没有代理、直连受限的电视）下可能拉不到，
     * 那就换下一个镜像；全都失败时抛出异常，本地已配置的源保持不变。
     */
    public static String fetch()throws Exception {
        Exception first=null;
        for(String url:URLS){
            try{return Net.get(url);}
            catch(Exception e){if(first==null)first=e;}
        }
        String reason=first==null||first.getMessage()==null?"未知原因":first.getMessage();
        throw new Exception("已尝试 "+URLS.length+" 个地址仍拉不到："+reason);
    }

    /** 收拢一个分组里的条目；分组决定类型，组内地址重复只取第一条。 */    private static void collect(List<JSONObject> out,JSONArray list,String group,boolean trimSlash) throws Exception {
        if(list==null)return;
        Set<String> seen=new HashSet<>();
        for(int n=0;n<list.length();n++){
            JSONObject entry=list.optJSONObject(n);
            if(entry==null)continue;
            String raw=entry.optString("url").trim();
            if(raw.isEmpty()||Catalog.httpUrl(raw,null)==null)continue;
            String url=trimSlash?key(raw):raw;
            if(!seen.add(url))continue;
            JSONObject source=new JSONObject();
            source.put("type",group);
            source.put("name",entry.optString("name",group.equals("live")?"直播源":"采集站"));
            source.put("url",url);
            out.add(source);
        }
    }

    /** 合并结果统计。 */
    public static class Result {
        /** 新增的官方源数量。 */
        public int added;
        /** 已在本地、仅更新了元信息的源数量。 */
        public int updated;
        /** 清单中被丢弃的非法条目数量。 */
        public int skipped;
        /** 本地源总数。 */
        public int total;
    }

    /**
     * 把清单合并进本地源列表并写回。
     *
     * local     本地现有源（会被就地修改）
     * incoming  清单解析出的源
     * dismissed 用户已删除的官方源地址（不会被加回）
     */
    public static Result merge(JSONArray local,List<JSONObject> incoming,Set<String> dismissed) throws Exception {
        Result result=new Result();
        Map<String,JSONObject> byUrl=new LinkedHashMap<>();
        for(int n=0;n<local.length();n++){
            JSONObject s=local.optJSONObject(n);
            if(s!=null)byUrl.put(typeKey(s.optString("type"),s.optString("url")),s);
        }
        for(JSONObject source:incoming){
            String type=source.optString("type");
            String url=source.optString("url");
            if(dismissed.contains(typeKey(type,url)))continue;
            String tk=typeKey(type,url);
            JSONObject existing=byUrl.get(tk);
            if(existing==null){
                // 新增的官方源默认启用；用户手动加的同名源也算已存在，不会重复添加
                JSONObject added=source;
                added.put("enabled",true);
                added.put("official",true);
                local.put(added);
                byUrl.put(tk,added);
                result.added++;
            }else{
                // 已存在：只补齐名称等元信息，保留用户的 enabled 开关
                String name=source.optString("name");
                if(!name.isEmpty()&&!name.equals(existing.optString("name"))){
                    existing.put("name",name);
                    result.updated++;
                }
                // 标记为官方源：用户删除它时不再被自动加回
                existing.put("official",true);
            }
        }
        result.total=local.length();
        return result;
    }

    /**
     * 源的身份：类型 + 地址。同一个地址既当点播又当直播时视为两个源。
     * 归一化规则与网页端一致：点播去尾部斜杠，直播保留。
     */
    private static String typeKey(String type,String url){
        String value=url==null?"":url.trim();
        if("live".equals(type))return type+":"+value;
        return type+":"+value.replaceAll("/+$","");
    }

    /** 读出用户已删除的官方源标识集合（元素为 typeKey 形式）。 */
    public static Set<String> readDismissed(Store store){
        Set<String> out=new HashSet<>();
        JSONArray list=store.array(DISMISSED);
        for(int n=0;n<list.length();n++){
            String value=list.optString(n);
            if(!value.isEmpty())out.add(value);
        }
        return out;
    }

    /** 记录一个被用户删除的官方源，之后同步不再自动加回。 */
    public static void dismiss(Store store,String type,String url){
        String identifier=typeKey(type,url);
        Set<String> set=readDismissed(store);
        if(!set.add(identifier))return;
        JSONArray list=new JSONArray();
        for(String value:set)list.put(value);
        store.put(DISMISSED,list);
    }

    /** 距离上次成功同步是否已超过间隔（从未同步过返回 true）。 */
    public static boolean due(Store store,long now){
        return due(store.object(SYNCED_AT).optLong("at",0),now);
    }

    /** 间隔判断的纯逻辑部分：last 为上次成功同步时间戳（0 表示从未同步过）。 */
    public static boolean due(long last,long now){
        return last<=0||now-last>=RESYNC_INTERVAL;
    }

    /** 记录一次成功同步。 */
    public static void markSynced(Store store,long now){
        try{store.putObject(SYNCED_AT,new JSONObject().put("at",now));}catch(Exception ignored){}
    }
}
