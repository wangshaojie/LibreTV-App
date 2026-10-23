package com.wangshaojie.libretv;
import java.io.*;
import java.net.*;
import java.nio.charset.Charset;
import java.util.*;

public final class Net {
    static final String BROWSER_UA="Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 Chrome/122.0.0.0 Mobile Safari/537.36";
    private Net() {}
    public static String get(String address)throws IOException {
        return get(address,Collections.emptyMap());
    }
    public static String get(String address,Map<String,String> headers)throws IOException {
        String current=address;
        for(int hop=0;hop<6;hop++) {
            if(Thread.currentThread().isInterrupted())throw new IOException("已取消");
            if(Catalog.httpUrl(current,null)==null)throw new IOException("只支持 HTTP/HTTPS 地址");
            HttpURLConnection c=(HttpURLConnection)new URL(current).openConnection();
            c.setConnectTimeout(12000);c.setReadTimeout(15000);c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent","LibreTV-App/1.0 Android");c.setRequestProperty("Accept","*/*");
            for(Map.Entry<String,String> h:headers.entrySet())c.setRequestProperty(h.getKey(),h.getValue());
            try {
                int code=c.getResponseCode();
                if(code==301||code==302||code==303||code==307||code==308){String location=c.getHeaderField("Location");if(location==null)throw new IOException("跳转地址缺失");current=new URL(new URL(current),location).toString();continue;}
                if(code<200||code>=300)throw new IOException("HTTP "+code);
                Charset charset=Charset.forName("UTF-8");String type=c.getContentType();if(type!=null&&type.toLowerCase(Locale.ROOT).contains("charset="))try{charset=Charset.forName(type.substring(type.toLowerCase(Locale.ROOT).indexOf("charset=")+8).split(";")[0].trim());}catch(Exception ignored){}
                try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int count;while((count=in.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new IOException("已取消");if(out.size()+count>12*1024*1024)throw new IOException("列表超过 12 MB");out.write(buffer,0,count);}return new String(out.toByteArray(),charset);}
            }catch(IOException e){android.util.Log.w("LibreTV-Net","Request failed for "+new URL(current).getHost(),e);throw e;}finally{c.disconnect();}
        }throw new IOException("跳转次数过多");
    }
}
