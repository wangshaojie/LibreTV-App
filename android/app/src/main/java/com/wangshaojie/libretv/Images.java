package com.wangshaojie.libretv;

import android.content.Context;
import android.graphics.*;
import android.os.*;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.*;
import java.lang.ref.WeakReference;
import java.net.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/** Bounded image work and caches: no Activity references or full-size poster decoding. */
final class Images {
    private static final ExecutorService pool=Executors.newFixedThreadPool(2);
    private static final Handler main=new Handler(Looper.getMainLooper());
    private static final LruCache<String,Bitmap> memory=new LruCache<String,Bitmap>((int)Math.min(12*1024*1024,Runtime.getRuntime().maxMemory()/10)){
        protected int sizeOf(String key,Bitmap value){return value.getByteCount();}
    };
    private static final Map<String,List<WeakReference<ImageView>>> pending=new HashMap<>();
    static void load(Context context,String url,ImageView view){
        view.setTag(url);view.setImageDrawable(null);if(Catalog.httpUrl(url,null)==null)return;
        Bitmap cached=memory.get(url);if(cached!=null){view.setImageBitmap(cached);return;}
        synchronized(pending){List<WeakReference<ImageView>> waiting=pending.get(url);if(waiting!=null){waiting.add(new WeakReference<>(view));return;}waiting=new ArrayList<>();waiting.add(new WeakReference<>(view));pending.put(url,waiting);}
        File dir=new File(context.getApplicationContext().getCacheDir(),"posters");
        pool.execute(()->{Bitmap bitmap=null;try{dir.mkdirs();File file=new File(dir,hash(url));if(!file.exists()){
            byte[] bytes=download(url);File temp=new File(dir,file.getName()+".tmp");try(FileOutputStream out=new FileOutputStream(temp)){out.write(bytes);}if(!temp.renameTo(file))temp.delete();trim(dir);
        }BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getPath(),bounds);BitmapFactory.Options options=new BitmapFactory.Options();options.inPreferredConfig=Bitmap.Config.RGB_565;options.inSampleSize=1;while(bounds.outWidth/options.inSampleSize>400||bounds.outHeight/options.inSampleSize>600)options.inSampleSize*=2;bitmap=BitmapFactory.decodeFile(file.getPath(),options);if(bitmap!=null)memory.put(url,bitmap);else file.delete();
        }catch(Exception ignored){}catch(OutOfMemoryError e){memory.evictAll();}final Bitmap result=bitmap;main.post(()->{List<WeakReference<ImageView>> views;synchronized(pending){views=pending.remove(url);}if(views!=null)for(WeakReference<ImageView> ref:views){ImageView target=ref.get();if(target!=null&&url.equals(target.getTag())&&result!=null)target.setImageBitmap(result);}});});
    }
    private static byte[] download(String address)throws Exception{
        for(int hop=0;hop<5;hop++){if(Catalog.httpUrl(address,null)==null)throw new IOException("Invalid image URL");Response response=attempt(address);if(response.location!=null){address=response.location;continue;}return response.data;}
        throw new IOException("Image redirects exceeded");
    }
    /** 与 Net 一致：IPv6 不可达时改用该主机的 IPv4 重试一次，否则海报会整片加载失败。 */
    private static Response attempt(String address)throws Exception{
        String host=Net.hostOf(address);
        if(host!=null&&Net.pinnedIpv4.contains(host))return ipv4(address);
        try{return hop(address,null);}
        catch(IOException e){
            if(host==null||!Net.unreachable(e))throw e;
            Net.pinnedIpv4.add(host);
            try{return ipv4(address);}catch(IOException retry){throw e;}
        }
    }
    private static Response ipv4(String address)throws Exception{
        Ipv4Client.Response raw=Ipv4Client.get(new URL(address),Collections.emptyMap(),Net.BROWSER_UA,3*1024*1024,false);
        Response response=new Response();response.data=raw.data;response.type=raw.type;
        if(raw.location!=null)response.location=new URL(new URL(address),raw.location).toString();
        return response;
    }
    private static Response hop(String address,String ipv4Host)throws Exception{
        URL target=new URL(address);Map<String,String> headers=new HashMap<>();if(target.getHost().endsWith("doubanio.com"))headers.put("Referer","https://movie.douban.com/");
        HttpURLConnection c=Net.open(target,headers,Net.BROWSER_UA,7000,7000);
        try{int code=c.getResponseCode();if(code>=300&&code<400){String location=c.getHeaderField("Location");if(location==null)throw new IOException("跳转地址缺失");Response redirect=new Response();redirect.location=new URL(target,location).toString();return redirect;}if(code!=200)throw new IOException("Image HTTP "+code);try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>3*1024*1024)throw new IOException("Image too large");out.write(buffer,0,n);}Response response=new Response();response.data=out.toByteArray();return response;}}finally{c.disconnect();}
    }
    private static final class Response{String location,type;byte[] data;}
    private static String hash(String text)throws Exception{byte[] bytes=MessageDigest.getInstance("SHA-256").digest(text.getBytes("UTF-8"));StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(Locale.ROOT,"%02x",b));return out.toString();}
    private static void trim(File dir){File[] files=dir.listFiles();if(files==null)return;long size=0;for(File f:files)size+=f.length();if(size<48*1024*1024)return;Arrays.sort(files,(a,b)->Long.compare(a.lastModified(),b.lastModified()));for(File f:files){size-=f.length();f.delete();if(size<36*1024*1024)break;}}
}
