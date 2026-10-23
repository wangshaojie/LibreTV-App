package com.wangshaojie.libretv;

import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.regex.*;

/** Cancellable, bounded network checks. A reachable sample is not a decoder guarantee. */
final class Probe {
    static final class Result {
        String name,url,kind,error="";boolean ok;long millis,time=System.currentTimeMillis();int count;
        JSONObject json(){JSONObject j=new JSONObject();try{j.put("name",name).put("url",url).put("kind",kind).put("ok",ok).put("millis",millis).put("time",time).put("error",error).put("count",count);}catch(Exception ignored){}return j;}
    }
    interface Listener {void result(Result result,int completed,int total);void finished(boolean cancelled);}
    static final class Session {
        private final AtomicBoolean stopped=new AtomicBoolean();
        private final Set<HttpURLConnection> connections=Collections.newSetFromMap(new ConcurrentHashMap<>());
        private final ExecutorService workers=Executors.newFixedThreadPool(4);
        void cancel(){stopped.set(true);workers.shutdownNow();List<HttpURLConnection> active=new ArrayList<>(connections);if(!active.isEmpty()){Thread close=new Thread(()->{for(HttpURLConnection c:active)c.disconnect();},"probe-disconnect");close.setDaemon(true);close.start();}}
        boolean cancelled(){return stopped.get();}
        String text(String url)throws IOException{return new String(fetch(url,Collections.emptyMap(),12*1024*1024,false).data,java.nio.charset.StandardCharsets.UTF_8);}
        void start(List<Catalog.Item> targets,boolean sources,Listener listener){
            AtomicInteger next=new AtomicInteger(),completed=new AtomicInteger(),remaining=new AtomicInteger(4);
            for(int n=0;n<4;n++)workers.execute(()->{try{int index;while(!stopped.get()&&(index=next.getAndIncrement())<targets.size()){
                Result result=check(targets.get(index),sources);if(!stopped.get())listener.result(result,completed.incrementAndGet(),targets.size());
            }}finally{if(remaining.decrementAndGet()==0){workers.shutdown();listener.finished(stopped.get());}}});
        }
        Result check(Catalog.Item item,boolean source){
            Result result=new Result();result.name=item.title;result.url=source&&!item.source.isEmpty()?item.source:item.url;result.kind=source?(item.live?"直播源":"点播源"):"频道";long started=System.nanoTime();
            try{if(source){String body=new String(fetch(item.url,item.headers,12*1024*1024,false).data,"UTF-8");if(item.live){result.count=Catalog.m3u(body,item.url).size();if(result.count==0)throw new IOException("没有有效频道");}else{result.count=Catalog.search(body,item.url,item.title).size();}}else stream(item.url,item.headers,0);result.ok=true;}catch(Exception e){result.error=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}result.millis=(System.nanoTime()-started)/1000000;return result;
        }
        private void stream(String url,Map<String,String> headers,int depth)throws Exception{
            if(depth>3)throw new IOException("播放列表嵌套过多");Response response=fetch(url,headers,256*1024,true);String text=new String(response.data,"UTF-8").trim();
            if(text.startsWith("#EXTM3U")){
                Matcher key=Pattern.compile("#EXT-X-KEY:[^\\n]*METHOD=AES-128[^\\n]*URI=\"([^\"]+)\"").matcher(text);if(key.find()){Response secret=fetch(resolve(response.url,key.group(1)),headers,32,true);if(secret.data.length!=16)throw new IOException("加密密钥不可达");}
                String child=null;for(String line:text.split("\\r?\\n")){line=line.trim();if(!line.isEmpty()&&!line.startsWith("#")){child=resolve(response.url,line);break;}}
                if(child==null)throw new IOException("播放列表没有媒体片段");stream(child,headers,depth+1);return;
            }
            String type=response.type.toLowerCase(Locale.ROOT);if(text.startsWith("<!")||text.toLowerCase(Locale.ROOT).startsWith("<html")||type.contains("text/html")||type.contains("application/json"))throw new IOException("返回网页或错误信息");
            byte[] b=response.data;boolean known=b.length>=8&&((b[0]&255)==0x47||(b[0]=='F'&&b[1]=='L'&&b[2]=='V')||(b[4]=='f'&&b[5]=='t'&&b[6]=='y'&&b[7]=='p')||((b[0]&255)==0x1A&&(b[1]&255)==0x45));
            if(b.length<16||(!known&&!type.startsWith("video/")&&!type.startsWith("audio/")&&!type.contains("octet-stream")))throw new IOException("未收到可识别的媒体数据");
        }
        private String resolve(String base,String child)throws IOException{String url=Catalog.httpUrl(child,base);if(url==null)throw new IOException("不支持的媒体地址");return url;}
        private Response fetch(String address,Map<String,String> headers,int limit,boolean sample)throws IOException{
            for(int hop=0;hop<6;hop++){
                if(stopped.get()||Thread.currentThread().isInterrupted())throw new IOException("已停止");if(Catalog.httpUrl(address,null)==null)throw new IOException("仅支持 HTTP/HTTPS");
                HttpURLConnection c=(HttpURLConnection)new URL(address).openConnection();connections.add(c);c.setConnectTimeout(6000);c.setReadTimeout(6000);c.setInstanceFollowRedirects(false);c.setRequestProperty("User-Agent",Net.BROWSER_UA);if(sample)c.setRequestProperty("Range","bytes=0-"+(Math.min(limit,8192)-1));for(Map.Entry<String,String> header:headers.entrySet())c.setRequestProperty(header.getKey(),header.getValue());
                try{int code=c.getResponseCode();if(code>=300&&code<400){String location=c.getHeaderField("Location");if(location==null)throw new IOException("跳转地址缺失");address=resolve(address,location);continue;}if(code<200||code>=300)throw new IOException("HTTP "+code);
                    long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=c.getInputStream()){byte[] buffer=new byte[4096];int n;boolean playlist=c.getContentType()!=null&&(c.getContentType().contains("mpegurl")||c.getContentType().contains("text/"));while((n=in.read(buffer,0,Math.min(buffer.length,limit-out.size())))!=-1){if(stopped.get()||Thread.currentThread().isInterrupted())throw new IOException("已停止");out.write(buffer,0,n);if(out.size()>=limit)break;if(sample&&!playlist&&out.size()>=8192)break;if(System.nanoTime()>deadline)throw new IOException("请求超时");}}
                    if(out.size()==0)throw new IOException("返回空数据");if(!sample&&out.size()>=limit)throw new IOException("源列表超过大小限制");Response response=new Response();response.url=address;response.type=c.getContentType()==null?"":c.getContentType();response.data=out.toByteArray();return response;
                }finally{connections.remove(c);c.disconnect();}
            }throw new IOException("跳转次数过多");
        }
    }
    private static final class Response{String url,type;byte[] data;}
}
