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
        for(int hop=0;hop<5;hop++){if(Catalog.httpUrl(address,null)==null)throw new IOException("Invalid image URL");HttpURLConnection c=(HttpURLConnection)new URL(address).openConnection();c.setConnectTimeout(7000);c.setReadTimeout(7000);c.setInstanceFollowRedirects(false);c.setRequestProperty("User-Agent",Net.BROWSER_UA);if(new URL(address).getHost().endsWith("doubanio.com"))c.setRequestProperty("Referer","https://movie.douban.com/");
            try{int code=c.getResponseCode();if(code>=300&&code<400){address=new URL(new URL(address),c.getHeaderField("Location")).toString();continue;}if(code!=200)throw new IOException("Image HTTP "+code);try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>3*1024*1024)throw new IOException("Image too large");out.write(buffer,0,n);}return out.toByteArray();}}finally{c.disconnect();}}
        throw new IOException("Image redirects exceeded");
    }
    private static String hash(String text)throws Exception{byte[] bytes=MessageDigest.getInstance("SHA-256").digest(text.getBytes("UTF-8"));StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(Locale.ROOT,"%02x",b));return out.toString();}
    private static void trim(File dir){File[] files=dir.listFiles();if(files==null)return;long size=0;for(File f:files)size+=f.length();if(size<48*1024*1024)return;Arrays.sort(files,(a,b)->Long.compare(a.lastModified(),b.lastModified()));for(File f:files){size-=f.length();f.delete();if(size<36*1024*1024)break;}}
}
