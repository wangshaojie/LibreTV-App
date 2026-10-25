package com.wangshaojie.libretv;

import android.app.*;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

@androidx.media3.common.util.UnstableApi
public final class DetailActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private Catalog.Item item;private LinearLayout body,episodeBox,sourceBox,sourceRow;private TextView state,sourceLine;private Button favorite;
    private final List<Catalog.Item> choices=new ArrayList<>();private int active=-1;
    private Store store;private List<Catalog.Item> selected=new ArrayList<>();
    @Override public void onCreate(Bundle saved){super.onCreate(saved);store=new Store(this);if(Ui.tv(this))setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        try{item=Catalog.Item.from(new JSONObject(getIntent().getStringExtra("item")));}catch(Exception e){finish();return;}
        try{JSONArray array=new JSONArray(getIntent().getStringExtra("choices"));for(int n=0;n<array.length();n++)if(array.optJSONObject(n)!=null)choices.add(Catalog.Item.from(array.optJSONObject(n)));}catch(Exception ignored){}
        active=match(item);
        if(active>=0)item=choices.get(active);
        LinearLayout root=Ui.column(this);Ui.inset(this,root);LinearLayout top=Ui.row(this);top.addView(Ui.button(this,"返回",this::finish));TextView title=Ui.heading(this,"影片详情");top.addView(title);root.addView(top);
        body=Ui.column(this);Ui.padding(body,Ui.tv(this)?28:16);root.addView(Ui.scroll(this,body),new LinearLayout.LayoutParams(-1,0,1));setContentView(root);renderHeader(item);
        if(choices.size()>1)renderSources();
        state=Ui.label(this,"正在加载线路与剧集…",14,Ui.MUTED);body.addView(state);episodeBox=Ui.column(this);body.addView(episodeBox);load();
    }
    /** 在候选片源里找出与给定条目同源的序号；找不到返回 -1。 */
    private int match(Catalog.Item target){for(int n=0;n<choices.size();n++){Catalog.Item c=choices.get(n);if(c.source.equals(target.source)&&c.vodId.equals(target.vodId))return n;}return -1;}
    private void renderHeader(Catalog.Item film){
        LinearLayout header=Ui.row(this);ImageView poster=new ImageView(this);poster.setScaleType(ImageView.ScaleType.CENTER_CROP);poster.setBackground(Ui.shape(Ui.SURFACE,0));header.addView(poster,new LinearLayout.LayoutParams(Ui.dp(this,Ui.tv(this)?150:104),Ui.dp(this,Ui.tv(this)?220:156)));Images.load(this,film.poster,poster);
        LinearLayout words=Ui.column(this);Ui.padding(words,16);TextView title=Ui.heading(this,film.title);words.addView(title);words.addView(Ui.gap(this,10));words.addView(Ui.label(this,film.year+"  "+film.category,14,Ui.MUTED));words.addView(Ui.gap(this,10));sourceLine=Ui.label(this,film.sourceName+" · "+film.remarks,13,Ui.ACCENT);words.addView(sourceLine);favorite=Ui.button(this,store.favorite(item)?"已收藏":"加入收藏",()->{});favorite.setOnClickListener(v->{store.toggleFavorite(item);favorite.setText(store.favorite(item)?"已收藏":"加入收藏");Ui.toast(this,store.favorite(item)?"已加入收藏":"已取消收藏");});words.addView(favorite);header.addView(words,new LinearLayout.LayoutParams(0,-2,1));body.addView(header);body.addView(Ui.gap(this,18));
        if(!film.summary.isEmpty()){TextView synopsis=Ui.label(this,android.text.Html.fromHtml(film.summary).toString().trim(),14,Ui.MUTED);synopsis.setMaxLines(6);synopsis.setLineSpacing(Ui.dp(this,4),1);body.addView(synopsis);body.addView(Ui.gap(this,20));}
    }
    /** 详情页内直接列出全部片源，当前所在片源高亮，点选即切换。 */
    private void renderSources(){
        sourceBox=Ui.column(this);sourceBox.addView(Ui.label(this,"片源 · "+choices.size()+" 个可用",13,Ui.MUTED));sourceBox.addView(Ui.gap(this,8));
        LinearLayout row=Ui.row(this);sourceRow=row;for(int n=0;n<choices.size();n++){final int index=n;Catalog.Item choice=choices.get(n);Button chip=Ui.button(this,choice.sourceName.isEmpty()?"片源 "+(index+1):choice.sourceName,()->select(index));chip.setSelected(index==active);row.addView(chip);}
        HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setHorizontalScrollBarEnabled(false);scroll.addView(row);sourceBox.addView(scroll);sourceBox.addView(Ui.gap(this,18));body.addView(sourceBox);
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
    private void load(){episodeBox.removeAllViews();selected=new ArrayList<>();state.setText("正在加载线路与剧集…");state.setTextColor(Ui.MUTED);worker.execute(()->{try{
        String json=Net.get(MainActivity.cmsUrl(item.source,"videolist","ids",item.vodId));LinkedHashMap<String,List<Catalog.Item>> routes=Catalog.episodes(json);
        runOnUiThread(()->{if(isFinishing()||isDestroyed())return;state.setText("选择线路与剧集");state.setTextColor(Ui.TEXT);String[] names=routes.keySet().toArray(new String[0]);String[] labels=new String[names.length];for(int n=0;n<names.length;n++)labels[n]=names[n]+(Catalog.directRoute(routes.get(names[n]))?" · 视频直连":" · 其他地址");Spinner picker=new Spinner(this);picker.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels));episodeBox.addView(picker);LinearLayout episodes=Ui.column(this);episodeBox.addView(episodes);
            picker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int at,long id){selected=routes.get(names[at]);renderEpisodes(episodes,60);}});
        });
    }catch(Exception e){runOnUiThread(()->{if(isFinishing()||isDestroyed())return;state.setText("暂未获取到可播放线路："+e.getMessage());episodeBox.addView(Ui.button(this,"重新加载",this::load));});}});}
    private void play(int at){for(Catalog.Item episode:selected){episode.source=item.source;episode.sourceName=item.sourceName;episode.poster=item.poster;}Playback.open(this,selected,at);}
    private void renderEpisodes(LinearLayout episodes,int limit){episodes.removeAllViews();int resumeAt=0;outer:for(Catalog.Item recent:store.items("history"))for(int n=0;n<selected.size();n++)if(selected.get(n).url.equals(recent.url)){resumeAt=n;break outer;}final int resumeIndex=resumeAt;episodes.addView(Ui.primary(this,store.position(selected.get(resumeAt).url)>0?"继续观看 · "+(resumeAt+1):"播放第一集",()->play(resumeIndex)));int columns=Ui.tv(this)?8:4;LinearLayout row=null;int shown=Math.min(limit,selected.size());for(int n=0;n<shown;n++){if(n%columns==0){row=Ui.row(this);episodes.addView(row);}final int index=n;String name=selected.get(n).title;int split=name.indexOf(" · ");if(split>=0)name=name.substring(split+3);Button b=Ui.button(this,name,()->play(index));b.setSingleLine(true);b.setSelected(n==resumeAt&&store.position(selected.get(n).url)>0);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,Ui.dp(this,48),1);lp.setMargins(Ui.dp(this,3),Ui.dp(this,3),Ui.dp(this,3),Ui.dp(this,3));row.addView(b,lp);}if(row!=null&&shown%columns!=0)for(int n=shown%columns;n<columns;n++)row.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));if(shown<selected.size())episodes.addView(Ui.button(this,"显示更多剧集（"+shown+" / "+selected.size()+"）",()->renderEpisodes(episodes,limit+60)));}
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
