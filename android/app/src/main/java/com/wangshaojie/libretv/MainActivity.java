package com.wangshaojie.libretv;

import android.app.*;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.io.*;

@androidx.media3.common.util.UnstableApi
public final class MainActivity extends Activity {
    private Store store;
    private LinearLayout content;
    private TextView status;
    private ListView list;
    private EditText query;
    private Spinner sourcePicker,groupPicker;
    private final ExecutorService workers=Executors.newFixedThreadPool(5);
    private final List<Catalog.Item> items=new ArrayList<>(),shown=new ArrayList<>();
    private List<JSONObject> liveSources=new ArrayList<>();
    private int generation=0;
    private String page="live",group="全部分组";
    private boolean initializing;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);store=new Store(this);if(Ui.tv(this))setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        LinearLayout root=Ui.column(this);root.setBackgroundColor(Ui.BG);root.setFitsSystemWindows(true);int pad=Ui.dp(this,Ui.tv(this)?24:10);root.setPadding(pad,pad,pad,pad);
        TextView brand=Ui.text(this,getString(R.string.app_name)+"  ·  手机 / Android TV",Ui.tv(this)?25:21);root.addView(brand);
        HorizontalScrollView navScroll=new HorizontalScrollView(this);navScroll.setHorizontalScrollBarEnabled(false);LinearLayout nav=new LinearLayout(this);
        nav.addView(Ui.button(this,"直播",()->live()));nav.addView(Ui.button(this,"点播",()->vod()));nav.addView(Ui.button(this,"收藏",()->saved("favorites")));
        nav.addView(Ui.button(this,"记录",()->saved("history")));nav.addView(Ui.button(this,"源管理",()->sources()));nav.addView(Ui.button(this,"关于",()->about()));navScroll.addView(nav);root.addView(navScroll);
        content=Ui.column(this);root.addView(content,new LinearLayout.LayoutParams(-1,0,1));status=Ui.text(this,"",14);status.setTextColor(Ui.MUTED);root.addView(status);setContentView(root);live();
    }
    private boolean current(int token){return generation==token&&!isFinishing()&&!isDestroyed();}
    private void reset(String next){generation++;page=next;content.removeAllViews();items.clear();shown.clear();list=null;query=null;group="全部分组";}
    private void searchBox(String hint,Runnable action){
        LinearLayout row=new LinearLayout(this);query=Ui.input(this,hint);query.setImeOptions(EditorInfo.IME_ACTION_SEARCH);row.addView(query,new LinearLayout.LayoutParams(0,-2,1));row.addView(Ui.button(this,"搜索",action));content.addView(row);
        query.setOnEditorActionListener((v,id,event)->{if(id==EditorInfo.IME_ACTION_SEARCH){action.run();return true;}return false;});
    }
    private void createList(){
        list=new ListView(this);list.setDividerHeight(Ui.dp(this,5));list.setChoiceMode(ListView.CHOICE_MODE_SINGLE);list.setFocusable(true);
        list.setAdapter(new android.widget.BaseAdapter(){
            public int getCount(){return shown.size();}public Object getItem(int p){return shown.get(p);}public long getItemId(int p){return p;}
            public View getView(int p,View old,android.view.ViewGroup parent){
                TextView text=old instanceof TextView?(TextView)old:Ui.text(MainActivity.this,"",Ui.tv(MainActivity.this)?20:17);
                Catalog.Item item=shown.get(p);String second=item.live?item.group:item.sourceName;
                text.setText((store.favorite(item)?"★ ":"")+item.title+(second.isEmpty()?"":"\n"+second));text.setMinHeight(Ui.dp(MainActivity.this,62));text.setBackground(Ui.focus());return text;
            }});
        list.setOnItemClickListener((parent,v,p,id)->open(shown.get(p)));
        list.setOnItemLongClickListener((parent,v,p,id)->{itemMenu(shown.get(p));return true;});
        content.addView(list,new LinearLayout.LayoutParams(-1,0,1));
    }
    private void refresh(){if(list!=null)((BaseAdapter)list.getAdapter()).notifyDataSetChanged();}
    private void filter(){
        shown.clear();String q=query==null?"":query.getText().toString().trim().toLowerCase(Locale.ROOT);
        for(Catalog.Item item:items)if((q.isEmpty()||item.title.toLowerCase(Locale.ROOT).contains(q))&&(group.equals("全部分组")||group.equals(item.group)))shown.add(item);
        refresh();status.setText("共 "+shown.size()+" 项 · 长按条目或按遥控器菜单键可收藏");
    }
    private List<JSONObject> sourcesOf(String type){List<JSONObject> out=new ArrayList<>();JSONArray array=store.array("sources");for(int n=0;n<array.length();n++){JSONObject s=array.optJSONObject(n);if(s!=null&&type.equals(s.optString("type")))out.add(s);}return out;}
    private void live(){
        reset("live");liveSources=sourcesOf("live");searchBox("搜索当前列表的频道或节目",this::filter);
        sourcePicker=new Spinner(this);List<String> names=new ArrayList<>();for(JSONObject s:liveSources)names.add(s.optString("name"));if(names.isEmpty())names.add("请先添加直播 M3U 源");
        sourcePicker.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));content.addView(sourcePicker);
        groupPicker=new Spinner(this);content.addView(groupPicker);createList();
        sourcePicker.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){} public void onItemSelected(AdapterView<?> p,View v,int at,long id){if(at<liveSources.size())loadLive(liveSources.get(at));}});
        if(liveSources.isEmpty())status.setText("源管理 → 添加 → 直播 M3U，支持手机和遥控器操作");
    }
    private void loadLive(JSONObject source){
        final int token=++generation;items.clear();shown.clear();refresh();status.setText("正在读取 "+source.optString("name")+"…");
        workers.execute(()->{try{
            List<Catalog.Item> channels=Catalog.m3u(Net.get(source.optString("url")),source.optString("url"));if(channels.isEmpty())throw new IOException("列表没有 HTTP/HTTPS 节目，请检查是否为 M3U 地址");
            for(Catalog.Item i:channels){i.sourceName=source.optString("name");i.source=source.optString("url");}android.util.Log.i("LibreTV-Net","Subscription loaded: "+channels.size()+" entries");
            runOnUiThread(()->{if(!current(token))return;items.addAll(channels);LinkedHashSet<String> gs=new LinkedHashSet<>();gs.add("全部分组");for(Catalog.Item i:items)if(!i.group.isEmpty())gs.add(i.group);
                List<String> groups=new ArrayList<>(gs);group="全部分组";groupPicker.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,groups));groupPicker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int n,long id){group=groups.get(n);filter();}});filter();});
        }catch(Exception e){runOnUiThread(()->{if(current(token))status.setText("订阅读取失败："+error(e)+" · 可在源管理中修改或测试");});}});
    }
    private void vod(){reset("vod");searchBox("输入影片名称，搜索已配置点播源",this::searchVod);createList();int count=sourcesOf("vod").size();status.setText(count==0?"请先在源管理中添加点播源":"输入片名后点击搜索 · 已配置 "+count+" 个点播源");}
    private void searchVod(){
        String term=query.getText().toString().trim();if(term.isEmpty()){Ui.toast(this,"请输入片名");return;}List<JSONObject> sources=sourcesOf("vod");if(sources.isEmpty()){Ui.message(this,"请先在源管理添加 Apple CMS 点播源");return;}
        final int token=++generation;items.clear();shown.clear();refresh();status.setText("正在搜索 "+sources.size()+" 个源…");AtomicInteger done=new AtomicInteger(),failed=new AtomicInteger();
        for(JSONObject source:sources)workers.execute(()->{List<Catalog.Item> found=new ArrayList<>();String failure="";try{found=Catalog.search(Net.get(cmsUrl(source.optString("url"),"videolist","wd",term)),source.optString("url"),source.optString("name"));}catch(Exception e){failed.incrementAndGet();failure=error(e);}final List<Catalog.Item> results=found;final String issue=failure;
            runOnUiThread(()->{if(!current(token))return;items.addAll(results);shown.clear();shown.addAll(items);refresh();int completed=done.incrementAndGet();status.setText("已完成 "+completed+"/"+sources.size()+" 个源 · "+items.size()+" 个结果 · "+failed.get()+" 个源失败"+(items.isEmpty()&&!issue.isEmpty()?"："+issue:""));});});
    }
    static String cmsUrl(String base,String action,String key,String value){
        Uri uri=Uri.parse(base);Uri.Builder builder=uri.buildUpon().clearQuery();for(String name:uri.getQueryParameterNames())if(!Arrays.asList("ac","wd","ids","pg").contains(name))for(String v:uri.getQueryParameters(name))builder.appendQueryParameter(name,v);
        builder.appendQueryParameter("ac",action);if(key!=null)builder.appendQueryParameter(key,value);return builder.build().toString();
    }
    private void saved(String key){reset(key);items.addAll(store.items(key));createList();shown.addAll(items);refresh();status.setText((key.equals("history")?"最近观看，点播自动续播":"我的收藏")+" · "+items.size()+" 项");}
    private void open(Catalog.Item item){
        if(!item.url.isEmpty()){play(Collections.singletonList(item),0);return;}
        final int token=++generation;status.setText("正在获取剧集…");workers.execute(()->{try{LinkedHashMap<String,List<Catalog.Item>> routes=Catalog.episodes(Net.get(cmsUrl(item.source,"videolist","ids",item.vodId)));runOnUiThread(()->{if(!current(token))return;String[] names=routes.keySet().toArray(new String[0]);new AlertDialog.Builder(this).setTitle(item.title+" · 选择线路").setItems(names,(d,which)->episodeList(item.title,routes.get(names[which]))).setNegativeButton("取消",null).show();});}catch(Exception e){runOnUiThread(()->{if(current(token))status.setText("详情失败："+error(e));});}});
    }
    private void episodeList(String title,List<Catalog.Item> episodes){String[] labels=new String[episodes.size()];for(int n=0;n<labels.length;n++)labels[n]=episodes.get(n).title;new AlertDialog.Builder(this).setTitle(title+" · 选集").setItems(labels,(d,p)->play(episodes,p)).setNegativeButton("返回",null).show();}
    private void play(List<Catalog.Item> media,int at){JSONArray json=new JSONArray();for(Catalog.Item i:media)json.put(i.json());startActivity(new Intent(this,PlayerActivity.class).putExtra("items",json.toString()).putExtra("index",at));}
    private void itemMenu(Catalog.Item item){if(item.url.isEmpty()){open(item);return;}new AlertDialog.Builder(this).setTitle(item.title).setItems(new String[]{"播放",store.favorite(item)?"取消收藏":"加入收藏"},(d,n)->{if(n==0)open(item);else{store.toggleFavorite(item);Ui.toast(this,"收藏已更新");if(page.equals("favorites"))saved("favorites");else refresh();}}).show();}
    @Override public boolean onKeyDown(int key,KeyEvent e){if(key==KeyEvent.KEYCODE_MENU&&list!=null&&list.getSelectedItemPosition()>=0&&list.getSelectedItemPosition()<shown.size()){itemMenu(shown.get(list.getSelectedItemPosition()));return true;}return super.onKeyDown(key,e);}
    private void sources(){
        JSONArray a=store.array("sources");String[] names=new String[a.length()];for(int n=0;n<a.length();n++){JSONObject s=a.optJSONObject(n);names[n]=(s.optString("type").equals("live")?"[直播] ":"[点播] ")+s.optString("name");}
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("源管理 · "+a.length()+" 个源").setItems(names,(d,n)->sourceOptions(n)).setPositiveButton("添加",(d,n)->editSource(-1)).setNeutralButton("导入 / 导出",(d,n)->transfer()).setNegativeButton("关闭",null).create();dialog.show();
    }
    private void sourceOptions(int at){JSONObject source=store.array("sources").optJSONObject(at);if(source==null)return;new AlertDialog.Builder(this).setTitle(source.optString("name")).setItems(new String[]{"测试可用性","编辑","删除"},(d,n)->{if(n==0)testSource(source);else if(n==1)editSource(at);else new AlertDialog.Builder(this).setMessage("删除这个源？").setPositiveButton("删除",(x,y)->{JSONArray a=store.array("sources"),next=new JSONArray();for(int k=0;k<a.length();k++)if(k!=at)next.put(a.opt(k));store.put("sources",next);live();}).setNegativeButton("取消",null).show();}).show();}
    private void editSource(int at){
        JSONArray array=store.array("sources");JSONObject old=at>=0?array.optJSONObject(at):null;
        LinearLayout fields=Ui.column(this);int p=Ui.dp(this,18);fields.setPadding(p,p,p,p);EditText name=Ui.input(this,"源名称"),url=Ui.input(this,"https://…（M3U 列表或 CMS API）");url.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);
        Spinner type=new Spinner(this);type.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"直播 M3U / M3U8","点播 Apple CMS JSON"}));fields.addView(type);fields.addView(name);fields.addView(url);
        if(old!=null){name.setText(old.optString("name"));url.setText(old.optString("url"));type.setSelection(old.optString("type").equals("vod")?1:0);}
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(at<0?"添加源":"编辑源").setView(fields).setPositiveButton("保存",null).setNegativeButton("取消",null).create();dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{String address=Catalog.httpUrl(url.getText().toString(),null);if(address==null){url.setError("请输入有效的 HTTP/HTTPS 地址");return;}try{JSONObject item=new JSONObject().put("name",name.getText().toString().trim().isEmpty()?"自定义源":name.getText().toString().trim()).put("url",address).put("type",type.getSelectedItemPosition()==0?"live":"vod");if(at>=0)array.put(at,item);else array.put(item);store.put("sources",array);dialog.dismiss();live();}catch(Exception e){Ui.message(this,error(e));}}));dialog.show();
    }
    private void testSource(JSONObject source){
        Ui.toast(this,"正在测试，请稍等…");workers.execute(()->{String message;try{long started=System.currentTimeMillis();int count;if(source.optString("type").equals("live"))count=Catalog.m3u(Net.get(source.optString("url")),source.optString("url")).size();else count=Catalog.search(Net.get(cmsUrl(source.optString("url"),"videolist",null,null)),source.optString("url"),source.optString("name")).size();if(source.optString("type").equals("live")&&count==0)throw new IOException("未找到可用的 HTTP/HTTPS 节目");message="可用 · "+count+" 项 · "+(System.currentTimeMillis()-started)+" ms\n列表可访问不代表每个节目均可播放。";}catch(Exception e){message="测试失败："+error(e);}final String result=message;runOnUiThread(()->{if(!isFinishing())Ui.message(this,result);});});
    }
    private void transfer(){new AlertDialog.Builder(this).setTitle("源配置迁移").setItems(new String[]{"导入 JSON 文件","导出配置、收藏和记录"},(d,n)->{Intent intent=n==0?new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE):new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"LibreTV-App-settings.json");startActivityForResult(intent,n==0?21:22);}).show();}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;try{
        if(request==22){JSONObject json=new JSONObject().put("version",1).put("sources",store.array("sources")).put("favorites",store.array("favorites")).put("history",store.array("history"));try(OutputStream out=getContentResolver().openOutputStream(data.getData())){out.write(json.toString(2).getBytes("UTF-8"));}Ui.toast(this,"已导出配置");}
        else if(request==21){ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=getContentResolver().openInputStream(data.getData())){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(out.size()+n>2*1024*1024)throw new IOException("配置超过 2 MB");out.write(b,0,n);}}JSONObject j=new JSONObject(out.toString("UTF-8"));JSONArray imported=new JSONArray();appendSources(imported,j.optJSONArray("sources"),"vod");appendSources(imported,j.optJSONArray("liveSources"),"live");JSONObject settings=j.optJSONObject("settings");if(settings!=null){appendSources(imported,settings.optJSONArray("customAPIs"),"vod");appendSources(imported,settings.optJSONArray("liveSubscriptions"),"live");}if(imported.length()==0)throw new IOException("配置中没有有效的源");new AlertDialog.Builder(this).setMessage("导入 "+imported.length()+" 个源？将替换当前源配置。").setPositiveButton("导入",(d,n)->{store.put("sources",imported);if(j.optJSONArray("favorites")!=null)store.put("favorites",j.optJSONArray("favorites"));if(j.optJSONArray("history")!=null)store.put("history",j.optJSONArray("history"));live();}).setNegativeButton("取消",null).show();}
    }catch(Exception e){Ui.message(this,"操作失败："+error(e));}}
    private void appendSources(JSONArray out,JSONArray sources,String fallback)throws JSONException{if(sources==null)return;for(int n=0;n<sources.length();n++){JSONObject s=sources.optJSONObject(n);if(s==null||Catalog.httpUrl(s.optString("url"),null)==null)continue;String type=s.optString("type",fallback);out.put(new JSONObject().put("name",s.optString("name","导入的源")).put("url",s.optString("url")).put("type",type.equals("live")?"live":"vod"));}}
    private void about(){new AlertDialog.Builder(this).setTitle(getString(R.string.app_name)+" "+BuildConfig.VERSION_NAME).setMessage("非官方 LibreTV 客户端\n手机触屏 / Android TV 遥控器通用\n"+(BuildConfig.LEGACY_TLS?"兼容版：最低 Android 5.0，推荐旧系统使用":"新版：最低 Android 8.0")+"\n\n支持 HTTP/HTTPS M3U、Apple CMS JSON 和原生 HLS/MP4 播放。解码能力取决于设备，不支持网页解析及 Spider 引擎。\n\n上游：LibreSpark/LibreTV\n许可：AGPL-3.0-or-later\n原生 Android 端：2026-10-23\n本软件按原样提供，不作担保。源码和许可可在仓库查看。")
        .setPositiveButton("源码与许可",(d,n)->{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/wangshaojie/LibreTV-App")));}catch(ActivityNotFoundException e){Ui.message(this,"此设备未安装浏览器，请用手机或电脑访问：\nhttps://github.com/wangshaojie/LibreTV-App");}}).setNegativeButton("关闭",null).show();}
    private String error(Exception e){return e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}
    @Override protected void onResume(){super.onResume();if(store!=null&&(page.equals("favorites")||page.equals("history")))saved(page);}
    @Override protected void onDestroy(){generation++;workers.shutdownNow();super.onDestroy();}
}
