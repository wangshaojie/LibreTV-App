package com.wangshaojie.libretv;
import android.app.*;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.drawable.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.media3.common.*;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.ui.PlayerView;
import org.json.*;
import java.util.*;

// The playback screen is a quiet, full screen canvas. The video fills the window and every
// control (title, progress, actions) is an overlay that fades in on interaction and fades
// away again, so watching the picture is never interrupted by permanent chrome.
@androidx.media3.common.util.UnstableApi
public final class PlayerActivity extends Activity {
    private ExoPlayer player;private PlayerView video;private Store store;private List<Catalog.Item> items=new ArrayList<>();private int index;
    private FrameLayout root;private LinearLayout titleBar,panel;private TextView title,status,osd,clock;private SeekBar seek;private Button playButton,prevButton,nextButton;
    private long resume;private boolean autoplay=true;private boolean panelOn,titleOn,osdOn,locked,dragging;
    private float playbackSpeed=1f;
    // 手机端全屏态：横屏 + 收起控制面板与标题栏，只剩画面。
    // 电视端恒为 false —— 电视本来就是横屏全屏，没有切换的必要。
    private boolean fullscreen;
    // Live reconnect bookkeeping: -1 means "never tried", recoverCount is the number of
    // consecutive failed reconnects and is reset as soon as the stream plays again.
    private long lastRecoveryAt=-1L;private int recoverCount;private boolean reconnecting,connecting;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final Runnable hideTitleTask=()->{if(!panelOn)hideTitle();};
    private final Runnable hidePanelTask=()->hidePanel(360);
    private final Runnable hideOsdTask=()->{osdOn=false;Ui.fade(osd,0f,280,()->{if(!osdOn)osd.setVisibility(View.GONE);});};
    private final Runnable tickTask=new Runnable(){@Override public void run(){refreshProgress();ui.postDelayed(this,400);}};

    @Override public void onCreate(Bundle b){super.onCreate(b);store=new Store(this);if(Ui.tv(this))setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        try{String file=getIntent().getStringExtra("playlistFile");JSONArray a=new JSONArray(file==null?getIntent().getStringExtra("items"):Playback.read(this,file));for(int n=0;n<a.length();n++)items.add(Catalog.Item.from(a.getJSONObject(n)));index=Math.max(0,Math.min(items.size()-1,getIntent().getIntExtra("index",0)));if(items.isEmpty())throw new Exception();}catch(Exception e){finish();return;}
        resume=store.position(items.get(index).url);build();setContentView(root);immersive();
    }

