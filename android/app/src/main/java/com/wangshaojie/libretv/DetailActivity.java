package com.wangshaojie.libretv;

import android.app.*;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.os.*;
import android.text.StaticLayout;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * 影片详情页 —— 晨雾白浅色主题。
 *
 * 层次靠「白卡 + 极淡描边 + 轻投影」拉开，不靠深浅底色；
 * 强调色只出现在主按钮、当前集和评分上。
 */
@androidx.media3.common.util.UnstableApi
public final class DetailActivity extends Activity {
    // 切源时新请求要能立刻发出去，不能排在旧请求后面等 ——
    // 旧请求即使回来也会被 generation 判定为过期丢弃，所以并发是安全的。
    private final ExecutorService worker=Executors.newFixedThreadPool(3);
    private Catalog.Item item;private LinearLayout body,episodeBox,sourceBox,sourceRow;private TextView state,sourceLine;private Button favorite;
    private ScrollView scroll;private int savedScroll;
    private final List<Catalog.Item> choices=new ArrayList<>();private int active=-1;
    private Store store;private List<Catalog.Item> selected=new ArrayList<>();
    /** 请求代次：切源时递增，旧请求的结果回来后直接丢弃，避免覆盖新源的线路 */
    private int generation=0;
    @Override public void onCreate(Bundle saved){super.onCreate(saved);store=new Store(this);if(Ui.tv(this))setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        try{item=Catalog.Item.from(new JSONObject(getIntent().getStringExtra("item")));}catch(Exception e){finish();return;}
        try{JSONArray array=new JSONArray(getIntent().getStringExtra("choices"));for(int n=0;n<array.length();n++)if(array.optJSONObject(n)!=null)choices.add(Catalog.Item.from(array.optJSONObject(n)));}catch(Exception ignored){}
        active=match(item);
        if(active>=0)item=choices.get(active);
        LinearLayout root=Ui.column(this);Ui.inset(this,root);
        // 顶栏：返回 + 回首页两个图标按钮，白卡 + 淡描边（浅色样式，不是深底样式）
        LinearLayout top=Ui.topBar(this,"影片详情",this::finish,this::goHome);
        root.addView(top);
        body=Ui.column(this);Ui.padding(body,Ui.tv(this)?20:12);
        scroll=Ui.scroll(this,body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);renderHeader(item);
        if(choices.size()>1)renderSources();
        LinearLayout epCard=Ui.panel(this);
        state=Ui.label(this,"正在加载线路与剧集…",14,Ui.MUTED);epCard.addView(state);
        episodeBox=Ui.column(this);epCard.addView(episodeBox);body.addView(epCard);load();
    }
    /** 在候选片源里找出与给定条目同源的序号；找不到返回 -1。 */
    private int match(Catalog.Item target){for(int n=0;n<choices.size();n++){Catalog.Item c=choices.get(n);if(c.source.equals(target.source)&&c.vodId.equals(target.vodId))return n;}return -1;}
    private void renderHeader(Catalog.Item film){
        LinearLayout card=Ui.panel(this);
        LinearLayout header=Ui.row(this);
        // 封面：浅米灰底 + 淡描边，评分徽章叠在右上角
        FrameLayout art=new FrameLayout(this);art.setBackground(Ui.shape(Ui.CHIP,Ui.LINE));
        ImageView poster=new ImageView(this);poster.setScaleType(ImageView.ScaleType.CENTER_CROP);art.addView(poster,new FrameLayout.LayoutParams(-1,-1));Images.load(this,film.poster,poster);
        // 评分徽章：白底胶囊 + 深琥珀字。浅色底上再用黑底压边会变成一块脏斑。
        if(!film.rating.isEmpty()){TextView badge=Ui.label(this,film.rating,12,Ui.RATING);badge.setTypeface(null,android.graphics.Typeface.BOLD);badge.setBackground(Ui.pill(this,Ui.CARD));badge.setPadding(Ui.dp(this,8),Ui.dp(this,3),Ui.dp(this,8),Ui.dp(this,3));FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.RIGHT);bp.setMargins(0,Ui.dp(this,5),Ui.dp(this,5),0);art.addView(badge,bp);}
        header.addView(art,new LinearLayout.LayoutParams(Ui.dp(this,Ui.tv(this)?150:104),Ui.dp(this,Ui.tv(this)?220:156)));
        LinearLayout words=Ui.column(this);
        TextView title=Ui.heading(this,film.title);words.addView(title);words.addView(Ui.gap(this,8));
        // 元信息做成 pill 标签，和桌面端详情弹窗一致
        LinearLayout tags=Ui.row(this);tags.setGravity(Gravity.START);
        if(!film.category.isEmpty())tags.addView(Ui.tagPair(this,"类型",film.category));
        if(!film.year.isEmpty())tags.addView(Ui.tagPair(this,"年份",film.year));
        if(tags.getChildCount()>0){words.addView(tags);words.addView(Ui.gap(this,10));}
        sourceLine=Ui.label(this,film.sourceName+" · "+film.remarks,13,Ui.ACCENT_DEEP);words.addView(sourceLine);words.addView(Ui.gap(this,14));
        LinearLayout actions=Ui.row(this);
        Button play=Ui.primary(this,"立即播放",()->{if(selected.isEmpty())Ui.toast(this,"剧集正在加载，请稍候");else play(0);});actions.addView(play);
        favorite=Ui.outline(this,store.favorite(item)?"已收藏":"加入收藏",()->{});favorite.setOnClickListener(v->{store.toggleFavorite(item);favorite.setText(store.favorite(item)?"已收藏":"加入收藏");Ui.toast(this,store.favorite(item)?"已加入收藏":"已取消收藏");});actions.addView(favorite);
        words.addView(actions);
        header.addView(words,new LinearLayout.LayoutParams(0,-2,1));
        card.addView(header);body.addView(card);body.addView(Ui.gap(this,14));
        if(!film.summary.isEmpty())renderSummary(film.summary);
    }
    /** 简介：默认最多 3 行，超出时出现「展开 / 收起」按钮。 */
    private void renderSummary(String summary){
        LinearLayout card=Ui.panel(this);
        card.addView(Ui.label(this,"简介",13,Ui.FAINT));card.addView(Ui.gap(this,6));
        TextView synopsis=Ui.label(this,android.text.Html.fromHtml(summary).toString().trim(),14,Ui.MUTED);synopsis.setMaxLines(3);synopsis.setLineSpacing(Ui.dp(this,4),1);
        // 宽度必须撑满：宽度不定的话 StaticLayout 量出来永远只有一行，展开按钮永远不出现
        card.addView(synopsis,new LinearLayout.LayoutParams(-1,-2));
        Button toggle=Ui.soft(this,"展开全文",()->{});toggle.setVisibility(View.GONE);card.addView(toggle);card.addView(Ui.gap(this,2));
        // 只有真正溢出 3 行时才显示切换按钮，否则会出现一个点了没反应的「展开全文」
        synopsis.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,orr,ob)->toggle.setVisibility(linesOf(synopsis)>3?View.VISIBLE:View.GONE));
        synopsis.post(()->toggle.setVisibility(linesOf(synopsis)>3?View.VISIBLE:View.GONE));
        toggle.setOnClickListener(v->{boolean expanded=synopsis.getMaxLines()!=3;synopsis.setMaxLines(expanded?3:Integer.MAX_VALUE);toggle.setText(expanded?"展开全文":"收起");});
        body.addView(card);body.addView(Ui.gap(this,14));
    }
    /** 按当前宽度算出的实际行数（maxLines 只影响绘制，必须单独量一次才知道有没有溢出）。 */
    private static int linesOf(TextView v){if(v.getWidth()<=0)return 0;CharSequence s=v.getText();if(s==null||s.length()==0)return 0;StaticLayout layout=new StaticLayout(s,v.getPaint(),v.getWidth()-v.getPaddingLeft()-v.getPaddingRight(),android.text.Layout.Alignment.ALIGN_NORMAL,1f,0f,false);return layout.getLineCount();}
    /** 详情页内直接列出全部片源，当前所在片源高亮，点选即切换。 */
    private void renderSources(){
        sourceBox=Ui.panel(this);sourceBox.addView(Ui.label(this,"片源 · "+choices.size()+" 个可用",13,Ui.MUTED));sourceBox.addView(Ui.gap(this,8));
        LinearLayout row=Ui.row(this);sourceRow=row;for(int n=0;n<choices.size();n++){final int index=n;Catalog.Item choice=choices.get(n);Button chip=Ui.chipOutline(this,choice.sourceName.isEmpty()?"片源 "+(index+1):choice.sourceName,index==active,()->select(index));row.addView(chip);}
        HorizontalScrollView hscroll=new HorizontalScrollView(this);hscroll.setHorizontalScrollBarEnabled(false);hscroll.setClipToPadding(false);hscroll.addView(row);sourceBox.addView(hscroll);body.addView(sourceBox);body.addView(Ui.gap(this,14));
    }
    /** 切换片源：更新标题行、收藏状态与片源高亮，然后重新加载该源的线路与剧集。 */
    private void select(int index){
        if(index<0||index>=choices.size()||index==active)return;
        active=index;item=choices.get(index);
        if(sourceLine!=null)sourceLine.setText(item.sourceName+" · "+item.remarks);
        if(favorite!=null)favorite.setText(store.favorite(item)?"已收藏":"加入收藏");
        if(sourceRow!=null)for(int n=0;n<sourceRow.getChildCount();n++)if(sourceRow.getChildAt(n) instanceof Button)((Button)sourceRow.getChildAt(n)).setSelected(n==active);
        load();
    }
    private void load(){
        // 每次切源都开一个新请求，旧请求回来后必须作废 ——
        // 否则慢的旧源会把新源的线路和剧集覆盖掉，用户看到的就是「切了源但线路没变」。
        final int token=++generation;final Catalog.Item target=item;
        episodeBox.removeAllViews();selected=new ArrayList<>();state.setText("正在加载线路与剧集…");state.setTextColor(Ui.MUTED);worker.execute(()->{try{
        String json=Net.get(MainActivity.cmsUrl(target.source,"videolist","ids",target.vodId));LinkedHashMap<String,List<Catalog.Item>> routes=Catalog.episodes(json);
        runOnUiThread(()->{if(token!=generation||isFinishing()||isDestroyed())return;state.setText("选择线路与剧集");state.setTextColor(Ui.TEXT);String[] names=routes.keySet().toArray(new String[0]);
            // 线路选择用胶囊行，不用系统 Spinner：系统下拉是默认蓝白样式，与品牌脱节，遥控器操作也不顺手
            if(names.length>0)selected=routes.get(names[0]);
            // 剧集容器要先建好：线路选择器里的按钮要往它里面重画剧集，
            // 如果传 episodeBox 过去，renderEpisodes 会把整个 episodeBox 清空，连线路选择器自己一起抹掉。
            final LinearLayout episodes=Ui.column(this);
            if(names.length>1){LinearLayout pickerRow=Ui.row(this);pickerRow.setGravity(Gravity.START);for(int n=0;n<names.length;n++){final int index=n;String label=names[n]+(Catalog.directRoute(routes.get(names[n]))?" · 直连":"");pickerRow.addView(Ui.chipOutline(this,label,n==0,()->{for(int k=0;k<pickerRow.getChildCount();k++)if(pickerRow.getChildAt(k) instanceof Button)((Button)pickerRow.getChildAt(k)).setSelected(k==index);selected=routes.get(names[index]);renderEpisodes(episodes,60);}));}HorizontalScrollView picker=new HorizontalScrollView(this);picker.setHorizontalScrollBarEnabled(false);picker.setClipToPadding(false);picker.addView(pickerRow);episodeBox.addView(picker);episodeBox.addView(Ui.gap(this,12));}
            episodeBox.addView(episodes);
            renderEpisodes(episodes,60);
        });
    }catch(Exception e){runOnUiThread(()->{if(token!=generation||isFinishing()||isDestroyed())return;state.setText("暂未获取到可播放线路："+e.getMessage());episodeBox.addView(Ui.outline(this,"重新加载",this::load));});}});}
    private void play(int at){for(Catalog.Item episode:selected){episode.source=item.source;episode.sourceName=item.sourceName;episode.poster=item.poster;}Playback.open(this,selected,at);}
    private void renderEpisodes(LinearLayout episodes,int limit){
        int keep=scroll!=null?scroll.getScrollY():0;
        episodes.removeAllViews();if(selected.isEmpty())return;
        // 当前集判定：有观看进度就落在续播的那一集；完全没看过的新片落在第 1 集。
        // 当前集始终高亮，否则用户既不知道现在看的是哪一集，也分不清按钮能不能点。
        int resumeAt=0;boolean resumed=false,matched=false;
        outer:for(Catalog.Item recent:store.items("history")){for(int n=0;n<selected.size();n++)if(selected.get(n).url.equals(recent.url)){if(!matched){resumeAt=n;matched=true;}if(store.position(recent.url)>0){resumeAt=n;resumed=true;break outer;}}}
        final int resumeIndex=resumeAt;
        // 顶部已有「立即播放」，这里只在有观看进度时给出续播入口，避免两个主按钮重复
        if(resumed)episodes.addView(Ui.primary(this,"继续观看 · "+(resumeAt+1),()->play(resumeIndex)));
        episodes.addView(Ui.gap(this,12));
        int columns=Ui.tv(this)?8:4;LinearLayout row=null;int shown=Math.min(limit,selected.size());
        for(int n=0;n<shown;n++){if(n%columns==0){row=Ui.row(this);row.setGravity(Gravity.START);episodes.addView(row);}final int index=n;String name=selected.get(n).title;int split=name.indexOf(" · ");if(split>=0)name=name.substring(split+3);
            // 当前集始终高亮：没看过的新片就是第 1 集，看过则高亮到续播的那一集。
            // 之前只在「有观看进度」时才高亮，结果新片打开时一集都不选中，
            // 用户既不知道当前是哪一集，也分不清按钮到底能不能点。
            Button b=Ui.chipSolid(this,name,n==resumeAt,()->play(index));b.setSingleLine(true);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,Ui.dp(this,48),1);lp.setMargins(Ui.dp(this,3),Ui.dp(this,3),Ui.dp(this,3),Ui.dp(this,3));row.addView(b,lp);}
        if(row!=null&&shown%columns!=0)for(int n=shown%columns;n<columns;n++)row.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
        if(shown<selected.size())episodes.addView(Ui.outline(this,"显示更多剧集（"+shown+" / "+selected.size()+"）",()->renderEpisodes(episodes,limit+60)));
        // 重建剧集区后把滚动位置放回去，展开更多剧集时页面不会跳回顶部
        if(scroll!=null)scroll.post(()->scroll.scrollTo(0,keep));
    }
    /** 回首页：与播放页一致，复用栈里的主界面实例，不重建（重建会丢掉当前浏览位置） */
    private void goHome(){Intent intent=new Intent(this,MainActivity.class);intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);startActivity(intent);finish();}
    // 从播放页返回时保留滚动位置，别把用户丢回详情页顶部
    @Override protected void onPause(){if(scroll!=null)savedScroll=scroll.getScrollY();super.onPause();}
    @Override protected void onResume(){super.onResume();restoreScroll();}
    /**
     * 恢复滚动位置。
     *
     * 必须在内容测量完成之后才能 scrollTo：内容还没排版时 ScrollView 的可滚动高度是 0，
     * 直接 scrollTo 会被夹回 0，位置就丢了。这里等一次全局布局再恢复。
     */
    private void restoreScroll(){
        if(scroll==null||savedScroll<=0)return;
        final android.view.ViewTreeObserver observer=scroll.getViewTreeObserver();
        if(!observer.isAlive())return;
        observer.addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener(){
            @Override public void onGlobalLayout(){android.view.ViewTreeObserver v=scroll.getViewTreeObserver();if(v.isAlive())v.removeOnGlobalLayoutListener(this);if(scroll!=null)scroll.scrollTo(0,savedScroll);}
        });
    }
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
