package com.wangshaojie.libretv;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.view.*;
import android.widget.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import java.io.*;
import java.lang.reflect.Field;
import java.util.*;

@RunWith(AndroidJUnit4.class)
@androidx.media3.common.util.UnstableApi
public class DeviceSmokeTest {
    @Rule public ActivityTestRule<MainActivity> main=new ActivityTestRule<>(MainActivity.class);
    @Test public void launchesWithSourcesAndRemoteFocusableNavigation(){
        Instrumentation i=InstrumentationRegistry.getInstrumentation();Activity a=main.getActivity();assertTrue(new Store(a).array("sources").length()>0);
        i.runOnMainSync(()->{ArrayList<View> found=new ArrayList<>();a.getWindow().getDecorView().findViewsWithText(found,"源管理",View.FIND_VIEWS_WITH_TEXT);assertFalse(found.isEmpty());assertTrue(found.get(0).isFocusable());assertTrue(found.get(0).requestFocus());});
        i.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_LEFT);i.waitForIdleSync();assertNotNull(a.getCurrentFocus());
    }
    @Test public void declaresTvLauncherAndOptionalTouchscreen()throws Exception{
        Context c=main.getActivity();Intent tv=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER).setPackage(c.getPackageName());assertFalse(c.getPackageManager().queryIntentActivities(tv,0).isEmpty());
        PackageInfo info=c.getPackageManager().getPackageInfo(c.getPackageName(),PackageManager.GET_CONFIGURATIONS);for(FeatureInfo f:info.reqFeatures)if("android.hardware.touchscreen".equals(f.name))assertEquals(0,f.flags&FeatureInfo.FLAG_REQUIRED);
    }
    @Test public void favoriteAndHistoryPersist(){Store s=new Store(main.getActivity());Catalog.Item item=new Catalog.Item();item.title="Test";item.url="https://example.com/test.mp4";if(!s.favorite(item))s.toggleFavorite(item);assertTrue(new Store(main.getActivity()).favorite(item));s.remember(item,12345);assertEquals(12345,new Store(main.getActivity()).position(item.url));s.toggleFavorite(item);}
    @Test public void playsH264AndHandlesMediaPause()throws Exception {
        Instrumentation i=InstrumentationRegistry.getInstrumentation();Context target=i.getTargetContext();File sample=new File(target.getCacheDir(),"playback.mp4");
        try(InputStream in=i.getContext().getAssets().open("sample.mp4");OutputStream out=new FileOutputStream(sample)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
        Catalog.Item item=new Catalog.Item();item.title="Native H264 test";item.url=sample.toURI().toString();Intent intent=new Intent(target,PlayerActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("items","["+item.json()+"]");Activity video=i.startActivitySync(intent);
        Field field=PlayerActivity.class.getDeclaredField("player");field.setAccessible(true);long deadline=System.currentTimeMillis()+15000;boolean[] ready={false};
        while(System.currentTimeMillis()<deadline&&!ready[0]){Thread.sleep(200);i.runOnMainSync(()->{try{ExoPlayer p=(ExoPlayer)field.get(video);ready[0]=p!=null&&p.getPlaybackState()==Player.STATE_READY&&p.getVideoSize().width>0;}catch(Exception e){throw new RuntimeException(e);}});}
        assertTrue("Native decoder never rendered H264 video",ready[0]);i.sendKeyDownUpSync(KeyEvent.KEYCODE_MEDIA_PAUSE);i.waitForIdleSync();i.runOnMainSync(()->{try{assertFalse(((ExoPlayer)field.get(video)).getPlayWhenReady());video.finish();}catch(Exception e){throw new RuntimeException(e);}});
    }
}
