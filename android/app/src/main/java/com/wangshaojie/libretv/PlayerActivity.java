package com.wangshaojie.libretv;
import android.app.*;
import android.content.pm.ActivityInfo;
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

@androidx.media3.common.util.UnstableApi
public final class PlayerActivity extends Activity {
    private ExoPlayer player;private PlayerView video;private Store store;private List<Catalog.Item> items=new ArrayList<>();private int index;private TextView title,error;private long resume;private boolean autoplay=true;
    @Override public void onCreate(Bundle b){super.onCreate(b);store=new Store(this);if(Ui.tv(this))setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        try{String file=getIntent().getStringExtra("playlistFile");JSONArray a=new JSONArray(file==null?getIntent().getStringExtra("items"):Playback.read(this,file));for(int n=0;n<a.length();n++)items.add(Catalog.Item.from(a.getJSONObject(n)));index=Math.max(0,Math.min(items.size()-1,getIntent().getIntExtra("index",0)));if(items.isEmpty())throw new Exception();}catch(Exception e){finish();return;}
        LinearLayout root=Ui.column(this);Ui.inset(this,root);
        HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setHorizontalScrollBarEnabled(false);LinearLayout nav=new LinearLayout(this);nav.addView(Ui.button(this,"返回",this::finish));nav.addView(Ui.button(this,"收藏",()->{store.toggleFavorite(items.get(index));Ui.toast(this,store.favorite(items.get(index))?"已收藏":"已取消收藏");}));nav.addView(Ui.button(this,items.get(index).live?"上一台":"上一集",()->change(-1)));nav.addView(Ui.button(this,items.get(index).live?"下一台":"下一集",()->change(1)));
        nav.addView(Ui.button(this,"选集",()->{String[] labels=new String[items.size()];for(int n=0;n<labels.length;n++)labels[n]=items.get(n).title;new AlertDialog.Builder(this).setItems(labels,(d,n)->{save();index=n;resume=store.position(items.get(index).url);prepare();}).show();}));
        nav.addView(Ui.button(this,"横 / 竖屏",()->{if(!Ui.tv(this))setRequestedOrientation(getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE?ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT:ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);}));nav.addView(Ui.button(this,"换线路",this::finish));nav.addView(Ui.button(this,"重试",this::prepare));scroll.addView(nav);root.addView(scroll);
        nav.addView(Ui.button(this,"倍速",()->new AlertDialog.Builder(this).setTitle("播放速度").setItems(new String[]{"0.75×","1.0×","1.25×","1.5×","2.0×"},(d,n)->{if(player!=null)player.setPlaybackSpeed(new float[]{.75f,1f,1.25f,1.5f,2f}[n]);}).show()));nav.addView(Ui.button(this,"画面比例",()->new AlertDialog.Builder(this).setTitle("画面显示").setItems(new String[]{"适应屏幕","填满屏幕","裁切放大"},(d,n)->video.setResizeMode(new int[]{androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT,androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL,androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM}[n])).show()));
        title=Ui.text(this,"",17);root.addView(title);video=new PlayerView(this);video.setUseController(true);video.setControllerAutoShow(true);video.setFocusable(true);root.addView(video,new LinearLayout.LayoutParams(-1,0,1));error=Ui.text(this,"",14);root.addView(error);setContentView(root);resume=store.position(items.get(index).url);
    }
    // One player instance is reused for every episode, channel and route. Releasing the
    // player re-creates the vendor MediaCodec, and on older MStar TV firmware that teardown
    // aborts the process (ACodec.cpp CHECK_EQ on OMX_IndexParamAudioMp3), which leaves the
    // television's video path stuck and makes every later video play with sound but no picture.
    private ExoPlayer ensurePlayer(){
        if(player!=null)return player;
        player=new ExoPlayer.Builder(this,new DefaultRenderersFactory(this).setEnableDecoderFallback(true)).build();
        player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),true);player.setHandleAudioBecomingNoisy(true);
        player.addListener(new Player.Listener(){@Override public void onPlaybackStateChanged(int state){if(state==Player.STATE_READY)error.setText("方向键 / 触屏控制播放 · 返回键退出");if(state==Player.STATE_ENDED&&index+1<items.size())change(1);}@Override public void onPlayerError(PlaybackException e){handleError(e);}});
        video.setPlayer(player);return player;
    }
    private void releasePlayer(){if(player!=null){player.release();player=null;}}
    private void prepare(){
        if(items.isEmpty())return;Catalog.Item item=items.get(index);title.setText(item.title+" · "+(index+1)+"/"+items.size());error.setText("正在连接…");
        Map<String,String> headers=new LinkedHashMap<>(item.headers);String ua=headers.remove("User-Agent");
        DefaultHttpDataSource.Factory http=new DefaultHttpDataSource.Factory().setUserAgent(ua==null?"LibreTV-App/1.0 Android":ua).setAllowCrossProtocolRedirects(true).setConnectTimeoutMs(12000).setReadTimeoutMs(20000).setDefaultRequestProperties(headers);
        MediaItem.Builder media=new MediaItem.Builder().setUri(item.url);String path=item.url.toLowerCase(Locale.ROOT).split("\\?")[0];if(path.endsWith(".m3u8"))media.setMimeType(MimeTypes.APPLICATION_M3U8);
        MediaSource source=new DefaultMediaSourceFactory(new DefaultDataSource.Factory(this,http)).createMediaSource(media.build());
        ExoPlayer p=ensurePlayer();p.setMediaSource(source);p.prepare();if(!item.live&&resume>0)p.seekTo(resume);p.setPlayWhenReady(autoplay);
        video.setKeepScreenOn(true);video.requestFocus();store.remember(item,resume);
    }
    private boolean decodeFailure(PlaybackException e){int code=e.errorCode;return code==PlaybackException.ERROR_CODE_DECODING_FAILED||code==PlaybackException.ERROR_CODE_DECODER_INIT_FAILED||code==PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED||code==PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES;}
    private void handleError(PlaybackException e){
        if(decodeFailure(e))error.setText("本机硬件解码器无法处理这条视频（可能是清晰度过高或编码不兼容）。可按「重试」再试一次，或返回详情点「换线路」换一条线路 / 选择更低清晰度的源；若电视上所有视频都只有声音没有画面，需要重启电视。");
        else if(e.errorCode==PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED)error.setText("当前地址未识别为视频，可能是网页播放线路。点「换线路」返回详情，选择 m3u8 / MP4 线路。");
        else error.setText("播放失败："+e.getErrorCodeName()+"。可点「换线路」尝试其他线路，或按「重试」重新连接。");
        video.showController();
    }
    private void save(){if(player!=null&&!items.isEmpty()){resume=player.getCurrentPosition();autoplay=player.getPlayWhenReady();store.remember(items.get(index),resume);}}
    private void change(int delta){int next=index+delta;if(next<0||next>=items.size()){Ui.toast(this,"没有更多剧集");return;}save();index=next;resume=store.position(items.get(index).url);autoplay=true;prepare();}
    @Override protected void onStart(){super.onStart();if(!items.isEmpty())prepare();}
    @Override protected void onStop(){save();releasePlayer();if(video!=null)video.setKeepScreenOn(false);super.onStop();}
    @Override public boolean dispatchKeyEvent(KeyEvent event){if(video!=null&&video.dispatchMediaKeyEvent(event))return true;return super.dispatchKeyEvent(event);}
    @Override public boolean onKeyDown(int key,KeyEvent event){if(!items.isEmpty()&&items.get(index).live&&(key==KeyEvent.KEYCODE_CHANNEL_UP||key==KeyEvent.KEYCODE_CHANNEL_DOWN)){change(key==KeyEvent.KEYCODE_CHANNEL_UP?1:-1);return true;}return super.onKeyDown(key,event);}
    @Override protected void onDestroy(){String file=getIntent().getStringExtra("playlistFile");if(isFinishing()&&file!=null&&file.startsWith("play-")&&!file.contains("/"))new java.io.File(getCacheDir(),file).delete();super.onDestroy();}
}
