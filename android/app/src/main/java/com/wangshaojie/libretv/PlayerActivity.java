package com.wangshaojie.libretv;
import android.app.*;
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
    // Live reconnect bookkeeping: -1 means "never tried", recoverCount is the number of
    // consecutive failed reconnects and is reset as soon as the stream plays again.
    private long lastRecoveryAt=-1L;private int recoverCount;private boolean reconnecting,connecting;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final Runnable hideTitleTask=()->{if(!panelOn)hideTitle();};
    private final Runnable hidePanelTask=()->hidePanel(360);
    private final Runnable hideOsdTask=()->{osdOn=false;osd.animate().alpha(0f).setDuration(280).withEndAction(()->{if(!osdOn)osd.setVisibility(View.GONE);}).start();};
    private final Runnable tickTask=new Runnable(){@Override public void run(){refreshProgress();ui.postDelayed(this,400);}};

    @Override public void onCreate(Bundle b){super.onCreate(b);store=new Store(this);if(Ui.tv(this))setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        try{String file=getIntent().getStringExtra("playlistFile");JSONArray a=new JSONArray(file==null?getIntent().getStringExtra("items"):Playback.read(this,file));for(int n=0;n<a.length();n++)items.add(Catalog.Item.from(a.getJSONObject(n)));index=Math.max(0,Math.min(items.size()-1,getIntent().getIntExtra("index",0)));if(items.isEmpty())throw new Exception();}catch(Exception e){finish();return;}
        resume=store.position(items.get(index).url);build();setContentView(root);immersive();
    }

    private void build(){
        root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);root.setFocusableInTouchMode(true);
        video=new PlayerView(this);video.setUseController(false);video.setBackgroundColor(Color.BLACK);video.setFocusable(false);
        video.setOnTouchListener((v,e)->{if(e.getAction()==MotionEvent.ACTION_UP)togglePanel();return true;});
        root.addView(video,new FrameLayout.LayoutParams(-1,-1));

        osd=Ui.text(this,"",16);osd.setGravity(Gravity.CENTER);osd.setBackground(Ui.pill(this,0xD90B121C));osd.setVisibility(View.GONE);
        root.addView(osd,new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER));

        titleBar=new LinearLayout(this);titleBar.setOrientation(LinearLayout.HORIZONTAL);titleBar.setGravity(Gravity.CENTER_VERTICAL);
        titleBar.setBackground(Ui.fade(this,GradientDrawable.Orientation.TOP_BOTTOM,0xE6000000,0x00000000));
        titleBar.setPadding(Ui.dp(this,18),0,Ui.dp(this,18),0);
        title=Ui.text(this,"",14);title.setTextColor(0xD9FFFFFF);title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titleBar.addView(title);
        root.addView(titleBar,new FrameLayout.LayoutParams(-1,Ui.dp(this,60),Gravity.TOP));titleBar.setVisibility(View.GONE);

        panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(Ui.fade(this,GradientDrawable.Orientation.BOTTOM_TOP,0xF2000000,0x00000000));
        panel.setPadding(Ui.dp(this,18),Ui.dp(this,16),Ui.dp(this,18),Ui.dp(this,10));
        status=Ui.text(this,"",13);status.setTextColor(Ui.MUTED);status.setVisibility(View.GONE);panel.addView(status);
        LinearLayout bar=new LinearLayout(this);bar.setOrientation(LinearLayout.HORIZONTAL);bar.setGravity(Gravity.CENTER_VERTICAL);
        seek=new SeekBar(this);seek.setMax(1000);seek.setProgressDrawable(progressDrawable());seek.setThumb(thumbDrawable());seek.setKeyProgressIncrement(10000);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){@Override public void onProgressChanged(SeekBar sb,int value,boolean fromUser){if(fromUser)clock.setText(fmt(value)+" / "+fmt(sb.getMax()));}@Override public void onStartTrackingTouch(SeekBar sb){dragging=true;}@Override public void onStopTrackingTouch(SeekBar sb){dragging=false;if(player!=null)player.seekTo(sb.getProgress());}});
        bar.addView(seek,new LinearLayout.LayoutParams(0,Ui.dp(this,30),1));
        clock=Ui.text(this,"00:00 / 00:00",12);clock.setTextColor(Ui.MUTED);bar.addView(clock);panel.addView(bar);
        HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout nav=new LinearLayout(this);nav.setOrientation(LinearLayout.HORIZONTAL);
        prevButton=action("",()->change(-1));nav.addView(prevButton);
        nav.addView(action("后退 10 秒",()->seekBy(-10000)));
        playButton=action("暂停",this::togglePlay);nav.addView(playButton);
        nav.addView(action("前进 10 秒",()->seekBy(10000)));
        nextButton=action("",()->change(1));nav.addView(nextButton);
        View split=new View(this);split.setBackgroundColor(0x33FFFFFF);LinearLayout.LayoutParams splitLp=new LinearLayout.LayoutParams(Ui.dp(this,1),Ui.dp(this,28));splitLp.setMargins(Ui.dp(this,10),0,Ui.dp(this,10),0);nav.addView(split,splitLp);
        nav.addView(action("选集",this::pickEpisode));
        nav.addView(action("倍速",this::pickSpeed));
        nav.addView(action("画面比例",this::pickRatio));
        Button rotate=action("横 / 竖屏",()->{if(!Ui.tv(this))setRequestedOrientation(getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE?ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT:ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);});
        if(Ui.tv(this))rotate.setVisibility(View.GONE);nav.addView(rotate);
        nav.addView(action("收藏",()->{store.toggleFavorite(items.get(index));Ui.toast(this,store.favorite(items.get(index))?"已收藏":"已取消收藏");}));
        nav.addView(action("换线路",this::finish));
        nav.addView(action("重试",this::prepare));
        nav.addView(action("返回",this::finish));
        scroll.addView(nav);panel.addView(scroll);
        root.addView(panel,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));panel.setVisibility(View.GONE);
    }

    private Button action(String label,Runnable run){Button b=Ui.button(this,label,run);b.setTextSize(14);return b;}
    private Drawable progressDrawable(){LayerDrawable l=new LayerDrawable(new Drawable[]{round(0x33FFFFFF,Ui.dp(this,2)),round(Ui.ACCENT,Ui.dp(this,2))});l.setId(0,android.R.id.background);l.setId(1,android.R.id.progress);return l;}
    private Drawable round(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius);return d;}
    private Drawable thumbDrawable(){GradientDrawable d=new GradientDrawable();d.setShape(GradientDrawable.OVAL);d.setColor(Ui.ACCENT);d.setSize(Ui.dp(this,14),Ui.dp(this,14));d.setStroke(Ui.dp(this,2),0x99000000);return d;}

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

    private void showTitle(){if(titleOn)return;titleOn=true;titleBar.setAlpha(0f);titleBar.setVisibility(View.VISIBLE);titleBar.animate().alpha(1f).setDuration(220).start();ui.removeCallbacks(hideTitleTask);ui.postDelayed(hideTitleTask,2800);}
    private void hideTitle(){if(!titleOn)return;titleOn=false;ui.removeCallbacks(hideTitleTask);titleBar.animate().alpha(0f).setDuration(220).withEndAction(()->{if(titleOn)return;titleBar.setVisibility(View.GONE);titleBar.setAlpha(1f);}).start();}
    private void showPanel(boolean lock){
        if(panelOn){if(lock){locked=true;ui.removeCallbacks(hidePanelTask);}return;}
        panelOn=true;locked=lock;panel.setAlpha(0f);panel.setVisibility(View.VISIBLE);panel.animate().alpha(1f).setDuration(220).start();
        showTitle();ui.removeCallbacks(tickTask);ui.postDelayed(tickTask,300);refreshProgress();
        ui.removeCallbacks(hidePanelTask);if(!locked&&!connecting)ui.postDelayed(hidePanelTask,4200);
        if(Ui.tv(this))playButton.requestFocus();
    }
    private void hidePanel(){hidePanel(220);}
    private void hidePanel(int duration){
        if(!panelOn||connecting)return;panelOn=false;locked=false;ui.removeCallbacks(hidePanelTask);ui.removeCallbacks(tickTask);
        panel.animate().alpha(0f).setDuration(duration).withEndAction(()->{if(panelOn)return;panel.setVisibility(View.GONE);panel.setAlpha(1f);root.requestFocus();}).start();
        hideTitle();
    }
    private void togglePanel(){if(panelOn)hidePanel();else showPanel(false);}
    private void showOsd(String text){osdOn=true;osd.setText(text);osd.setVisibility(View.VISIBLE);osd.setAlpha(1f);ui.removeCallbacks(hideOsdTask);ui.postDelayed(hideOsdTask,1500);}
    private void refreshProgress(){
        if(player==null)return;long d=player.getDuration(),p=player.getCurrentPosition();
        if(!dragging){if(d>0){seek.setMax((int)Math.min(d,Integer.MAX_VALUE));seek.setProgress((int)Math.max(0,Math.min(p,d)));}clock.setText(fmt(p)+" / "+fmt(d));}
    }
    private void togglePlay(){if(player!=null)player.setPlayWhenReady(!player.getPlayWhenReady());}
    private void seekBy(long ms){if(player==null)return;long d=player.getDuration(),p=player.getCurrentPosition();long t=Math.max(0,p+ms);if(d>0)t=Math.min(t,d);player.seekTo(t);showOsd((ms>0?"快进 ":"快退 ")+(Math.abs(ms)/1000)+" 秒 · "+fmt(t));}
    private static String fmt(long ms){if(ms<0)return "--:--";long s=ms/1000;long h=s/3600,m=(s/60)%60,x=s%60;return h>0?String.format(Locale.getDefault(),"%d:%02d:%02d",h,m,x):String.format(Locale.getDefault(),"%02d:%02d",m,x);}

    private void pickEpisode(){String[] labels=new String[items.size()];for(int n=0;n<labels.length;n++)labels[n]=items.get(n).title;new AlertDialog.Builder(this).setItems(labels,(d,n)->{save();index=n;resume=store.position(items.get(index).url);prepare();}).show();}
    private void pickSpeed(){new AlertDialog.Builder(this).setTitle("播放速度").setItems(new String[]{"0.75×","1.0×","1.25×","1.5×","2.0×"},(d,n)->{if(player!=null)player.setPlaybackSpeed(new float[]{.75f,1f,1.25f,1.5f,2f}[n]);}).show();}
    private void pickRatio(){new AlertDialog.Builder(this).setTitle("画面显示").setItems(new String[]{"适应屏幕","填满屏幕","裁切放大"},(d,n)->video.setResizeMode(new int[]{androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT,androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL,androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM}[n])).show();}
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
        if(!panelOn&&event.getAction()==KeyEvent.ACTION_DOWN){switch(event.getKeyCode()){
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
        if(panelOn&&key==KeyEvent.KEYCODE_BACK)return true;
        return super.onKeyDown(key,event);
    }
    @Override public boolean onKeyUp(int key,KeyEvent event){if(key==KeyEvent.KEYCODE_BACK){if(panelOn){hidePanel();return true;}finish();return true;}return super.onKeyUp(key,event);}
    @Override protected void onDestroy(){String file=getIntent().getStringExtra("playlistFile");if(isFinishing()&&file!=null&&file.startsWith("play-")&&!file.contains("/"))new java.io.File(getCacheDir(),file).delete();super.onDestroy();}
}
