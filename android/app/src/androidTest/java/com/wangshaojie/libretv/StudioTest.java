package com.wangshaojie.libretv;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.view.*;
import android.widget.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import org.json.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

@RunWith(AndroidJUnit4.class)
@androidx.media3.common.util.UnstableApi
public class StudioTest {
    @Rule public ActivityTestRule<MainActivity> main=new ActivityTestRule<>(MainActivity.class,false,false);
    private Instrumentation instrumentation;private StudioFixture fixture;private Store store;private final Map<String,JSONArray> arrays=new HashMap<>();private final Map<String,JSONObject> objects=new HashMap<>();private final List<Activity> opened=new ArrayList<>();
    @Before public void setup()throws Exception{instrumentation=InstrumentationRegistry.getInstrumentation();store=new Store(instrumentation.getTargetContext());fixture=new StudioFixture(instrumentation.getContext());for(String name:new String[]{"sources","history","favorites","searches"})arrays.put(name,store.array(name));for(String name:new String[]{"preferences","source-health","channel-health",Discover.key("movie","热门"),Discover.key("tv","热门")})objects.put(name,store.object(name));store.put("sources",fixture.sources());store.put("favorites",new JSONArray());store.put("history",new JSONArray());store.put("searches",new JSONArray());store.putObject("preferences",new JSONObject().put("discover","cms"));store.putObject("source-health",new JSONObject());store.putObject("channel-health",new JSONObject());store.cache(Discover.key("movie","热门"),fixture.posters("movie"));store.cache(Discover.key("tv","热门"),fixture.posters("tv"));}
    @After public void cleanup()throws Exception{instrumentation.runOnMainSync(()->{for(Activity a:opened)if(!a.isFinishing())a.finish();});if(main.getActivity()!=null)main.finishActivity();for(Map.Entry<String,JSONArray> e:arrays.entrySet())store.put(e.getKey(),e.getValue());for(Map.Entry<String,JSONObject> e:objects.entrySet())store.putObject(e.getKey(),e.getValue());fixture.close();}
    @Test public void homeAndSearchHavePosterLayouts()throws Exception{MainActivity activity=main.launchActivity(null);Thread.sleep(900);assertText(activity,"热门电影");assertText(activity,"热播剧集");capture("home");invoke(activity,"browse",new Class[]{String.class,String.class},"movie","热门");waitFor(()->gridCount(activity)==16,5000);capture("movies");invoke(activity,"namedSearch",new Class[]{String.class},"光影纪事");waitFor(()->gridCount(activity)==1,6000);capture("search");assertText(activity,"全源搜索");}
    @Test public void detailToHttpPlaybackAndFavoriteWork()throws Exception{
        DetailActivity detail=(DetailActivity)instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(),DetailActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("item",fixture.film().json().toString()));opened.add(detail);waitFor(()->hasButton(detail,"第1集"),6000);Thread.sleep(350);capture("detail");click(detail,"加入收藏");assertTrue(store.favorite(fixture.film()));Instrumentation.ActivityMonitor monitor=instrumentation.addMonitor(PlayerActivity.class.getName(),null,false);click(detail,"第1集");PlayerActivity player=(PlayerActivity)instrumentation.waitForMonitorWithTimeout(monitor,5000);assertNotNull(player);opened.add(player);instrumentation.removeMonitor(monitor);
        Field field=PlayerActivity.class.getDeclaredField("player");field.setAccessible(true);waitFor(()->{try{androidx.media3.exoplayer.ExoPlayer p=(androidx.media3.exoplayer.ExoPlayer)field.get(player);return p!=null&&p.getPlaybackState()==androidx.media3.common.Player.STATE_READY&&p.getVideoSize().width>0;}catch(Exception e){return false;}},15000);capture("player");
    }
    @Test public void batchResultsPersistAndCanBeFiltered()throws Exception{MainActivity activity=main.launchActivity(null);invoke(activity,"sources",new Class[]{});capture("sources");Instrumentation.ActivityMonitor monitor=instrumentation.addMonitor(ProbeActivity.class.getName(),null,false);click(activity,"全部测活");ProbeActivity probe=(ProbeActivity)instrumentation.waitForMonitorWithTimeout(monitor,5000);assertNotNull(probe);opened.add(probe);instrumentation.removeMonitor(monitor);Field running=ProbeActivity.class.getDeclaredField("running");running.setAccessible(true);waitFor(()->{try{return !running.getBoolean(probe);}catch(Exception e){return false;}},12000);assertEquals(3,store.object("source-health").length());capture("probe");click(probe,"异常");Field visible=ProbeActivity.class.getDeclaredField("visible");visible.setAccessible(true);Object[] count={0};instrumentation.runOnMainSync(()->{try{count[0]=((List<?>)visible.get(probe)).size();}catch(Exception e){throw new RuntimeException(e);}});assertEquals(1,count[0]);}
    @Test public void channelProbeChecksSegmentsRejectsHtmlAndCancels()throws Exception{Probe.Session session=new Probe.Session();Catalog.Item live=new Catalog.Item();live.title="Live";live.live=true;live.url=fixture.base+"/hls.m3u8";assertTrue(session.check(live,false).ok);live.url=fixture.base+"/html";assertFalse(session.check(live,false).ok);session.cancel();Probe.Session slow=new Probe.Session();List<Catalog.Item> jobs=new ArrayList<>();for(int n=0;n<12;n++){Catalog.Item item=new Catalog.Item();item.url=fixture.base+"/slow";jobs.add(item);}CountDownLatch stopped=new CountDownLatch(1);slow.start(jobs,false,new Probe.Listener(){public void result(Probe.Result result,int complete,int total){}public void finished(boolean cancelled){if(cancelled)stopped.countDown();}});Thread.sleep(150);slow.cancel();assertTrue(slow.cancelled());assertTrue("Cancelled requests should release workers",stopped.await(8,TimeUnit.SECONDS));}
    private interface Check{boolean ok();}
    private void waitFor(Check check,long timeout)throws Exception{long deadline=System.currentTimeMillis()+timeout;boolean[] good={false};do{instrumentation.runOnMainSync(()->good[0]=check.ok());if(good[0])return;Thread.sleep(100);}while(System.currentTimeMillis()<deadline);assertTrue("UI or playback state did not settle",good[0]);}
    private int gridCount(Activity a){ArrayList<View> views=new ArrayList<>();collect(a.getWindow().getDecorView(),views);for(View v:views)if(v instanceof GridView)return ((GridView)v).getAdapter().getCount();return -1;}
    private void collect(View view,List<View> out){out.add(view);if(view instanceof ViewGroup)for(int n=0;n<((ViewGroup)view).getChildCount();n++)collect(((ViewGroup)view).getChildAt(n),out);}
    private boolean hasButton(Activity a,String text){ArrayList<View> views=new ArrayList<>();a.getWindow().getDecorView().findViewsWithText(views,text,View.FIND_VIEWS_WITH_TEXT);for(View v:views)if(v instanceof Button&&((Button)v).getText().toString().equals(text))return true;return false;}
    private void click(Activity a,String text){instrumentation.runOnMainSync(()->{ArrayList<View> views=new ArrayList<>();a.getWindow().getDecorView().findViewsWithText(views,text,View.FIND_VIEWS_WITH_TEXT);for(View v:views)if(v instanceof Button&&((Button)v).getText().toString().equals(text)){v.performClick();return;}fail("Button missing: "+text);});}
    private void assertText(Activity a,String text){instrumentation.runOnMainSync(()->{ArrayList<View> views=new ArrayList<>();a.getWindow().getDecorView().findViewsWithText(views,text,View.FIND_VIEWS_WITH_TEXT);assertFalse("Text missing: "+text,views.isEmpty());});}
    private void invoke(Object target,String name,Class<?>[] types,Object... args)throws Exception{Method method=target.getClass().getDeclaredMethod(name,types);method.setAccessible(true);instrumentation.runOnMainSync(()->{try{method.invoke(target,args);}catch(Exception e){throw new RuntimeException(e);}});}
    private void capture(String name)throws Exception{instrumentation.waitForIdleSync();Bitmap screenshot=instrumentation.getUiAutomation().takeScreenshot();assertNotNull(screenshot);File folder=new File(instrumentation.getTargetContext().getExternalFilesDir(null),"screenshots");folder.mkdirs();try(OutputStream out=new FileOutputStream(new File(folder,name+".png"))){screenshot.compress(Bitmap.CompressFormat.PNG,100,out);}screenshot.recycle();}
}
