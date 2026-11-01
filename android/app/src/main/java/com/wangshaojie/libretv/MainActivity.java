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
    private TextView status,emptyTitle,emptyHint;
    private AbsListView list;
    private boolean gridPressed;
    private EditText query;
    private LinearLayout sourceRow,groupRow;
    private int liveSourceIndex=0;
    private final ExecutorService workers=Executors.newFixedThreadPool(5);
    private final List<Catalog.Item> items=new ArrayList<>(),shown=new ArrayList<>();
    private List<JSONObject> liveSources=new ArrayList<>();
    private int generation=0;
    private String page="live",group="全部分组";
    private Button backButton;
    /** 顶栏：一级页面整条隐藏，二级页面才出现（返回按钮 + 页面名） */
    private LinearLayout headerView;
    private TextView pageTitle;
    private final Map<String,Button> navigation=new LinkedHashMap<>();
    private final List<Future<?>> tasks=new ArrayList<>();
    private final Map<String,List<Catalog.Item>> grouped=new LinkedHashMap<>();
    private JSONObject channelHealth=new JSONObject();
    private boolean onlyAvailable;
    private String catalogType="movie",catalogTag="热门";
    private int catalogOffset,restoreLiveIndex=-1;
    private String restoreGroup="";

    // ---- 返回栈与位置保持 ----
    /**
     * 返回栈：只存一级页面（首页 / 点播 / 直播 / 我的）。
     * 二级页面（发现片单 / 收藏 / 观看记录 / 源管理）进入时压栈，
     * 返回时弹栈回到原来那一页，而不是一律回首页。
     */
    private final ArrayDeque<String> backStack=new ArrayDeque<>();
    /** 每个页面的视觉快照：滚动位置、搜索词、焦点落在哪张卡片上 */
    private final Map<String,Snapshot> snapshots=new HashMap<>();
    /** 当前页的主滚动容器（首页 / 我的页） */
    private ScrollView mainScroll;
    /** section 标题与容器的关系（原来用 setTag 存的，和滚动快照的 tag 冲突） */
    private final Map<LinearLayout,TextView> sectionTitles=new IdentityHashMap<>();
    /** 从返回栈恢复页面时抑制再次压栈 */
    private boolean restoring;
    /** 初始焦点是否已经放到左侧导航（只做一次，避免从详情页返回时抢走恢复好的焦点） */
    private boolean focusInitialized;

    private static final class Snapshot {
        int scrollY,listPosition,listTop;
        String term,focusKey;
        final Map<String,Integer> hScroll=new HashMap<>();
    }

    @Override public void onCreate(Bundle b){
        super.onCreate(b);store=new Store(this);if(Ui.tv(this))setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        boolean tv=Ui.tv(this);LinearLayout root=new LinearLayout(this);root.setOrientation(tv?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL);Ui.inset(this,root);
        // 侧边栏：白卡片浮在暖白页面底上，留出边距并加轻投影，层次才立得住
        LinearLayout navWrap=tv?Ui.column(this):Ui.row(this);
        LinearLayout nav=tv?Ui.column(this):Ui.row(this);
        nav.setBackground(Ui.shape(Ui.SURFACE,Ui.LINE,20));Ui.elevate(nav,2f);Ui.padding(nav,tv?16:6);
        if(tv){
            LinearLayout brand=Ui.row(this);
            TextView mark=Ui.label(this,"静",19,Ui.ON_ACCENT);mark.setGravity(Gravity.CENTER);mark.setBackground(Ui.circle(Ui.ACCENT));
            brand.addView(mark,new LinearLayout.LayoutParams(Ui.dp(this,38),Ui.dp(this,38)));
            TextView word=Ui.label(this,"静观 TV",17,Ui.TEXT);word.setTypeface(null,android.graphics.Typeface.BOLD);word.setPadding(Ui.dp(this,10),0,0,0);
            brand.addView(word);
            nav.addView(brand);nav.addView(Ui.gap(this,6));nav.addView(Ui.label(this,"发现你的下一部好片",11,Ui.MUTED));nav.addView(Ui.gap(this,22));
        }
        String[] names={"首页","点播","直播","我的"};String[] keys={"home","vod","live","my"};Runnable[] actions={this::home,this::vod,this::live,this::mine};
        for(int n=0;n<names.length;n++){Button button=Ui.navigation(this,names[n],keys[n],actions[n]);navigation.put(keys[n],button);if(tv){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,Ui.dp(this,52));lp.setMargins(0,0,0,Ui.dp(this,10));nav.addView(button,lp);}else nav.addView(button,new LinearLayout.LayoutParams(0,Ui.dp(this,62),1));}
        if(tv){nav.addView(new View(this),new LinearLayout.LayoutParams(1,0,1));nav.addView(Ui.label(this,"静观 TV · "+BuildConfig.VERSION_NAME,11,Ui.FAINT));}
        if(tv){LinearLayout.LayoutParams wrapLp=new LinearLayout.LayoutParams(Ui.dp(this,178),-1);wrapLp.setMargins(Ui.dp(this,16),Ui.dp(this,16),0,Ui.dp(this,16));navWrap.addView(nav,new LinearLayout.LayoutParams(-1,-1));root.addView(navWrap,wrapLp);}
        else navWrap.addView(nav,new LinearLayout.LayoutParams(-1,-1));
        LinearLayout main=Ui.column(this);main.setPadding(Ui.dp(this,tv?28:16),Ui.dp(this,tv?16:6),Ui.dp(this,tv?28:16),0);LinearLayout header=Ui.row(this);
        headerView=header;
        // 二级页面在顶栏左侧给出返回入口：电视遥控器之外的路径也要能回上一级
        backButton=Ui.icon(this,"back","返回上一页",this::goBack);backButton.setVisibility(View.GONE);header.addView(backButton);
        pageTitle=Ui.heading(this,"");pageTitle.setGravity(Gravity.CENTER_VERTICAL);header.addView(pageTitle,new LinearLayout.LayoutParams(0,Ui.dp(this,52),1));main.addView(header);
        content=Ui.column(this);main.addView(content,new LinearLayout.LayoutParams(-1,0,1));status=Ui.label(this,"",12,Ui.MUTED);status.setMaxLines(2);status.setPadding(0,Ui.dp(this,6),0,Ui.dp(this,8));main.addView(status);
        root.addView(main,tv?new LinearLayout.LayoutParams(0,-1,1):new LinearLayout.LayoutParams(-1,0,1));
        // 手机端导航在底部，电视端在左侧
        if(!tv)root.addView(navWrap,new LinearLayout.LayoutParams(-1,Ui.dp(this,72)));setContentView(root);channelHealth=store.object("channel-health");restorePage(b);
        if(!tv){root.setFocusableInTouchMode(true);root.requestFocus();}
        syncOfficialSources(false);
        // 上一次启动异常退出的话，把崩溃现场摆出来：
        // 手机端往往连不上 adb，拿不到 logcat，用户光说「闪退了」没法定位，
        // 让他截图这一屏就够了。
        String crash=LibreTvApplication.pendingCrash(this);
        if(!crash.isEmpty())root.post(()->{
            try{
                LinearLayout box=Ui.column(this);
                box.addView(Ui.label(this,"上次启动时应用异常退出，以下是崩溃现场：",14,Ui.TEXT));
                box.addView(Ui.gap(this,10));
                box.addView(Ui.label(this,crash,11,Ui.MUTED));
                UiDialog.custom(this,"异常记录",box,null,null);
            }catch(Exception ignored){}
        });
    }
    private boolean current(int token){return generation==token&&!isFinishing()&&!isDestroyed();}

    // ================= 页面栈与快照 =================

    /** 记录当前页面的视觉状态：滚动位置、搜索词、焦点位置 */
    private void saveSnapshot(){
        if(page==null||page.isEmpty())return;
        Snapshot s=snapshots.get(page);if(s==null){s=new Snapshot();snapshots.put(page,s);}
        s.scrollY=mainScroll==null?0:mainScroll.getScrollY();
        s.listPosition=list==null?-1:list.getFirstVisiblePosition();
        s.listTop=list==null?0:list.getTop();
        s.term=query==null?"":query.getText().toString();
        s.focusKey=focusedKey();
        s.hScroll.clear();
        collectScroll(content,s.hScroll);
    }
    /** 重建页面后把视觉状态还原回去 —— 用户返回时看到的还是刚才那一屏 */
    private void restoreSnapshot(){
        Snapshot s=snapshots.get(page);if(s==null)return;
        if(query!=null&&s.term!=null&&!s.term.isEmpty()&&query.getText().length()==0)query.setText(s.term);
        if(mainScroll!=null&&s.scrollY>0)mainScroll.post(()->mainScroll.scrollTo(0,s.scrollY));
        if(list!=null&&s.listPosition>=0)list.post(()->list.setSelectionFromTop(s.listPosition,s.listTop));
        for(Map.Entry<String,Integer> e:s.hScroll.entrySet()){
            int x=e.getValue();if(x<=0)continue;
            View target=findByTag(content,e.getKey());
            if(target instanceof HorizontalScrollView)((HorizontalScrollView)target).post(()->((HorizontalScrollView)target).scrollTo(x,0));
        }
        if(s.focusKey!=null&&s.focusKey.length()>0)content.post(()->{View v=findByDesc(content,s.focusKey);if(v!=null)v.requestFocus();});
    }
    private String focusedKey(){
        View f=getWindow().getDecorView().findFocus();
        if(f==null||f.getContentDescription()==null)return null;
        String d=f.getContentDescription().toString();
        return d.length()==0?null:d;
    }
    private void collectScroll(View v,Map<String,Integer> out){
        if(v instanceof HorizontalScrollView&&v.getTag() instanceof String){Object t=v.getTag();out.put((String)t,((HorizontalScrollView)v).getScrollX());}
        if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int n=0;n<g.getChildCount();n++)collectScroll(g.getChildAt(n),out);}
    }
    private View findByTag(View v,String tag){
        if(tag.equals(v.getTag()))return v;
        if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int n=0;n<g.getChildCount();n++){View r=findByTag(g.getChildAt(n),tag);if(r!=null)return r;}}
        return null;
    }
    private View findByDesc(View v,String desc){
        if(desc.equals(v.getContentDescription())&&v.isFocusable())return v;
        if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int n=0;n<g.getChildCount();n++){View r=findByDesc(g.getChildAt(n),desc);if(r!=null)return r;}}
        return null;
    }
    /** 二级页面才压栈：一级页面之间切换是平移，不是「进入下一层」 */
    private void pushBack(){if(restoring)return;if(page==null||page.isEmpty())return;if(!isPrimary(page))return;backStack.push(page);}
    private static boolean isPrimary(String key){return key.equals("home")||key.equals("vod")||key.equals("live")||key.equals("my");}
    /** 按栈回到指定页面 */
    private void goTo(String key){
        restoring=true;
        try{
            if(key.equals("home"))home();else if(key.equals("vod"))vod();else if(key.equals("live"))live();else if(key.equals("my"))mine();
            else if(key.startsWith("catalog:")){String rest=key.substring(8);int cut=rest.indexOf(':');String type=cut<0?rest:rest.substring(0,cut);String tag=cut<0?"热门":rest.substring(cut+1);browse(type,tag);}
            else if(key.equals("favorites"))saved("favorites");else if(key.equals("history"))saved("history");else if(key.equals("sources"))sources();
        }finally{restoring=false;}
    }
    /** 顶栏返回按钮：先按栈回上一级，栈空才回首页 */
    private void goBack(){
        if(!backStack.isEmpty()){String target=backStack.pop();goTo(target);return;}
        if(!page.equals("home")){goTo("home");return;}
    }

    // ================= 页面 =================

    private void home(){
        reset("home");searchBox("搜索电影、剧集",()->{String term=query.getText().toString().trim();if(!term.isEmpty())namedSearch(term);});
        LinearLayout body=Ui.column(this);mainScroll=Ui.scroll(this,body);content.addView(mainScroll,new LinearLayout.LayoutParams(-1,0,1));
        // 今日精选：白卡，左文右图。浅色主题下不再用深色渐变大图 —— 暖白底上压深图会又脏又重
        FrameLayout hero=new FrameLayout(this);hero.setBackground(Ui.shape(Ui.SURFACE,Ui.LINE,18));Ui.elevate(hero,2f);
        LinearLayout words=Ui.row(this);Ui.padding(words,16);
        LinearLayout copy=Ui.column(this);
        copy.addView(Ui.label(this,"今日精选",11,Ui.ACCENT));copy.addView(Ui.gap(this,4));
        TextView featured=Ui.heading(this,"发现下一部好故事");featured.setMaxLines(1);featured.setEllipsize(android.text.TextUtils.TruncateAt.END);copy.addView(featured);
        copy.addView(Ui.gap(this,4));copy.addView(Ui.label(this,"从你的片源里挑一部今晚的",13,Ui.MUTED));
        words.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        Button action=Ui.primary(this,"电影片单",()->browse("movie","热门"));words.addView(action);
        hero.addView(words,new FrameLayout.LayoutParams(-1,-1));
        ImageView cover=new ImageView(this);cover.setScaleType(ImageView.ScaleType.CENTER_CROP);Ui.clipRound(cover,12);
        FrameLayout.LayoutParams coverLp=new FrameLayout.LayoutParams(Ui.dp(this,150),-1,Gravity.END|Gravity.CENTER_VERTICAL);coverLp.setMargins(0,Ui.dp(this,12),Ui.dp(this,12),Ui.dp(this,12));hero.addView(cover,coverLp);
        body.addView(hero,new LinearLayout.LayoutParams(-1,Ui.dp(this,Ui.tv(this)?108:96)));body.addView(Ui.gap(this,8));
        List<Catalog.Item> history=store.items("history");if(!history.isEmpty()){LinearLayout recent=section(body,"最近观看",()->saved("history"));posterRow(recent,"recent",history.subList(0,Math.min(8,history.size())));}
        int token=generation;homeSection(body,"热门电影","movie",token,cover,featured,action);homeSection(body,"热播剧集","tv",token,null,null,null);body.addView(Ui.gap(this,20));status.setText("热门推荐 · 点击海报查找片源");
    }
    private LinearLayout section(LinearLayout parent,String name,Runnable more){parent.addView(Ui.gap(this,14));LinearLayout head=Ui.row(this);head.addView(Ui.heading(this,name),new LinearLayout.LayoutParams(0,-2,1));head.addView(Ui.link(this,"更多",more));parent.addView(head);LinearLayout box=Ui.column(this);sectionTitles.put(box,(TextView)head.getChildAt(0));parent.addView(box);return box;}
    private void posterRow(LinearLayout box,String key,List<Catalog.Item> cards){box.removeAllViews();HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setHorizontalScrollBarEnabled(false);scroll.setClipToPadding(false);scroll.setTag("h:"+key);LinearLayout row=Ui.row(this);row.setPadding(Ui.dp(this,3),Ui.dp(this,8),Ui.dp(this,3),Ui.dp(this,8));int width=Ui.tv(this)?128:104;for(Catalog.Item item:cards){View card=Ui.poster(this,item,width,()->open(item));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(Ui.dp(this,width),-2);lp.setMargins(0,0,Ui.dp(this,10),0);row.addView(card,lp);}scroll.addView(row);box.addView(scroll);}
    private void homeSection(LinearLayout body,String name,String type,int token,ImageView hero,TextView title,Button action){LinearLayout box=section(body,name,()->browse(type,"热门"));List<Catalog.Item> cached=store.cached(Discover.key(type,"热门"));if(!cached.isEmpty())posterRow(box,name,cached.subList(0,Math.min(12,cached.size())));else{LinearLayout skeleton=Ui.row(this);for(int n=0;n<3;n++){View placeholder=new View(this);placeholder.setBackground(Ui.shape(Ui.CHIP,0));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,Ui.dp(this,160),1);lp.setMargins(Ui.dp(this,3),Ui.dp(this,8),Ui.dp(this,3),0);skeleton.addView(placeholder,lp);}box.addView(skeleton);}
        tasks.add(workers.submit(()->{try{List<Catalog.Item> cards=Discover.load(store,type,"热门",0);runOnUiThread(()->{if(!current(token))return;posterRow(box,name,cards.subList(0,Math.min(12,cards.size())));TextView titleView=sectionTitles.get(box);if(titleView!=null)titleView.setText(!cards.isEmpty()&&!cards.get(0).source.isEmpty()?(type.equals("tv")?"剧集 · 最近更新":"电影 · 最近更新"):name);if(hero!=null&&!cards.isEmpty()){Catalog.Item first=cards.get(0);Images.load(this,first.poster,hero);title.setText(first.title);action.setText("找片源");action.setOnClickListener(v->open(first));}});}catch(Exception e){runOnUiThread(()->{if(!current(token)||!cached.isEmpty())return;box.removeAllViews();box.addView(Ui.label(this,"片单暂未加载成功，仍可直接搜索影片",14,Ui.MUTED));box.addView(Ui.soft(this,"重新加载",this::home));});}}));
    }
    private void browse(String type,String tag){pushBack();reset("catalog:"+type);catalogType=type;catalogTag=tag;catalogOffset=0;content.addView(Ui.heading(this,type.equals("movie")?"发现电影":"发现剧集"));LinearLayout tabs=Ui.row(this);String[] tags=type.equals("movie")?new String[]{"热门","最新","经典","豆瓣高分","华语","欧美","动画"}:new String[]{"热门","国产剧","美剧","韩剧","日剧","综艺","纪录片"};for(String value:tags){Button b=Ui.chipOutline(this,value,tag.equals(value),()->browse(type,value));tabs.addView(b);}HorizontalScrollView categories=new HorizontalScrollView(this);categories.setHorizontalScrollBarEnabled(false);categories.setTag("h:catalog");categories.addView(tabs);content.addView(categories);createGrid();content.addView(Ui.soft(this,"加载更多",()->loadCatalog(true)));loadCatalog(false);}
    private void loadCatalog(boolean more){final int token=generation;final int offset=more?catalogOffset+24:0;status.setText("正在加载"+catalogTag+"片单…");tasks.add(workers.submit(()->{try{List<Catalog.Item> cards=Discover.load(store,catalogType,catalogTag,offset);runOnUiThread(()->{if(!current(token))return;if(!more)shown.clear();Set<String> known=new HashSet<>();for(Catalog.Item i:shown)known.add(Catalog.titleKey(i.title));int added=0;for(Catalog.Item i:cards)if(known.add(Catalog.titleKey(i.title))){shown.add(i);added++;}catalogOffset=offset;refresh();status.setText(added==0?"暂无更多内容":"共 "+shown.size()+" 部 · 点击海报查找片源");});}catch(Exception e){runOnUiThread(()->{if(current(token))status.setText("片单暂不可用，可重试或使用点播搜索");});}}));}
    private void mine(){reset("my");LinearLayout body=Ui.column(this);mainScroll=Ui.scroll(this,body);content.addView(mainScroll,new LinearLayout.LayoutParams(-1,0,1));body.addView(Ui.heading(this,"我的放映厅"));body.addView(Ui.gap(this,8));body.addView(Ui.label(this,"收藏喜欢的内容，接着上次的进度看",14,Ui.MUTED));body.addView(Ui.gap(this,20));LinearLayout stats=Ui.row(this);String[] values={store.array("favorites").length()+"\n收藏",store.array("history").length()+"\n记录",store.array("sources").length()+"\n片源"};for(String value:values){TextView cell=Ui.text(this,value,19);cell.setGravity(Gravity.CENTER);cell.setBackground(Ui.shape(Ui.SURFACE,Ui.LINE));Ui.elevate(cell,1f);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,Ui.dp(this,88),1);lp.setMargins(Ui.dp(this,4),0,Ui.dp(this,4),0);stats.addView(cell,lp);}body.addView(stats);body.addView(Ui.gap(this,20));menu(body,"我的收藏","影片与频道，随时回来继续看",()->saved("favorites"));menu(body,"观看记录","点播自动保存播放进度",()->saved("history"));menu(body,"源管理","添加片源、启用开关、查看健康状态",this::sources);menu(body,"批量测活","检查全部源或所有直播频道",()->UiDialog.list(this,"批量测活",new String[]{"所有源地址","所有直播频道"},-1,n->ProbeActivity.launch(this,n==0?"sources":"allChannels")));menu(body,"首页片单来源","豆瓣热门或已启用片源的最近更新",this::recommendSettings);menu(body,"配置导入 / 导出","备份源、收藏与观看记录",this::transfer);menu(body,"隐私与许可","本地数据与开源信息",this::privacy);menu(body,"关于 静观 TV",getString(R.string.app_name)+" "+BuildConfig.VERSION_NAME,this::about);status.setText("数据保存在本机 · 无需账号");}
    private void menu(LinearLayout parent,String title,String subtitle,Runnable action){LinearLayout card=Ui.panel(this);card.setBackground(Ui.focus());Ui.focusable(card,this);card.setClickable(true);card.setOnClickListener(v->action.run());card.addView(Ui.label(this,title,17,Ui.TEXT));card.addView(Ui.gap(this,5));card.addView(Ui.label(this,subtitle,13,Ui.MUTED));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,Ui.dp(this,10));parent.addView(card,lp);}
    private void recommendSettings(){UiDialog.list(this,"首页片单来源",new String[]{"豆瓣热门（失败时使用片源）","仅使用已启用的片源"},store.object("preferences").optString("discover","douban").equals("cms")?1:0,n->{try{JSONObject preferences=store.object("preferences");preferences.put("discover",n==0?"douban":"cms");store.putObject("preferences",preferences);for(String type:new String[]{"movie","tv"})for(String tag:new String[]{"热门","最新","经典","豆瓣高分","华语","欧美","动画","国产剧","美剧","韩剧","日剧","综艺","纪录片"})store.remove(Discover.key(type,tag));home();}catch(Exception e){Ui.message(this,error(e));}});}
    private void privacy(){LinearLayout body=Ui.column(this);Ui.padding(body,4);body.addView(Ui.label(this,"源配置、收藏、观看记录和搜索历史保存在设备本地。应用不设置账号，不收集遥测，也没有内置广告。搜索、推荐、封面和播放请求会直接发送到你配置的源及推荐服务。\n\n推荐使用豆瓣接口，失败时尝试已配置的 CMS 源；图片会缓存到应用私有目录。配置导出由你选择文件位置，卸载应用会删除本地数据。\n\n本项目基于 LibreSpark/LibreTV，按 AGPL-3.0-or-later 提供源码。Media3 使用 Apache-2.0；兼容版 CA 数据使用 MPL-2.0。",14,Ui.MUTED));UiDialog.custom(this,"隐私与开源",body,"完整许可",()->{try(InputStream in=getAssets().open("LICENSE.txt");ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int read;while((read=in.read(b))!=-1)out.write(b,0,read);LinearLayout text=Ui.column(this);text.addView(Ui.label(this,out.toString("UTF-8"),12,Ui.MUTED));UiDialog.custom(this,"开源许可",text,null,null);}catch(Exception e){Ui.message(this,"许可见源码仓库");}});}
    private void reset(String next){
        // 先把当前页的视觉状态存下来 —— 无论这次是切换页面还是原地重建
        saveSnapshot();
        generation++;for(Future<?> task:tasks)task.cancel(true);tasks.clear();page=next;content.removeAllViews();items.clear();shown.clear();grouped.clear();list=null;query=null;emptyTitle=null;emptyHint=null;group="全部分组";mainScroll=null;status.setText("");
        String selected=next.equals("live")?"live":next.equals("vod")?"vod":next.equals("home")||next.startsWith("catalog")?"home":"my";for(Map.Entry<String,Button> nav:navigation.entrySet())nav.getValue().setSelected(nav.getKey().equals(selected));
        // 只有二级页面才显示顶栏：一级页面（首页 / 点播 / 直播 / 我的）是主导航，
        // 顶栏留着只会占掉一整行高度，把内容挤到首屏之外。
        // 源管理的入口在「我的」里，顶栏不再重复放一个。
        boolean primary=next.equals("home")||next.equals("vod")||next.equals("live")||next.equals("my");
        if(headerView!=null)headerView.setVisibility(primary?View.GONE:View.VISIBLE);
        if(pageTitle!=null)pageTitle.setText(primary?"":titleFor(next));
        if(backButton!=null)backButton.setVisibility(primary?View.GONE:View.VISIBLE);
        // 页面搭好后把滚动位置、搜索词、焦点位置还原回去
        content.post(this::restoreSnapshot);
    }
    /** 二级页面的顶栏标题：一级页面没有顶栏，这里只管二级 */
    private String titleFor(String key){
        if(key.startsWith("catalog:"))return key.startsWith("catalog:tv")?"发现剧集":"发现电影";
        if(key.equals("favorites"))return "我的收藏";
        if(key.equals("history"))return "观看记录";
        if(key.equals("sources"))return "源管理";
        return "静观 TV";
    }
    private void searchBox(String hint,Runnable action){
        query=Ui.input(this,hint);query.setImeOptions(EditorInfo.IME_ACTION_SEARCH);content.addView(Ui.searchField(this,hint,query,action));
        // 搜索框和下面的内容之间留一段空白：贴着排会显得挤成一团
        content.addView(Ui.gap(this,16));
    }
    private void createList(){
        ListView rows=new ListView(this);rows.setDivider(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));rows.setDividerHeight(Ui.dp(this,7));rows.setChoiceMode(ListView.CHOICE_MODE_SINGLE);rows.setFocusable(true);list=rows;
        list.setAdapter(new android.widget.BaseAdapter(){
            public int getCount(){return shown.size();}public Object getItem(int p){return shown.get(p);}public long getItemId(int p){return p;}
            public View getView(int p,View old,android.view.ViewGroup parent){
                TextView text=old instanceof TextView?(TextView)old:Ui.text(MainActivity.this,"",Ui.tv(MainActivity.this)?20:17);
                Catalog.Item item=shown.get(p);String second=item.live?item.group:item.sourceName;
                JSONObject health=channelHealth.optJSONObject(item.url);String badge=health==null?"":health.optBoolean("ok")?"  ·  可连接 "+health.optLong("millis")+" ms":"  ·  连接异常";text.setText((store.favorite(item)?"★ ":"")+item.title+(second.isEmpty()?"":"\n"+second)+badge);text.setMinHeight(Ui.dp(MainActivity.this,68));text.setBackground(Ui.focus());text.setTextColor(Ui.TEXT);return text;
            }});
        list.setOnItemClickListener((parent,v,p,id)->open(shown.get(p)));
        list.setOnItemLongClickListener((parent,v,p,id)->{itemMenu(shown.get(p));return true;});
        content.addView(list,new LinearLayout.LayoutParams(-1,0,1));
    }
    /**
     * 刷新列表，并保住滚动位置。
     *
     * 搜索时每个源到达都会触发一次刷新，而原生 ListView/GridView 在
     * notifyDataSetChanged 之后会把列表弹回顶部——用户刚看到第 5 条结果，
     * 下一个源一回来就被甩回第一屏。记录首行位置再还原即可避免。
     * （原生 Adapter 没有 notifyItemRangeInserted 这类局部刷新 API，
     *   那是 RecyclerView 才有的；这里靠增量聚合 + 视图复用把整表刷新的代价压下来。）
     */
    private void refresh(){
        if(list==null)return;
        int position=list.getFirstVisiblePosition(),offset=list.getTop();
        ((BaseAdapter)list.getAdapter()).notifyDataSetChanged();
        if(position>0)list.post(()->list.setSelectionFromTop(position,offset));
    }
    private void createGrid(){GridView grid=new GridView(this);int width=(int)(getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density)-(Ui.tv(this)?210:32);int columns=Math.max(3,width/(Ui.tv(this)?150:112));grid.setNumColumns(columns);grid.setSelector(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));grid.setHorizontalSpacing(Ui.dp(this,8));grid.setVerticalSpacing(Ui.dp(this,12));grid.setClipToPadding(false);grid.setPadding(0,Ui.dp(this,8),0,Ui.dp(this,12));int cardWidth=Math.max(88,(width-(columns-1)*8)/columns);
        grid.setAdapter(new BaseAdapter(){public int getCount(){return shown.size();}public Object getItem(int p){return shown.get(p);}public long getItemId(int p){return p;}
            // 复用被回收的卡片：只换内容不重建视图层级，滚动时不再反复创建 View 与触发图片加载
            public View getView(int p,View old,ViewGroup parent){Catalog.Item item=shown.get(p);View card=old instanceof LinearLayout?old:Ui.poster(MainActivity.this,item,cardWidth,()->open(item));Ui.rebindPoster(MainActivity.this,card,item,()->open(item));card.setOnClickListener(null);card.setClickable(false);card.setFocusable(false);return card;}});
        // GridView owns selection and activation: child click listeners bypass remote item clicks.
        grid.setOnItemClickListener((parent,v,p,id)->{if(p>=0&&p<shown.size())open(shown.get(p));});
        grid.setOnItemLongClickListener((parent,v,p,id)->{if(p>=0&&p<shown.size())itemMenu(shown.get(p));return true;});
        grid.setOnKeyListener((v,key,event)->{int p=grid.getSelectedItemPosition();if(p<0||p>=shown.size())return false;if(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER||key==KeyEvent.KEYCODE_NUMPAD_ENTER){int action=event.getAction();if(action==KeyEvent.ACTION_DOWN)gridPressed=false;if((action==KeyEvent.ACTION_DOWN||action==KeyEvent.ACTION_UP)&&!event.isCanceled()&&!gridPressed){gridPressed=true;grid.performItemClick(grid.getSelectedView(),p,grid.getAdapter().getItemId(p));}return true;}if(key==KeyEvent.KEYCODE_MENU){if(event.getAction()==KeyEvent.ACTION_UP)itemMenu(shown.get(p));return true;}return false;});
        list=grid;content.addView(grid,new LinearLayout.LayoutParams(-1,0,1));LinearLayout empty=Ui.column(this);empty.setGravity(Gravity.CENTER);Ui.padding(empty,24);emptyTitle=Ui.heading(this,page.equals("vod")?"想看什么，搜一下":page.equals("favorites")?"还没有收藏":page.equals("history")?"最近观看会显示在这里":"正在准备片单");emptyTitle.setGravity(Gravity.CENTER);empty.addView(emptyTitle);empty.addView(Ui.gap(this,12));emptyHint=Ui.label(this,page.equals("vod")?"输入片名，查找所有已启用的片源":"发现喜欢的故事，随时回来继续看",14,Ui.MUTED);emptyHint.setGravity(Gravity.CENTER);empty.addView(emptyHint);empty.addView(Ui.gap(this,18));if(page.startsWith("catalog"))empty.addView(Ui.soft(this,"重新加载",()->loadCatalog(false)));else if(!page.equals("vod"))empty.addView(Ui.soft(this,"去发现",this::home));content.addView(empty,new LinearLayout.LayoutParams(-1,0,1));grid.setEmptyView(empty);}
    private void filter(){
        shown.clear();String q=query==null?"":query.getText().toString().trim().toLowerCase(Locale.ROOT);
        for(Catalog.Item item:items)if((q.isEmpty()||item.title.toLowerCase(Locale.ROOT).contains(q))&&(group.equals("全部分组")||group.equals(item.group))&&(!onlyAvailable||channelHealth.optJSONObject(item.url)!=null&&channelHealth.optJSONObject(item.url).optBoolean("ok")))shown.add(item);
        refresh();status.setText(onlyAvailable&&shown.isEmpty()?"暂无可连接结果，请先测活或切换订阅":"共 "+shown.size()+" 项 · 长按或按菜单键收藏");
    }
    private List<JSONObject> sourcesOf(String type){List<JSONObject> out=new ArrayList<>();JSONArray array=store.array("sources");for(int n=0;n<array.length();n++){JSONObject s=array.optJSONObject(n);if(s!=null&&type.equals(s.optString("type"))&&s.optBoolean("enabled",true))out.add(s);}return out;}
    private void live(){
        reset("live");onlyAvailable=false;liveSources=sourcesOf("live");content.addView(Ui.heading(this,"直播频道"));searchBox("搜索频道或节目",this::filter);
        // 源与分组都用胶囊行，不用系统 Spinner：系统下拉是默认蓝白样式，与品牌脱节，遥控器操作也不顺手
        sourceRow=Ui.row(this);
        for(int n=0;n<liveSources.size();n++){final JSONObject source=liveSources.get(n);final int index=n;Button b=Ui.chipOutline(this,source.optString("name"),false,()->{liveSourceIndex=index;markChip(sourceRow,index);loadLive(source);});sourceRow.addView(b);}
        if(liveSources.isEmpty())sourceRow.addView(Ui.label(this,"请先添加直播 M3U 源",13,Ui.MUTED));
        HorizontalScrollView sourceScroll=new HorizontalScrollView(this);sourceScroll.setHorizontalScrollBarEnabled(false);sourceScroll.setTag("h:liveSource");sourceScroll.addView(sourceRow);
        groupRow=Ui.row(this);HorizontalScrollView groupScroll=new HorizontalScrollView(this);groupScroll.setHorizontalScrollBarEnabled(false);groupScroll.setTag("h:liveGroup");groupScroll.addView(groupRow);
        LinearLayout pickers=Ui.column(this);pickers.addView(sourceScroll);pickers.addView(groupScroll);content.addView(pickers);
        LinearLayout actions=Ui.row(this);actions.addView(Ui.button(this,"当前列表测活",()->{if(!shown.isEmpty())ProbeActivity.channels(this,new ArrayList<>(shown));else Ui.toast(this,"请先载入频道");}));actions.addView(Ui.button(this,"全部测活",()->ProbeActivity.launch(this,"allChannels")));Button healthy=Ui.soft(this,"仅可连接",()->{onlyAvailable=!onlyAvailable;filter();});healthy.setOnClickListener(v->{onlyAvailable=!onlyAvailable;healthy.setSelected(onlyAvailable);filter();});actions.addView(healthy);HorizontalScrollView tools=new HorizontalScrollView(this);tools.setHorizontalScrollBarEnabled(false);tools.addView(actions);content.addView(tools);createList();
        if(restoreLiveIndex>=0){liveSourceIndex=Math.min(restoreLiveIndex,Math.max(0,liveSources.size()-1));restoreLiveIndex=-1;}
        if(liveSourceIndex>=0&&liveSourceIndex<liveSources.size()){markChip(sourceRow,liveSourceIndex);loadLive(liveSources.get(liveSourceIndex));}
        if(liveSources.isEmpty())status.setText("源管理 → 添加 → 直播 M3U，支持手机和遥控器操作");
    }
    /** 把胶囊行里第 index 项标成选中态 */
    private void markChip(LinearLayout row,int index){for(int n=0;n<row.getChildCount();n++)if(row.getChildAt(n) instanceof Button)((Button)row.getChildAt(n)).setSelected(n==index);}
    private void loadLive(JSONObject source){
        final int token=++generation;group="全部分组";items.clear();shown.clear();items.addAll(store.cached("playlist:"+source.optString("url")));filter();status.setText("正在更新 "+source.optString("name")+"…");
        tasks.add(workers.submit(()->{try{
            List<Catalog.Item> channels=Catalog.m3u(Net.get(source.optString("url")),source.optString("url"));if(channels.isEmpty())throw new IOException("列表没有 HTTP/HTTPS 节目，请检查是否为 M3U 地址");
            for(Catalog.Item i:channels){i.sourceName=source.optString("name");i.source=source.optString("url");}store.cache("playlist:"+source.optString("url"),channels);android.util.Log.i("LibreTV-Net","Subscription loaded: "+channels.size()+" entries");
            runOnUiThread(()->{if(!current(token))return;items.clear();items.addAll(channels);LinkedHashSet<String> gs=new LinkedHashSet<>();gs.add("全部分组");for(Catalog.Item i:items)if(!i.group.isEmpty())gs.add(i.group);
                List<String> groups=new ArrayList<>(gs);group="全部分组";groupRow.removeAllViews();
                for(String value:groups){final String name=value;Button b=Ui.chipOutline(this,name,false,()->{group=name;markChip(groupRow,groups.indexOf(name));filter();});groupRow.addView(b);}
                if(restoreGroup!=null&&!restoreGroup.isEmpty()){int at=groups.indexOf(restoreGroup);if(at>=0){markChip(groupRow,at);group=restoreGroup;}}
                restoreGroup="";filter();});
        }catch(Exception e){runOnUiThread(()->{if(current(token))status.setText(items.isEmpty()?"订阅读取失败："+error(e)+" · 可在源管理中修改或测试":"更新失败，正在展示上次缓存的频道");});}}));
    }
    private void vod(){reset("vod");content.addView(Ui.heading(this,"全源搜索"));searchBox("输入电影或剧集名称",this::searchVod);JSONArray history=store.array("searches");if(history.length()>0){LinearLayout chips=Ui.row(this);for(int n=0;n<Math.min(5,history.length());n++){String term=history.optString(n);chips.addView(Ui.chipOutline(this,term,false,()->namedSearch(term)));}HorizontalScrollView recent=new HorizontalScrollView(this);recent.setHorizontalScrollBarEnabled(false);recent.setTag("h:searchHistory");recent.addView(chips);content.addView(recent);}createGrid();int count=sourcesOf("vod").size();status.setText(count==0?"请先在源管理中添加点播源":"输入片名后点击搜索 · 已配置 "+count+" 个点播源");}
    private void namedSearch(String name){vod();query.setText(name);searchVod();}
    private void searchVod(){
        String term=query.getText().toString().trim();if(term.isEmpty()){Ui.toast(this,"请输入片名");return;}List<JSONObject> sources=sourcesOf("vod");if(sources.isEmpty()){Ui.message(this,"请先在源管理添加 Apple CMS 点播源");return;}
        ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(query.getWindowToken(),0);query.clearFocus();for(Future<?> task:tasks)task.cancel(true);tasks.clear();store.searchHistory(term);if(emptyTitle!=null)emptyTitle.setText("正在全源搜索…");final int token=++generation;items.clear();shown.clear();grouped.clear();refresh();status.setText("正在搜索 "+sources.size()+" 个源…");AtomicInteger done=new AtomicInteger(),failed=new AtomicInteger();
        for(JSONObject source:sources)tasks.add(workers.submit(()->{List<Catalog.Item> found=new ArrayList<>();String failure="";try{found=Catalog.search(Net.get(cmsUrl(source.optString("url"),"videolist","wd",term)),source.optString("url"),source.optString("name"));}catch(Exception e){failed.incrementAndGet();failure=error(e);}final List<Catalog.Item> results=found;final String issue=failure;
            // 增量聚合：每个源到达时只处理它自己的结果。
            // 旧实现每来一个源就清空并重建 grouped 与 shown 全表（结果上千时是 O(源数×总数)），
            // 9 个源就能把主线程压住，电视上表现为搜索过程中界面僵住。
            runOnUiThread(()->{if(!current(token))return;int inserted=0;for(Catalog.Item item:results){items.add(item);String key=Catalog.titleKey(item.title);List<Catalog.Item> choices=grouped.get(key);if(choices==null){choices=new ArrayList<>();grouped.put(key,choices);choices.add(item);Catalog.Item first=Catalog.Item.from(item.json());first.remarks="1 个来源 · "+first.remarks;shown.add(first);inserted++;}else{choices.add(item);for(int n=0;n<shown.size();n++){Catalog.Item card=shown.get(n);if(key.equals(Catalog.titleKey(card.title))){Catalog.Item first=Catalog.Item.from(choices.get(0).json());first.remarks=choices.size()+" 个来源 · "+first.remarks;shown.set(n,first);break;}}}}refresh();int completed=done.incrementAndGet();if(completed==sources.size()&&shown.isEmpty()&&emptyTitle!=null){emptyTitle.setText("没有找到匹配的影片");emptyHint.setText("换个关键词试试，或在源管理中检查片源");}status.setText("已搜索 "+completed+"/"+sources.size()+" 个源 · "+shown.size()+" 部影片"+(failed.get()>0?" · "+failed.get()+" 个源暂不可用":"")+(items.isEmpty()&&completed==sources.size()?" · 可换个关键词或检查源":""));});}));
    }
    static String cmsUrl(String base,String action,String key,String value){
        Uri uri=Uri.parse(base);Uri.Builder builder=uri.buildUpon().clearQuery();for(String name:uri.getQueryParameterNames())if(!Arrays.asList("ac","wd","ids","pg").contains(name))for(String v:uri.getQueryParameters(name))builder.appendQueryParameter(name,v);
        builder.appendQueryParameter("ac",action);if(key!=null)builder.appendQueryParameter(key,value);return builder.build().toString();
    }
    private void saved(String key){pushBack();reset(key);content.addView(Ui.heading(this,key.equals("history")?"观看记录":"我的收藏"));items.addAll(store.items(key));createGrid();shown.addAll(items);refresh();status.setText((key.equals("history")?"最近观看，点播自动续播":"长按卡片可管理收藏")+" · "+items.size()+" 项");}
    private void open(Catalog.Item item){
        if(!item.url.isEmpty()){if(item.live&&page.equals("live")){play(new ArrayList<>(shown),Math.max(0,shown.indexOf(item)));}else play(Collections.singletonList(item),0);return;}
        if(item.source.isEmpty()){namedSearch(item.title);return;}
        // 搜索聚合出的同名影片把全部片源带进详情页，由详情页内的片源行直接切换，不再弹窗二次选择
        List<Catalog.Item> choices=grouped.get(Catalog.titleKey(item.title));
        if(page.equals("vod")&&choices!=null&&choices.size()>1)detail(choices.get(0),choices);else detail(item,choices);
    }
    private void detail(Catalog.Item item){detail(item,null);}
    private void detail(Catalog.Item item,List<Catalog.Item> choices){
        Intent intent=new Intent(this,DetailActivity.class).putExtra("item",item.json().toString());
        if(choices!=null&&choices.size()>1){JSONArray array=new JSONArray();for(Catalog.Item choice:choices)array.put(choice.json());intent.putExtra("choices",array.toString());}
        startActivity(intent);
    }
    private void episodeList(String title,List<Catalog.Item> episodes){String[] labels=new String[episodes.size()];for(int n=0;n<labels.length;n++)labels[n]=episodes.get(n).title;UiDialog.list(this,title+" · 选集",labels,-1,p->play(episodes,p));}
    private void play(List<Catalog.Item> media,int at){Playback.open(this,media,at);}
    private void itemMenu(Catalog.Item item){if(item.source.isEmpty()&&item.url.isEmpty()){open(item);return;}UiDialog.list(this,item.title,new String[]{"打开",store.favorite(item)?"取消收藏":"加入收藏"},-1,n->{if(n==0)open(item);else{store.toggleFavorite(item);Ui.toast(this,"收藏已更新");if(page.equals("favorites"))saved("favorites");else refresh();}});}
    @Override public boolean onKeyDown(int key,KeyEvent e){if(key==KeyEvent.KEYCODE_MENU&&list!=null&&list.getSelectedItemPosition()>=0&&list.getSelectedItemPosition()<shown.size()){itemMenu(shown.get(list.getSelectedItemPosition()));return true;}return super.onKeyDown(key,e);}
    /**
     * 拉取官方片源清单并与本地源合并。
     *
     * manual=true 时由用户点击触发，给出明确结果提示；
     * manual=false 时为启动时的静默同步：失败不打扰用户，本地源保持原样。
     * 同步成功后若停在源管理页则刷新列表，让用户立刻看到新增的源。
     */
    private void syncOfficialSources(boolean manual){
        // 启动时的静默同步按间隔限流：距上次成功不足 6 小时就不再打网络
        if(!manual&&!OfficialSources.due(store,System.currentTimeMillis()))return;
        if(manual)Ui.toast(this,"正在获取官方源…");
        tasks.add(workers.submit(()->{
            String failure="";int added=0,updated=0,total=0;
            try{
                List<JSONObject> incoming=OfficialSources.parse(OfficialSources.fetch());
                JSONArray local=store.array("sources");
                OfficialSources.Result result=OfficialSources.merge(local,incoming,OfficialSources.readDismissed(store));
                store.put("sources",local);
                OfficialSources.markSynced(store,System.currentTimeMillis());
                added=result.added;updated=result.updated;total=result.total;
            }catch(Exception e){failure=error(e);}
            final String issue=failure;final int fresh=added,changed=updated,count=total;
            runOnUiThread(()->{
                if(issue!=null&&!issue.isEmpty()){
                    if(manual)Ui.message(this,"获取官方源失败："+issue+"\n本地已配置的源不受影响。");
                    else android.util.Log.w("LibreTV-Sources","官方源同步失败："+issue);
                    return;
                }
                if(manual)Ui.message(this,count+" 个源可用"+(fresh>0?"，新增 "+fresh+" 个":"")+(changed>0?"，更新 "+changed+" 个":""));
                if(page.equals("sources"))sources();
            });
        }));
    }
    private void sources(){
        pushBack();reset("sources");JSONArray array=store.array("sources");JSONObject health=store.object("source-health");content.addView(Ui.heading(this,"我的片源 · "+array.length()));LinearLayout tools=Ui.row(this);tools.addView(Ui.primary(this,"全部测活",()->ProbeActivity.launch(this,"sources")));tools.addView(Ui.soft(this,"添加源",()->editSource(-1)));tools.addView(Ui.soft(this,"官方清单",()->syncOfficialSources(true)));tools.addView(Ui.soft(this,"导入导出",this::transfer));HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setHorizontalScrollBarEnabled(false);scroll.setTag("h:sourceTools");scroll.addView(tools);content.addView(scroll);ListView rows=new ListView(this);rows.setDivider(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));rows.setDividerHeight(Ui.dp(this,8));rows.setAdapter(new BaseAdapter(){public int getCount(){return array.length();}public Object getItem(int n){return array.optJSONObject(n);}public long getItemId(int n){return n;}
            public View getView(int at,View old,ViewGroup parent){JSONObject source=array.optJSONObject(at);JSONObject result=health.optJSONObject(source.optString("url"));LinearLayout card=Ui.panel(MainActivity.this);LinearLayout line=Ui.row(MainActivity.this);TextView name=Ui.label(MainActivity.this,source.optString("name"),17,Ui.TEXT);line.addView(name,new LinearLayout.LayoutParams(0,-2,1));line.addView(Ui.soft(MainActivity.this,source.optBoolean("enabled",true)?"已启用":"已停用",()->{try{source.put("enabled",!source.optBoolean("enabled",true));store.put("sources",array);sources();}catch(Exception ignored){}}));card.addView(line);String state=result==null?"未检测":result.optBoolean("ok")?"可连接 · "+result.optLong("millis")+" ms":"连接异常 · "+result.optString("error");card.addView(Ui.label(MainActivity.this,(source.optString("type").equals("live")?"直播 M3U":"点播 CMS")+"  ·  "+state,13,result==null?Ui.MUTED:result.optBoolean("ok")?Ui.ACCENT:Ui.BAD));card.addView(Ui.gap(MainActivity.this,8));TextView address=Ui.label(MainActivity.this,source.optString("url"),11,Ui.MUTED);address.setMaxLines(1);address.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);card.addView(address);LinearLayout actions=Ui.row(MainActivity.this);actions.addView(Ui.soft(MainActivity.this,"编辑",()->editSource(at)));actions.addView(Ui.soft(MainActivity.this,"更多",()->sourceOptions(at)));card.addView(actions);return card;}});content.addView(rows,new LinearLayout.LayoutParams(-1,0,1));status.setText("停用的源不会参与搜索、推荐或直播列表");
    }
    private void sourceOptions(int at){JSONObject source=store.array("sources").optJSONObject(at);if(source==null)return;UiDialog.list(this,source.optString("name"),new String[]{"测试可用性","编辑","删除"},-1,n->{if(n==0)testSource(source);else if(n==1)editSource(at);else UiDialog.confirm(this,"删除这个源？","删除后该源不再参与搜索与直播，可以随时重新添加。","取消","删除",()->{JSONArray a=store.array("sources"),next=new JSONArray();for(int k=0;k<a.length();k++)if(k!=at)next.put(a.optJSONObject(k));store.put("sources",next);
            // 官方源被用户删除后不再自动加回；用户自己添加的源不受影响
            if(source.optBoolean("official",false))OfficialSources.dismiss(store,source.optString("type"),source.optString("url"));
            sources();});});}
    private void editSource(int at){
        JSONArray array=store.array("sources");JSONObject old=at>=0?array.optJSONObject(at):null;
        LinearLayout fields=Ui.column(this);int p=Ui.dp(this,18);fields.setPadding(p,p,p,p);EditText name=Ui.input(this,"源名称"),url=Ui.input(this,"https://…（M3U 列表或 CMS API）");url.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);
        // 类型选择也用胶囊，不用系统 Spinner
        final int[] typeIndex={0};final LinearLayout typeRow=Ui.row(this);
        Button live=Ui.chipOutline(this,"直播 M3U / M3U8",true,()->{typeIndex[0]=0;markChip(typeRow,0);});
        Button vod=Ui.chipOutline(this,"点播 Apple CMS JSON",false,()->{typeIndex[0]=1;markChip(typeRow,1);});
        typeRow.addView(live);typeRow.addView(vod);
        fields.addView(Ui.label(this,"类型",13,Ui.MUTED));fields.addView(Ui.gap(this,8));fields.addView(typeRow);fields.addView(Ui.gap(this,12));fields.addView(name);fields.addView(Ui.gap(this,10));fields.addView(url);
        if(old!=null){name.setText(old.optString("name"));url.setText(old.optString("url"));if(old.optString("type").equals("vod")){typeIndex[0]=1;markChip(typeRow,1);}}
        UiDialog.form(this,at<0?"添加源":"编辑源",fields,"取消","保存",()->{String address=Catalog.httpUrl(url.getText().toString(),null);if(address==null){url.setError("请输入有效的 HTTP/HTTPS 地址");return false;}try{JSONObject item=new JSONObject().put("name",name.getText().toString().trim().isEmpty()?"自定义源":name.getText().toString().trim()).put("url",address).put("type",typeIndex[0]==0?"live":"vod");if(at>=0)array.put(at,item);else array.put(item);store.put("sources",array);sources();return true;}catch(Exception e){Ui.message(this,error(e));return false;}});
    }
    private void testSource(JSONObject source){Ui.toast(this,"正在检测…");int token=generation;tasks.add(workers.submit(()->{Probe.Session session=new Probe.Session();Catalog.Item item=new Catalog.Item();item.title=source.optString("name");item.source=source.optString("url");item.live=source.optString("type").equals("live");item.url=item.live?item.source:cmsUrl(item.source,"videolist",null,null);Probe.Result result=session.check(item,true);session.cancel();runOnUiThread(()->{JSONObject health=store.object("source-health");try{health.put(item.source,result.json());store.putObject("source-health",health);}catch(Exception ignored){}if(!current(token))return;if(page.equals("sources"))sources();Ui.message(this,result.ok?"可连接 · "+result.count+" 项 · "+result.millis+" ms":"检测失败："+result.error);});}));}
    private void transfer(){UiDialog.list(this,"源配置迁移",new String[]{"导入 JSON 文件","导出配置、收藏和记录"},-1,n->{Intent intent=n==0?new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE):new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"LibreTV-App-settings.json");startActivityForResult(intent,n==0?21:22);});}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;try{
        if(request==22){JSONObject json=new JSONObject().put("version",1).put("sources",store.array("sources")).put("favorites",store.array("favorites")).put("history",store.array("history")).put("positions",store.exportPositions()).put("preferences",store.object("preferences"));try(OutputStream out=getContentResolver().openOutputStream(data.getData())){out.write(json.toString(2).getBytes("UTF-8"));}Ui.toast(this,"已导出配置");}
        else if(request==21){ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=getContentResolver().openInputStream(data.getData())){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(out.size()+n>2*1024*1024)throw new IOException("配置超过 2 MB");out.write(b,0,n);}}JSONObject j=new JSONObject(out.toString("UTF-8"));JSONArray imported=new JSONArray();appendSources(imported,j.optJSONArray("sources"),"vod");appendSources(imported,j.optJSONArray("liveSources"),"live");JSONObject settings=j.optJSONObject("settings");JSONObject desktop=j.optJSONObject("data");if(desktop!=null&&desktop.optString("libretv-settings").length()>0){JSONObject wrapped=new JSONObject(desktop.optString("libretv-settings"));settings=wrapped.optJSONObject("state");}if(settings!=null){appendSources(imported,settings.optJSONArray("customAPIs"),"vod");appendSources(imported,settings.optJSONArray("liveSubscriptions"),"live");}if(imported.length()==0)throw new IOException("配置中没有有效的源");UiDialog.confirm(this,"导入 "+imported.length()+" 个源？","将替换当前源配置，本地收藏与记录不受影响。","取消","导入",()->{store.put("sources",imported);if(j.optJSONArray("favorites")!=null)store.put("favorites",j.optJSONArray("favorites"));if(j.optJSONArray("history")!=null)store.put("history",j.optJSONArray("history"));store.importPositions(j.optJSONObject("positions"));if(j.optJSONObject("preferences")!=null)store.putObject("preferences",j.optJSONObject("preferences"));sources();});}
    }catch(Exception e){Ui.message(this,"操作失败："+error(e));}}
    private void appendSources(JSONArray out,JSONArray sources,String fallback)throws JSONException{if(sources==null)return;for(int n=0;n<sources.length();n++){JSONObject s=sources.optJSONObject(n);if(s==null||Catalog.httpUrl(s.optString("url"),null)==null)continue;String type=s.optString("type",fallback);out.put(new JSONObject().put("name",s.optString("name","导入的源")).put("url",s.optString("url")).put("type",type.equals("live")?"live":"vod").put("enabled",s.optBoolean("enabled",true)));}}
    private void about(){LinearLayout body=Ui.column(this);body.addView(Ui.label(this,"静观 TV · 非官方 LibreTV 客户端\n手机触屏 / Android TV 遥控器通用\n最低 Android 5.0，Android 8.0 及以上自动使用系统证书库\n\n支持 HTTP/HTTPS M3U、Apple CMS JSON 和原生 HLS/MP4 播放。解码能力取决于设备，不支持网页解析及 Spider 引擎。\n\n上游：LibreSpark/LibreTV\n许可：AGPL-3.0-or-later\n原生 Android 端：2026-10-25\n本软件按原样提供，不作担保。源码和许可可在仓库查看。",14,Ui.MUTED));
        UiDialog.custom(this,getString(R.string.app_name)+" "+BuildConfig.VERSION_NAME,body,"源码与许可",()->{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/wangshaojie/LibreTV-App")));}catch(ActivityNotFoundException e){Ui.message(this,"此设备未安装浏览器，请用手机或电脑访问：\nhttps://github.com/wangshaojie/LibreTV-App");}});}
    private String error(Exception e){return e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}
    @Override protected void onResume(){super.onResume();if(store==null)return;channelHealth=store.object("channel-health");
        // 这些页面会因为数据变化（收藏、源状态）需要重建；重建后由快照把滚动位置和焦点还原回去
        if(page.equals("favorites")||page.equals("history"))saved(page);else if(page.equals("sources"))sources();else if(page.equals("my"))mine();else if(page.equals("live"))filter();}
    private void restorePage(Bundle saved){if(saved==null){home();return;}String next=saved.getString("page","home");String term=saved.getString("query","");if(next.equals("vod")){vod();query.setText(term);if(!term.isEmpty())searchVod();}else if(next.equals("live")){restoreLiveIndex=saved.getInt("liveSource",0);restoreGroup=saved.getString("group","");live();query.setText(term);}else if(next.equals("sources"))sources();else if(next.equals("my"))mine();else if(next.equals("history")||next.equals("favorites"))saved(next);else if(next.startsWith("catalog"))browse(saved.getString("catalogType","movie"),saved.getString("catalogTag","热门"));else home();}
    @Override protected void onSaveInstanceState(Bundle out){out.putString("page",page);out.putString("query",query==null?"":query.getText().toString());out.putString("group",group);out.putInt("liveSource",liveSourceIndex);out.putString("catalogType",catalogType);out.putString("catalogTag",catalogTag);super.onSaveInstanceState(out);}
    /**
     * 返回键：一级页面退出应用，其余情况按返回栈回上一级。
     *
     * 退出弹框的焦点默认落在「继续浏览」而不是「退出」——
     * 遥控器按一次 OK 就退出应用是最危险的一种默认，破坏性操作必须由用户主动选。
     */
    @Override public void onBackPressed(){
        if(!backStack.isEmpty()){String target=backStack.pop();goTo(target);return;}
        if(!page.equals("home")){goTo("home");return;}
        UiDialog.confirm(this,"退出 静观 TV？","退出后将停止记录播放进度，正在播放的内容会中断。","继续浏览","退出应用",this::finish,true);
    }
    @Override protected void onDestroy(){generation++;workers.shutdownNow();super.onDestroy();}

    /**
     * 电视端把初始焦点放在左侧导航，而不是搜索框。
     *
     * 必须等窗口真正拿到焦点才能 requestFocus —— onCreate / onResume 阶段窗口还没 focus，
     * 请求会被系统忽略，结果焦点落到搜索框上，软键盘随即弹出，
     * 电视屏幕下半被键盘面板盖住，用户第一眼看到的是一片灰。
     *
     * 从详情页返回时也会走到这里，但位置恢复（restoreSnapshot）在下一帧执行，
     * 会把焦点重新放回原来那张卡片 —— 返回后仍然停在离开前的那一屏。
     */
    @Override public void onWindowFocusChanged(boolean hasFocus){
        super.onWindowFocusChanged(hasFocus);
        if(!hasFocus||!Ui.tv(this)||focusInitialized)return;
        focusInitialized=true;
        // 初始焦点要落在左侧导航，而不是搜索框。
        //
        // 系统按几何位置挑初始焦点：搜索框在顶栏（y 更小），导航项在侧栏（y 更大），
        // 所以搜索框总是先被选中。遥控器用户开机看到的却是「焦点在搜索框」，
        // 按 DOWN 没反应（单行输入框不换行），再按才莫名其妙跳到侧栏 —— 非常别扭。
        //
        // 这里延后几拍把焦点夺回导航栏；EditText 在布局过程中还会再次 requestFocus，
        // 所以要分几次试，并且只在焦点确实还停在搜索框时才抢，
        // 否则会把用户已经移走的焦点又拽回来。
        for(int i=0;i<4;i++)getWindow().getDecorView().postDelayed(()->{
            Button home=navigation.get("home");
            if(home==null)return;
            View current=getCurrentFocus();
            if(current==null||current==query)home.requestFocus();
        },220+i*260);
    }
}
