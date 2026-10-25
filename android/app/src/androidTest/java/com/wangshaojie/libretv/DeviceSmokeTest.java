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
    @Test public void caStoreRejectsUntrustedCertificate()throws Exception{
        Context c=main.getActivity();javax.net.ssl.X509TrustManager trust;
        // 合并版始终携带 Mozilla 证书包；Android 8.0 以下用它补齐系统根证书库
        try(InputStream in=c.getAssets().open("cacert.pem")){assertTrue(in.read()>=0);}
        trust=TlsCompat.trustManager(c);assertTrue(trust.getAcceptedIssuers().length>=100);
        try(InputStream in=InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("untrusted.pem")){
            java.security.cert.X509Certificate cert=(java.security.cert.X509Certificate)java.security.cert.CertificateFactory.getInstance("X.509").generateCertificate(in);
            try{trust.checkServerTrusted(new java.security.cert.X509Certificate[]{cert},"RSA");fail("An unknown self-signed certificate must be rejected");}catch(java.security.cert.CertificateException expected){}
        }
        // Android 8.0 及以上必须使用系统证书库，不能被旧证书补丁顶替
        if(android.os.Build.VERSION.SDK_INT>=26)assertFalse(TlsCompat.installed());
        else{assertTrue(TlsCompat.install(c));assertTrue(TlsCompat.installed());}
        javax.net.ssl.HostnameVerifier verifier=javax.net.ssl.HttpsURLConnection.getDefaultHostnameVerifier();
        assertSame(verifier,javax.net.ssl.HttpsURLConnection.getDefaultHostnameVerifier());
    }
    @Test public void vodLandingGuidesSearchWithoutDisplayingAnError()throws Exception{
        Instrumentation i=InstrumentationRegistry.getInstrumentation();Activity a=main.getActivity();
        i.runOnMainSync(()->{ArrayList<View> found=new ArrayList<>();a.getWindow().getDecorView().findViewsWithText(found,"点播",View.FIND_VIEWS_WITH_TEXT);for(View v:found)if(v instanceof Button){v.performClick();break;}ArrayList<View> guidance=new ArrayList<>();a.getWindow().getDecorView().findViewsWithText(guidance,"输入片名后点击搜索",View.FIND_VIEWS_WITH_TEXT);assertFalse(guidance.isEmpty());ArrayList<View> misleading=new ArrayList<>();a.getWindow().getDecorView().findViewsWithText(misleading,"Spider",View.FIND_VIEWS_WITH_TEXT);assertTrue(misleading.isEmpty());});
    }
    @Test public void cmsSearchAndEpisodeRequestsPreserveApiParameters()throws Exception{
        java.net.ServerSocket server=new java.net.ServerSocket(0,4,java.net.InetAddress.getByName("127.0.0.1"));
        java.util.concurrent.FutureTask<Void> fixture=new java.util.concurrent.FutureTask<>(()->{for(int n=0;n<2;n++)try(java.net.Socket socket=server.accept()){
            BufferedReader reader=new BufferedReader(new InputStreamReader(socket.getInputStream(),"UTF-8"));String request=reader.readLine();String line;while((line=reader.readLine())!=null&&!line.isEmpty()){}
            assertTrue(request.contains("ac=videolist"));assertTrue(request.contains("token=keep"));String body;
            if(n==0){assertTrue(request.contains("wd="));body="{\"list\":[{\"vod_id\":7,\"vod_name\":\"Fixture\"}]}";}
            else{assertTrue(request.contains("ids=7"));body="{\"list\":[{\"vod_name\":\"Fixture\",\"vod_play_from\":\"HLS\",\"vod_play_url\":\"Episode$https://example.com/fixture.m3u8\"}]}";}
            byte[] bytes=body.getBytes("UTF-8");OutputStream out=socket.getOutputStream();out.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: "+bytes.length+"\r\nConnection: close\r\n\r\n").getBytes("US-ASCII"));out.write(bytes);out.flush();
        }return null;});new Thread(fixture,"cms-fixture").start();
        try{String base="http://127.0.0.1:"+server.getLocalPort()+"/api?token=keep&ac=old&pg=9";List<Catalog.Item> found=Catalog.search(Net.get(MainActivity.cmsUrl(base,"videolist","wd","test")),base,"Fixture");assertEquals(1,found.size());assertEquals("7",found.get(0).vodId);Map<String,List<Catalog.Item>> episodes=Catalog.episodes(Net.get(MainActivity.cmsUrl(base,"videolist","ids",found.get(0).vodId)));assertEquals("https://example.com/fixture.m3u8",episodes.get("HLS").get(0).url);fixture.get(5,java.util.concurrent.TimeUnit.SECONDS);}finally{server.close();}
    }
    @Test public void upgradeAddsPresetsOnceAndPreservesUserData()throws Exception{
        Context base=main.getActivity();Context isolated=new ContextWrapper(base){@Override public SharedPreferences getSharedPreferences(String name,int mode){return base.getSharedPreferences("preset-migration-test",mode);}};
        SharedPreferences prefs=isolated.getSharedPreferences("libretv",0);prefs.edit().clear().putString("sources","[{\"type\":\"live\",\"name\":\"My source\",\"url\":\"https://example.com/mine.m3u\"}]").putString("favorites","[{\"title\":\"Keep me\"}]").commit();
        Store upgraded=new Store(isolated);assertEquals(7,upgraded.array("sources").length());assertEquals("My source",upgraded.array("sources").getJSONObject(0).getString("name"));assertEquals("Keep me",upgraded.array("favorites").getJSONObject(0).getString("title"));
        org.json.JSONArray saved=upgraded.array("sources");saved.remove(1);upgraded.put("sources",saved);assertEquals(6,new Store(isolated).array("sources").length());prefs.edit().clear().commit();
    }
    @Test public void keepsControlsUpWhileConnectingAndFadesThemAfterPlayback()throws Exception {
        Instrumentation i=InstrumentationRegistry.getInstrumentation();Context target=i.getTargetContext();File sample=new File(target.getCacheDir(),"panel.mp4");
        try(InputStream in=i.getContext().getAssets().open("sample.mp4");OutputStream out=new FileOutputStream(sample)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
        Catalog.Item item=new Catalog.Item();item.title="Panel test";item.url=sample.toURI().toString();
        Intent intent=new Intent(target,PlayerActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("items","["+item.json()+"]");Activity video=i.startActivitySync(intent);
        Field panelField=PlayerActivity.class.getDeclaredField("panel");panelField.setAccessible(true);
        boolean[] up={false};i.runOnMainSync(()->{try{up[0]=((View)panelField.get(video)).getVisibility()==View.VISIBLE;}catch(Exception e){throw new RuntimeException(e);}});
        assertTrue("The control panel must stay up while the source connects, otherwise the viewer only sees a black screen",up[0]);
        boolean[] gone={false};long deadline=System.currentTimeMillis()+20000;
        while(System.currentTimeMillis()<deadline&&!gone[0]){Thread.sleep(300);i.runOnMainSync(()->{try{gone[0]=((View)panelField.get(video)).getVisibility()!=View.VISIBLE;}catch(Exception e){throw new RuntimeException(e);}});}
        assertTrue("The control panel must fade away on its own once playback starts",gone[0]);
        i.runOnMainSync(video::finish);
    }
    @Test public void playsH264AndHandlesMediaPause()throws Exception {
        Instrumentation i=InstrumentationRegistry.getInstrumentation();Context target=i.getTargetContext();File sample=new File(target.getCacheDir(),"playback.mp4");
        try(InputStream in=i.getContext().getAssets().open("sample.mp4");OutputStream out=new FileOutputStream(sample)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
        Catalog.Item item=new Catalog.Item();item.title="Native H264 test";item.url=sample.toURI().toString();Intent intent=new Intent(target,PlayerActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("items","["+item.json()+"]");Activity video=i.startActivitySync(intent);
        Field field=PlayerActivity.class.getDeclaredField("player");field.setAccessible(true);long deadline=System.currentTimeMillis()+15000;boolean[] ready={false};
        while(System.currentTimeMillis()<deadline&&!ready[0]){Thread.sleep(200);i.runOnMainSync(()->{try{ExoPlayer p=(ExoPlayer)field.get(video);ready[0]=p!=null&&p.getPlaybackState()==Player.STATE_READY&&p.getVideoSize().width>0;}catch(Exception e){throw new RuntimeException(e);}});}
        assertTrue("Native decoder never rendered H264 video",ready[0]);i.sendKeyDownUpSync(KeyEvent.KEYCODE_MEDIA_PAUSE);i.waitForIdleSync();i.runOnMainSync(()->{try{assertFalse(((ExoPlayer)field.get(video)).getPlayWhenReady());video.finish();}catch(Exception e){throw new RuntimeException(e);}});
    }
}