    private void build(){
        // 播放页保持深色沉浸：根容器用深色版 inset（背景 DARK），视频区域仍是主体
        root=new FrameLayout(this);root.setFocusableInTouchMode(true);Ui.insetDark(this,root);
        // 视频要铺满整块屏幕，所以不把系统栏内边距加到根容器上（insetDark 默认会加），
        // 这里把内边距清零，只保留它的深色背景与沉浸式系统栏设置。
        root.setPadding(0,0,0,0);
        root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(0,0,0,0);return insets;});
        video=new PlayerView(this);video.setUseController(false);video.setBackgroundColor(Color.BLACK);video.setFocusable(false);
        // 全屏时点屏幕 = 退出全屏；平时点屏幕 = 呼出/收起控制面板。
        video.setOnTouchListener((v,e)->{
            if(e.getAction()==MotionEvent.ACTION_UP){
                if(fullscreen)exitFullscreen();else togglePanel();
            }
            return true;
        });
        root.addView(video,new FrameLayout.LayoutParams(-1,-1));

        osd=Ui.text(this,"",16);osd.setGravity(Gravity.CENTER);osd.setBackground(Ui.pill(this,0xD90B121C));osd.setTextColor(0xFFF2F4F3);osd.setVisibility(View.GONE);
        root.addView(osd,new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER));

        titleBar=new LinearLayout(this);titleBar.setOrientation(LinearLayout.HORIZONTAL);titleBar.setGravity(Gravity.CENTER_VERTICAL);
        titleBar.setBackground(Ui.fade(this,GradientDrawable.Orientation.TOP_BOTTOM,0xE6000000,0x00000000));
        titleBar.setPadding(Ui.dp(this,10),Ui.dp(this,6),Ui.dp(this,14),Ui.dp(this,6));
        // 与详情页同一套返回入口：左箭头回到详情页，房屋直接回首页。
        // 深底上不能套浅色主题的 Ui.icon（那是一块白方块），改用播放页私有的深底按钮。
        titleBar.addView(darkIcon("back","返回上一页",this::finish));
        titleBar.addView(darkIcon("home","回首页",this::goHome));
        title=Ui.text(this,"",14);title.setTextColor(0xE6F5F5F7);title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleLp=new LinearLayout.LayoutParams(0,-2,1);titleLp.setMargins(Ui.dp(this,8),0,0,0);titleBar.addView(title,titleLp);
        root.addView(titleBar,new FrameLayout.LayoutParams(-1,Ui.dp(this,60),Gravity.TOP));titleBar.setVisibility(View.GONE);

        panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);
        // 深色卡片：控制条整体是深底 + 浅字 + 青绿进度，不能用浅色主题的 card 样式
        panel.setBackground(darkCard());
        panel.setPadding(Ui.dp(this,18),Ui.dp(this,16),Ui.dp(this,18),Ui.dp(this,10));
        status=Ui.text(this,"",13);status.setTextColor(0xCCEDEFF1);status.setVisibility(View.GONE);panel.addView(status);
        LinearLayout bar=new LinearLayout(this);bar.setOrientation(LinearLayout.HORIZONTAL);bar.setGravity(Gravity.CENTER_VERTICAL);
        seek=new SeekBar(this);seek.setMax(1000);seek.setProgressDrawable(progressDrawable());seek.setThumb(thumbDrawable());seek.setKeyProgressIncrement(10000);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){@Override public void onProgressChanged(SeekBar sb,int value,boolean fromUser){if(fromUser)clock.setText(fmt(value)+" / "+fmt(sb.getMax()));}@Override public void onStartTrackingTouch(SeekBar sb){dragging=true;}@Override public void onStopTrackingTouch(SeekBar sb){dragging=false;if(player!=null)player.seekTo(sb.getProgress());}});
        bar.addView(seek,new LinearLayout.LayoutParams(0,Ui.dp(this,30),1));
        clock=Ui.text(this,"00:00 / 00:00",12);clock.setTextColor(0x99EDEFF1);bar.addView(clock);panel.addView(bar);
        HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout nav=new LinearLayout(this);nav.setOrientation(LinearLayout.HORIZONTAL);
        prevButton=action("",()->change(-1));nav.addView(prevButton);
        nav.addView(action("后退 10 秒",()->seekBy(-10000)));
        playButton=darkPrimary("暂停",this::togglePlay);nav.addView(playButton);
        nav.addView(action("前进 10 秒",()->seekBy(10000)));
        nextButton=action("",()->change(1));nav.addView(nextButton);
        View split=new View(this);split.setBackgroundColor(0x33FFFFFF);LinearLayout.LayoutParams splitLp=new LinearLayout.LayoutParams(Ui.dp(this,1),Ui.dp(this,28));splitLp.setMargins(Ui.dp(this,10),0,Ui.dp(this,10),0);nav.addView(split,splitLp);
        nav.addView(action("选集",this::pickEpisode));
        nav.addView(action("倍速",this::pickSpeed));
        nav.addView(action("画面比例",this::pickRatio));
        // 「全屏」取代原来的「横 / 竖屏」：
        // 进入全屏 = 切横屏 + 收起控制面板与标题栏，只留画面；
        // 再点一次屏幕退出全屏 = 切回竖屏 + 面板照旧。
        // 电视端本来就是横屏全屏，没有切换的必要，按钮隐藏。
        Button rotate=action("全屏",this::toggleFullscreen);
        if(Ui.tv(this))rotate.setVisibility(View.GONE);nav.addView(rotate);
        nav.addView(action("收藏",()->{store.toggleFavorite(items.get(index));Ui.toast(this,store.favorite(items.get(index))?"已收藏":"已取消收藏");}));
        nav.addView(action("换线路",this::finish));
        nav.addView(action("重试",this::prepare));
        scroll.addView(nav);panel.addView(scroll);
        root.addView(panel,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));panel.setVisibility(View.GONE);
    }

    private Button action(String label,Runnable run){Button b=darkButton(label,run);b.setTextSize(14);return b;}
    /**
     * 播放页私有的按钮样式：深底 + 浅字 + 半透明白描边。
     *
     * 不能直接套浅色主题的 Ui.focus()／Ui.button —— 那是白卡配深字，
     * 放在深色画面上是一块刺眼的白方块。焦点态改成青绿描边，
     * 遥控器移动焦点时依然看得出来停在哪。
     */
    private Button darkButton(String label,Runnable run){
        Button b=new Button(this);b.setAllCaps(false);b.setText(label);b.setTextSize(14);
        b.setBackground(darkState(0x14FFFFFF,0x59FFFFFF,0x2EFFFFFF,Ui.ACCENT));
        b.setTextColor(new android.content.res.ColorStateList(new int[][]{{android.R.attr.state_focused},{-android.R.attr.state_focused}},new int[]{0xFFFFFFFF,0xE6EDEFF1}));
        b.setMinHeight(Ui.dp(this,40));b.setPadding(Ui.dp(this,14),0,Ui.dp(this,14),0);
        b.setOnClickListener(v->run.run());
        if(android.os.Build.VERSION.SDK_INT>=21)b.setStateListAnimator(null);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,Ui.dp(this,40));lp.setMargins(Ui.dp(this,3),Ui.dp(this,3),Ui.dp(this,3),Ui.dp(this,3));b.setLayoutParams(lp);return b;
    }
    /** 深底描边图标按钮：白色线性图标 + 半透明白描边，聚焦时青绿描边。 */
    private Button darkIcon(String key,String label,Runnable run){
        Button b=new Button(this);b.setAllCaps(false);b.setText("");b.setMinWidth(Ui.dp(this,44));b.setMinHeight(Ui.dp(this,44));b.setPadding(Ui.dp(this,12),0,Ui.dp(this,12),0);
        final NavIcon icon=new NavIcon(key,Ui.dp(this,22),0xFFF2F4F3);icon.setBounds(0,0,Ui.dp(this,22),Ui.dp(this,22));
        b.setCompoundDrawables(icon,null,null,null);b.setGravity(Gravity.CENTER);
        b.setBackground(darkState(0x1FFFFFFF,0x66FFFFFF,0x33FFFFFF,Ui.ACCENT));
        b.setContentDescription(label);b.setFocusable(true);b.setOnClickListener(v->run.run());
        // 图标常态白、聚焦青绿（forced color 模式下 setColor 可直接改色）
        b.setOnFocusChangeListener((v,focused)->icon.setColor(focused?Ui.ACCENT_LIGHT:0xFFF2F4F3));
        if(android.os.Build.VERSION.SDK_INT>=21)b.setStateListAnimator(null);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(Ui.dp(this,44),Ui.dp(this,44));lp.setMargins(Ui.dp(this,3),Ui.dp(this,3),Ui.dp(this,3),Ui.dp(this,3));b.setLayoutParams(lp);return b;
    }
    /** 播放 / 暂停是控制条上最该被看见的按钮，用青绿实心 + 白字；焦点时加深。 */
    private Button darkPrimary(String label,Runnable run){
        Button b=new Button(this);b.setAllCaps(false);b.setText(label);b.setTextSize(14);b.setTypeface(null,android.graphics.Typeface.BOLD);
        android.graphics.drawable.StateListDrawable s=new android.graphics.drawable.StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused},round(Ui.ACCENT_DEEP,Ui.ACCENT_LIGHT,Ui.dp(this,12)));
        s.addState(new int[]{},round(Ui.ACCENT,0,Ui.dp(this,12)));
        b.setBackground(s);b.setTextColor(Ui.ON_ACCENT);
        b.setMinHeight(Ui.dp(this,40));b.setPadding(Ui.dp(this,18),0,Ui.dp(this,18),0);
        b.setOnClickListener(v->run.run());
        if(android.os.Build.VERSION.SDK_INT>=21)b.setStateListAnimator(null);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,Ui.dp(this,40));lp.setMargins(Ui.dp(this,3),Ui.dp(this,3),Ui.dp(this,3),Ui.dp(this,3));b.setLayoutParams(lp);return b;
    }
    /** 深色卡片：控制条底色，深灰蓝卡片压住画面下沿，上沿留一道淡白细线做层次。 */
    private Drawable darkCard(){GradientDrawable d=new GradientDrawable();d.setOrientation(GradientDrawable.Orientation.BOTTOM_TOP);d.setColors(new int[]{0xF223272E,0xE60F1216});d.setStroke(Math.max(2,Ui.dp(this,1)),0x1FFFFFFF);return d;}
    /** 深底按钮的背景：常态半透明填充 + 白描边，聚焦时填充加深 + 青绿描边。 */
    private android.graphics.drawable.Drawable darkState(int fill,int stroke,int focusFill,int focusStroke){
        android.graphics.drawable.StateListDrawable s=new android.graphics.drawable.StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused},round(focusFill,focusStroke,Ui.dp(this,12)));
        s.addState(new int[]{},round(fill,stroke,Ui.dp(this,12)));return s;
    }
    /** 回首页：复用栈里的主界面实例，不重建（重建会丢掉当前浏览位置） */
    private void goHome(){Intent intent=new Intent(this,MainActivity.class);intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);startActivity(intent);finish();}
    private Drawable progressDrawable(){LayerDrawable l=new LayerDrawable(new Drawable[]{round(0x33FFFFFF,Ui.dp(this,2)),round(Ui.ACCENT,Ui.dp(this,2))});l.setId(0,android.R.id.background);l.setId(1,android.R.id.progress);return l;}
    private Drawable round(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius);return d;}
    /** 深底按钮底：填充 + 描边 + 圆角。 */
    private Drawable round(int color,int stroke,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius);if(stroke!=0)d.setStroke(Math.max(2,Ui.dp(this,1)),stroke);return d;}
    // 进度条：青绿进度 + 半透明白轨道，滑块描边用半透明白（深底上黑描边会糊成一团）
    private Drawable thumbDrawable(){GradientDrawable d=new GradientDrawable();d.setShape(GradientDrawable.OVAL);d.setColor(Ui.ACCENT);d.setSize(Ui.dp(this,14),Ui.dp(this,14));d.setStroke(Ui.dp(this,2),0x99FFFFFF);return d;}

    // One player instance is reused for every episode, channel and route. Releasing the
    // player re-creates the vendor MediaCodec, and on older MStar TV firmware that teardown
    // aborts the process (ACodec.cpp CHECK_EQ on OMX_IndexParamAudioMp3), which leaves the
    // television's video path stuck and makes every later video play with sound but no picture.
    private ExoPlayer ensurePlayer(){
        if(player!=null)return player;
        player=new ExoPlayer.Builder(this,new DefaultRenderersFactory(this).setEnableDecoderFallback(true)).build();
        player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),true);player.setHandleAudioBecomingNoisy(true);
        player.addListener(new Player.Listener(){@Override public void onPlaybackStateChanged(int state){if(state==Player.STATE_READY){connecting=false;recoverCount=0;setStatus("方向键调节进度 · 上下键呼出控制 · 返回键退出");ui.removeCallbacks(hidePanelTask);ui.postDelayed(hidePanelTask,4200);}if(state==Player.STATE_ENDED&&index+1<items.size())change(1);}@Override public void onIsPlayingChanged(boolean playing){playButton.setText(playing?"暂停":"播放");}@Override public void onPlayerError(PlaybackException e){handleError(e);}});
        video.setPlayer(player);return player;
    }
    private void releasePlayer(){if(player!=null){player.release();player=null;}}
    private void prepare(){
        if(items.isEmpty())return;Catalog.Item item=items.get(index);title.setText(item.title+" · "+(index+1)+"/"+items.size());
        prevButton.setText(item.live?"上一台":"上一集");nextButton.setText(item.live?"下一台":"下一集");setStatus("正在连接…");
        Map<String,String> headers=new LinkedHashMap<>(item.headers);String ua=headers.remove("User-Agent");
        DefaultHttpDataSource.Factory http=new DefaultHttpDataSource.Factory().setUserAgent(ua==null?"LibreTV-App/1.0 Android":ua).setAllowCrossProtocolRedirects(true).setConnectTimeoutMs(12000).setReadTimeoutMs(20000).setDefaultRequestProperties(headers);
        MediaItem.Builder media=new MediaItem.Builder().setUri(item.url);String path=item.url.toLowerCase(Locale.ROOT).split("\\?")[0];if(path.endsWith(".m3u8"))media.setMimeType(MimeTypes.APPLICATION_M3U8);
        MediaSource source=new DefaultMediaSourceFactory(new DefaultDataSource.Factory(this,http)).createMediaSource(media.build());
        ExoPlayer p=ensurePlayer();p.setMediaSource(source);p.prepare();if(!item.live&&resume>0)p.seekTo(resume);p.setPlayWhenReady(autoplay);
        video.setKeepScreenOn(true);root.requestFocus();store.remember(item,resume);
        // While the source is still loading the picture is black, so the panel stays up with
        // the "connecting" hint instead of leaving the viewer in front of an empty screen.
        // It fades away on its own once playback starts.
        if(!reconnecting){connecting=true;showPanel(false);}
    }
    private boolean decodeFailure(PlaybackException e){int code=e.errorCode;return code==PlaybackException.ERROR_CODE_DECODING_FAILED||code==PlaybackException.ERROR_CODE_DECODER_INIT_FAILED||code==PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED||code==PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES;}
    private void handleError(PlaybackException e){
        int code=e.errorCode;
        android.util.Log.w("LibreTV-Player","Playback error "+e.getErrorCodeName()+" ("+code+") live="+live()+" recoveries="+recoverCount);
        // A live stream that falls behind the sliding live window is not a real failure: the
        // stream itself is fine, the player just has to join the live edge again. Reconnect
        // at most three times in a row and never faster than every 20 seconds, so a dead
        // channel can never turn into an endless reconnect loop.
        if(live()&&liveRecoverable(code)&&recoverCount<3&&(lastRecoveryAt<0||SystemClock.uptimeMillis()-lastRecoveryAt>20000)){
            lastRecoveryAt=SystemClock.uptimeMillis();recoverCount++;recoverLive();return;
        }
        if(decodeFailure(e))setStatus("本机硬件解码器无法处理这条视频（可能是清晰度过高或编码不兼容）。可按「重试」再试一次，或返回详情点「换线路」换一条线路 / 选择更低清晰度的源；若电视上所有视频都只有声音没有画面，需要重启电视。");
        else if(code==PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED)setStatus("当前地址未识别为视频，可能是网页播放线路。点「换线路」返回详情，选择 m3u8 / MP4 线路。");
        else if(liveRecoverable(code))setStatus("直播流落后于直播窗口，可按「重试」重新接入直播；也可以点「换线路」换一个频道。");
        else setStatus("播放失败："+e.getErrorCodeName()+"。可点「换线路」尝试其他线路，或按「重试」重新连接。");
        connecting=false;showPanel(true);
    }
    private boolean live(){return !items.isEmpty()&&items.get(index).live;}
    private static boolean liveRecoverable(int code){return code==PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW||code==PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED||code==PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT||code==PlaybackException.ERROR_CODE_IO_UNSPECIFIED||code==PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS;}
    // Reconnect to the live edge: the same code path as the retry button, so the player
    // instance is reused and the vendor decoder is never torn down. The title bar stays
    // hidden, a short hint in the middle of the picture is enough.
    private void recoverLive(){
        if(player==null||items.isEmpty())return;
        reconnecting=true;
        showOsd("正在重新接入直播…");
        prepare();
        reconnecting=false;
    }
    private void setStatus(String text){status.setText(text);status.setVisibility(text==null||text.isEmpty()?View.GONE:View.VISIBLE);}

    private void showTitle(){if(titleOn&&titleVisible())return;titleOn=true;titleBar.setAlpha(0f);titleBar.setVisibility(View.VISIBLE);Ui.fade(titleBar,1f,220,null);ui.removeCallbacks(hideTitleTask);ui.postDelayed(hideTitleTask,2800);}
    private void hideTitle(){if(!titleOn)return;titleOn=false;ui.removeCallbacks(hideTitleTask);Ui.fade(titleBar,0f,220,()->{if(titleOn)return;titleBar.setVisibility(View.GONE);titleBar.setAlpha(1f);});}
    private void showPanel(boolean lock){
        // 状态位与实际可见性必须一致：只要面板已经不可见了，就重新弹一次，
        // 否则一次失败的显示会让 panelOn 永远停在 true，之后所有按键都被挡住。
        if(panelOn&&panel.getVisibility()==View.VISIBLE&&panel.getAlpha()>0f){if(lock){locked=true;ui.removeCallbacks(hidePanelTask);}return;}
        panelOn=true;locked=lock;panel.setAlpha(0f);panel.setVisibility(View.VISIBLE);Ui.fade(panel,1f,220,null);
        showTitle();ui.removeCallbacks(tickTask);ui.postDelayed(tickTask,300);refreshProgress();
        ui.removeCallbacks(hidePanelTask);if(!locked&&!connecting)ui.postDelayed(hidePanelTask,4200);
        if(Ui.tv(this))playButton.requestFocus();
    }
    private void hidePanel(){hidePanel(220);}
    private void hidePanel(int duration){
        // 这里不能用 connecting 当拦截条件：直播源连不上时 connecting 会一直停在 true，
        // 面板就再也收不起来，返回键被吞掉，用户被困在播放页里。
        // 自动隐藏的时机本来就已经由 showPanel 里的「connecting 时不排 hidePanelTask」控制，
        // 这里只管「被显式要求收起」，一律照做。
        if(!panelOn)return;panelOn=false;locked=false;ui.removeCallbacks(hidePanelTask);ui.removeCallbacks(tickTask);
        Ui.fade(panel,0f,duration,()->{if(panelOn)return;panel.setVisibility(View.GONE);panel.setAlpha(1f);if(root.isShown())root.requestFocus();});
        hideTitle();
    }
    private void togglePanel(){if(panelVisible())hidePanel();else showPanel(false);}
    /**
     * 面板是否真的在屏幕上。
     *
     * 状态位（panelOn/titleOn）必须和实际可见性一致，否则一次失败的显示会让
     * panelOn 永远停在 true，之后所有按键都被挡住。
     * 判断用「可见 + 不透明」，不用 getWindowVisibility() —— 刚 setVisibility(VISIBLE)
     * 的那一帧窗口还没完成绘制，用窗口可见性会把正在显示的面板误判成隐藏。
     */
    private boolean panelVisible(){return panel.getVisibility()==View.VISIBLE&&panel.getAlpha()>0f;}
    /** 标题条是否真的在屏幕上：可见 + 不透明（不用 getWindowVisibility 那一套）。 */
    private boolean titleVisible(){return titleBar.getVisibility()==View.VISIBLE&&titleBar.getAlpha()>0f;}
    private void showOsd(String text){osdOn=true;osd.setText(text);osd.setVisibility(View.VISIBLE);osd.setAlpha(1f);ui.removeCallbacks(hideOsdTask);ui.postDelayed(hideOsdTask,1500);}
    private void refreshProgress(){
        if(player==null)return;long d=player.getDuration(),p=player.getCurrentPosition();
        if(!dragging){if(d>0){seek.setMax((int)Math.min(d,Integer.MAX_VALUE));seek.setProgress((int)Math.max(0,Math.min(p,d)));}clock.setText(fmt(p)+" / "+fmt(d));}
    }
    private void togglePlay(){if(player!=null)player.setPlayWhenReady(!player.getPlayWhenReady());}

    // ================= 手机端全屏 =================
    /**
     * 全屏 / 退出全屏。
     *
     * 全屏 = 横屏 + 收起控制面板与标题栏，画面铺满整屏，什么都不挡。
     * 退出全屏 = 切回竖屏 + 把面板照旧还回来（不重新呼出，让用户自己点）。
     *
     * Manifest 里声明了 configChanges 含 orientation|screenSize，
     * 所以切方向不会重建 Activity，播放器实例和播放进度都保留着。
     */
    private void toggleFullscreen(){
        if(Ui.tv(this))return;
        if(fullscreen){exitFullscreen();return;}
        fullscreen=true;
        // 先切横屏，等方向稳定后再收面板 —— 反过来的话旋转动画会带着面板一起闪
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        // 面板直接同步收起，不走淡出动画：
        // 旋转过程中动画帧会被打断，半透明的面板会残留在画面上，
        // 而且淡出回调里还有 requestFocus()，在方向切换的中间态调用容易出错。
        collapseChrome();
        showOsd("全屏 · 再点一次屏幕退出");
    }
    /** 退出全屏：切回竖屏，面板与标题栏恢复到退出前的常态。 */
    private void exitFullscreen(){
        if(!fullscreen)return;
        fullscreen=false;
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
        // 面板不主动呼出：用户退出全屏后画面干干净净，需要控制条时再点一下屏幕
        collapseChrome();
    }
    /** 同步收起控制面板与标题栏：直接改状态，不走动画，避免旋转时留下残影。 */
    private void collapseChrome(){
        ui.removeCallbacks(hidePanelTask);ui.removeCallbacks(hideTitleTask);
        panelOn=false;locked=false;
        panel.setVisibility(View.GONE);panel.setAlpha(1f);
        titleOn=false;
        titleBar.setVisibility(View.GONE);titleBar.setAlpha(1f);
    }
    /**
     * 切回竖屏时系统可能重建 Activity（某些 ROM 不吃 configChanges），
     * 或者用户自己旋转了手机 —— 这两种情况下全屏态必须跟着走，
     * 否则退出全屏后又停在横屏，跟按钮说的「退出全屏」对不上。
     */
    @Override public void onConfigurationChanged(android.content.res.Configuration config){
        super.onConfigurationChanged(config);
        if(Ui.tv(this))return;
        boolean landscape=config.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        // 用户手动转屏也算全屏：面板收起来，只留画面
        if(landscape&&!fullscreen){fullscreen=true;collapseChrome();}
        else if(!landscape&&fullscreen){fullscreen=false;collapseChrome();}
    }
    private void seekBy(long ms){if(player==null)return;long d=player.getDuration(),p=player.getCurrentPosition();long t=Math.max(0,p+ms);if(d>0)t=Math.min(t,d);player.seekTo(t);showOsd((ms>0?"快进 ":"快退 ")+(Math.abs(ms)/1000)+" 秒 · "+fmt(t));}
    private static String fmt(long ms){if(ms<0)return "--:--";long s=ms/1000;long h=s/3600,m=(s/60)%60,x=s%60;return h>0?String.format(Locale.getDefault(),"%d:%02d:%02d",h,m,x):String.format(Locale.getDefault(),"%02d:%02d",m,x);}

    // 选集 / 倍速 / 画面比例都用自绘弹框：系统 AlertDialog 是 Material 蓝白样式，
    // 和播放页的深色沉浸完全是两套东西，焦点反馈也不统一。
    private void pickEpisode(){String[] labels=new String[items.size()];for(int n=0;n<labels.length;n++)labels[n]=items.get(n).title;UiDialog.list(this,"选集",labels,index,n->{save();index=n;resume=store.position(items.get(index).url);prepare();});}
    // 当前倍速自己记一份：media3 这个版本的 ExoPlayer 没有 getPlaybackSpeed()，读不到当前值
    private void pickSpeed(){final float[] speeds={.75f,1f,1.25f,1.5f,2f};String[] labels={"0.75×","1.0×","1.25×","1.5×","2.0×"};int checked=1;for(int n=0;n<speeds.length;n++)if(Math.abs(playbackSpeed-speeds[n])<0.01f)checked=n;UiDialog.list(this,"播放速度",labels,checked,n->{playbackSpeed=speeds[n];if(player!=null)player.setPlaybackSpeed(playbackSpeed);});}
    private void pickRatio(){String[] labels={"适应屏幕","填满屏幕","裁切放大"};UiDialog.list(this,"画面显示",labels,-1,n->video.setResizeMode(new int[]{androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT,androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL,androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM}[n]));}
    private void save(){if(player!=null&&!items.isEmpty()){resume=player.getCurrentPosition();autoplay=player.getPlayWhenReady();store.remember(items.get(index),resume);}}
    private void change(int delta){int next=index+delta;if(next<0||next>=items.size()){Ui.toast(this,"没有更多剧集");return;}save();index=next;resume=store.position(items.get(index).url);autoplay=true;prepare();}
    // Hiding the system bars in an immersive way makes Android 11 and newer raise the
    // "immersive mode confirmation" overlay, which takes the input focus and swallows the
    // next key press. Televisions keep the immersive flags, where no such overlay exists,
    // while phones only hide the status bar and stay edge to edge.
    private void immersive(){
        if(Ui.tv(this))getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        else getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }
    @Override public void onWindowFocusChanged(boolean has){super.onWindowFocusChanged(has);if(has)immersive();}
    @Override protected void onStart(){super.onStart();if(!items.isEmpty())prepare();}
    @Override protected void onStop(){save();releasePlayer();if(video!=null)video.setKeepScreenOn(false);super.onStop();}
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        if(mediaKey(event))return true;
        // 用实际可见性判断，而不是只看 panelOn：
        // 面板若因动画没跑完而停在不可见状态，按键必须仍然能把它唤出来。
        boolean shown=panelVisible();
        if(!shown&&event.getAction()==KeyEvent.ACTION_DOWN){switch(event.getKeyCode()){
            case KeyEvent.KEYCODE_DPAD_CENTER:case KeyEvent.KEYCODE_ENTER:case KeyEvent.KEYCODE_SPACE:togglePlay();return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:seekBy(-10000);return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:seekBy(10000);return true;
            case KeyEvent.KEYCODE_DPAD_UP:case KeyEvent.KEYCODE_DPAD_DOWN:case KeyEvent.KEYCODE_MENU:showPanel(false);return true;
        }}
        return super.dispatchKeyEvent(event);
    }
    // PlayerView only forwards media keys to the player while its built in controller is
    // enabled. That controller is off here so the immersive screen stays free of chrome, and
    // the same key handling is done by hand instead.
    private boolean mediaKey(KeyEvent event){
        if(player==null)return false;int code=event.getKeyCode();
        if(event.getAction()!=KeyEvent.ACTION_DOWN)return isMediaKey(code);
        if(code==KeyEvent.KEYCODE_MEDIA_FAST_FORWARD){player.seekForward();return true;}
        if(code==KeyEvent.KEYCODE_MEDIA_REWIND){player.seekBack();return true;}
        if(event.getRepeatCount()!=0)return true;
        switch(code){
            case KeyEvent.KEYCODE_MUSIC:case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:togglePlay();return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY:player.play();return true;
            case KeyEvent.KEYCODE_MEDIA_PAUSE:player.pause();return true;
            case KeyEvent.KEYCODE_MEDIA_NEXT:if(player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT))player.seekToNext();return true;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:if(player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS))player.seekToPrevious();return true;
        }
        return false;
    }
    private static boolean isMediaKey(int code){return code==KeyEvent.KEYCODE_MUSIC||code==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE||code==KeyEvent.KEYCODE_MEDIA_PLAY||code==KeyEvent.KEYCODE_MEDIA_PAUSE||code==KeyEvent.KEYCODE_MEDIA_NEXT||code==KeyEvent.KEYCODE_MEDIA_PREVIOUS||code==KeyEvent.KEYCODE_MEDIA_FAST_FORWARD||code==KeyEvent.KEYCODE_MEDIA_REWIND;}
    @Override public boolean onKeyDown(int key,KeyEvent event){
        if(!items.isEmpty()&&items.get(index).live&&(key==KeyEvent.KEYCODE_CHANNEL_UP||key==KeyEvent.KEYCODE_CHANNEL_DOWN)){change(key==KeyEvent.KEYCODE_CHANNEL_UP?1:-1);return true;}
        if(panelVisible()&&key==KeyEvent.KEYCODE_BACK)return true;
        return super.onKeyDown(key,event);
    }
    /**
     * 返回键：全屏时先退全屏，再收起控制面板，最后才退出播放页。
     *
     * 面板收起不能被 connecting 拦住 —— 直播源连不上时 connecting 一直是 true，
     * 面板就永远收不起来，返回键被吞掉，用户会被困在播放页里出不来。
     * 所以这里用「面板是否真的可见」判断，connecting 不参与。
     */
    @Override public boolean onKeyUp(int key,KeyEvent event){if(key==KeyEvent.KEYCODE_BACK){if(fullscreen){exitFullscreen();return true;}if(panelVisible()){hidePanel();return true;}finish();return true;}return super.onKeyUp(key,event);}
    @Override protected void onDestroy(){String file=getIntent().getStringExtra("playlistFile");if(isFinishing()&&file!=null&&file.startsWith("play-")&&!file.contains("/"))new java.io.File(getCacheDir(),file).delete();super.onDestroy();}
}
