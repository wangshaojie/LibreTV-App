package com.wangshaojie.libretv;

import android.content.Context;
import android.graphics.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Local-only synthetic catalogue, posters and media. Never included in release APKs. */
final class StudioFixture implements AutoCloseable {
    final ServerSocket server;final String base;private final ExecutorService workers=Executors.newFixedThreadPool(4);private final byte[] video;
    StudioFixture(Context tests)throws Exception{server=new ServerSocket(0,16,InetAddress.getByName("127.0.0.1"));base="http://127.0.0.1:"+server.getLocalPort();try(InputStream in=tests.getAssets().open("sample.mp4");ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);video=out.toByteArray();}Thread accept=new Thread(()->{while(!server.isClosed())try{Socket socket=server.accept();workers.execute(()->serve(socket));}catch(Exception e){break;}},"studio-http");accept.setDaemon(true);accept.start();}
    List<Catalog.Item> posters(String type){List<Catalog.Item> out=new ArrayList<>();for(int n=0;n<16;n++){Catalog.Item i=new Catalog.Item();i.title=(type.equals("movie")?"光影纪事 ":"山海之间 ")+(n+1);i.poster=base+"/poster"+(n%5)+".png";i.category=type.equals("movie")?"电影":"剧集";i.rating=n%2==0?"8.6":"9.1";i.remarks=type.equals("movie")?"2026 · 精选电影":"更新至第 12 集";out.add(i);}return out;}
    Catalog.Item film(){Catalog.Item item=new Catalog.Item();item.title="光影纪事";item.poster=base+"/poster0.png";item.source=base+"/cms?token=keep";item.sourceName="本地验证源";item.vodId="42";item.year="2026";item.category="剧情片";item.remarks="高清";item.summary="本地测试片目，用于验证界面布局、选集和播放器，不作为线上推荐。";return item;}
    JSONArray sources()throws Exception{return new JSONArray().put(new JSONObject().put("name","本地验证源").put("type","vod").put("url",base+"/cms?token=keep")).put(new JSONObject().put("name","本地直播订阅").put("type","live").put("url",base+"/subscription.m3u")).put(new JSONObject().put("name","异常示例").put("type","live").put("url",base+"/missing"));}
    private void serve(Socket socket){try(Socket client=socket){client.setSoTimeout(4000);BufferedReader in=new BufferedReader(new InputStreamReader(client.getInputStream(),"UTF-8"));String request=in.readLine();if(request==null)return;String range="",line;while((line=in.readLine())!=null&&!line.isEmpty())if(line.toLowerCase(Locale.ROOT).startsWith("range:"))range=line.substring(6).trim();String path=request.split(" ")[1];String type="text/plain",extra="";int code=200;byte[] bytes;
        if(path.startsWith("/poster")){type="image/png";bytes=poster(Character.digit(path.charAt(7),10));}
        else if(path.startsWith("/cms")){type="application/json";Catalog.Item item=film();JSONObject data=new JSONObject().put("vod_id",42).put("vod_name",item.title).put("vod_pic",item.poster).put("vod_year","2026").put("type_name","剧情片").put("vod_content",item.summary).put("vod_remarks","高清").put("vod_play_from","网页线路$$$高清线路").put("vod_play_url","第1集$"+base+"/html$$$第1集$"+base+"/video.mp4#第2集$"+base+"/video.mp4?ep=2");bytes=new JSONObject().put("list",new JSONArray().put(data)).toString().getBytes("UTF-8");}
        else if(path.startsWith("/subscription.m3u"))bytes=("#EXTM3U\n#EXTINF:-1 group-title=\"演示\",验证频道\n"+base+"/hls.m3u8\n").getBytes("UTF-8");
        else if(path.startsWith("/hls.m3u8")){type="application/vnd.apple.mpegurl";bytes="#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=100000\nmedia.m3u8\n".getBytes("UTF-8");}
        else if(path.startsWith("/media.m3u8")){type="application/vnd.apple.mpegurl";bytes="#EXTM3U\n#EXT-X-TARGETDURATION:8\n#EXTINF:8,\nseg.ts\n#EXT-X-ENDLIST\n".getBytes("UTF-8");}
        else if(path.startsWith("/seg.ts")){type="video/mp2t";bytes=new byte[188*3];for(int n=0;n<3;n++)bytes[n*188]=0x47;}
        else if(path.startsWith("/video.mp4")){type="video/mp4";bytes=video;if(range.startsWith("bytes=")){String[] parts=range.substring(6).split("-",-1);int start=Integer.parseInt(parts[0]),end=parts.length>1&&!parts[1].isEmpty()?Integer.parseInt(parts[1]):video.length-1;end=Math.min(end,video.length-1);bytes=Arrays.copyOfRange(video,start,end+1);code=206;extra="Content-Range: bytes "+start+"-"+end+"/"+video.length+"\r\n";}}
        else if(path.startsWith("/html")){type="text/html";bytes="<html>Not a stream</html>".getBytes("UTF-8");}
        else if(path.startsWith("/slow")){Thread.sleep(10000);bytes="late".getBytes("UTF-8");}
        else{code=404;bytes="not found".getBytes("UTF-8");}
        OutputStream out=client.getOutputStream();out.write(("HTTP/1.1 "+code+" OK\r\nContent-Type: "+type+"\r\nContent-Length: "+bytes.length+"\r\n"+extra+"Connection: close\r\n\r\n").getBytes("US-ASCII"));out.write(bytes);out.flush();
    }catch(Exception ignored){}}
    private byte[] poster(int index)throws IOException{int[] colors={0xFF1C606A,0xFF654C79,0xFF9C5C47,0xFF526D8B,0xFF65714C};Bitmap bitmap=Bitmap.createBitmap(336,504,Bitmap.Config.RGB_565);Canvas c=new Canvas(bitmap);Paint p=new Paint(3);p.setColor(colors[Math.max(0,index)%colors.length]);c.drawRect(0,0,336,504,p);p.setColor(0x33FFFFFF);c.drawCircle(275,130,130,p);p.setColor(0x66101925);Path shape=new Path();shape.moveTo(0,400);shape.lineTo(180,180);shape.lineTo(336,350);shape.lineTo(336,504);shape.lineTo(0,504);c.drawPath(shape,p);p.setColor(0xFFF0F6F8);p.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));p.setTextSize(22);c.drawText("LIBRE CINEMA",28,55,p);p.setTextSize(118);c.drawText("0"+(index+1),20,364,p);p.setTextSize(16);c.drawText("A STORY WORTH EXPLORING",28,465,p);ByteArrayOutputStream out=new ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.PNG,100,out);bitmap.recycle();return out.toByteArray();}
    @Override public void close()throws Exception{server.close();workers.shutdownNow();}
}
