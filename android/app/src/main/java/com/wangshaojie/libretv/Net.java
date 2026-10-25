package com.wangshaojie.libretv;
import java.io.*;
import java.net.*;
import java.nio.charset.Charset;
import java.util.*;
import javax.net.ssl.*;

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
            HttpURLConnection c=open(current);
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

    /**
     * 建立连接。
     *
     * 老 Android（尤其 5.0 的电视）上，底层会把 DNS 返回的 IPv6 地址排在 IPv4 之前；
     * 这类设备通常只有链路本地 IPv6、没有 IPv6 路由，于是首个连接直接 ENETUNREACH，
     * 而底层对「Network is unreachable」不做地址回退——所有带 AAAA 记录的站点都连不上。
     *
     * 这里在连接前自己解析主机名，挑一个 IPv4 地址直接建连：用 IP 作为 URL 主机，
     * 同时把原主机名放进 Host 头。HTTPS 下 SNI 与证书校验仍按原主机名进行，
     * 不会因为改用 IP 而削弱 TLS 安全性。挑不到 IPv4 时退回系统默认行为。
     */
    private static HttpURLConnection open(String address)throws IOException {
        URL target=new URL(address);
        String host=target.getHost();
        if(host==null||host.isEmpty())return (HttpURLConnection)target.openConnection();
        InetAddress[] resolved;
        try{resolved=InetAddress.getAllByName(host);}catch(UnknownHostException e){return (HttpURLConnection)target.openConnection();}
        InetAddress ipv4=firstIpv4(resolved);
        if(ipv4==null)return (HttpURLConnection)target.openConnection();
        try {
            URL byIp=new URL(target.getProtocol(),ipv4.getHostAddress(),target.getPort(),target.getFile());
            HttpURLConnection c=(HttpURLConnection)byIp.openConnection();
            // 底层按 URL 主机名做 SNI 与证书校验，这里把原主机名还回去
            c.setRequestProperty("Host",host);
            if(c instanceof HttpsURLConnection)verify((HttpsURLConnection)c,host);
            return c;
        }catch(Exception e){
            android.util.Log.w("LibreTV-Net","IPv4 direct connect failed for "+host,e);
            return (HttpURLConnection)target.openConnection();
        }
    }

    /** 从解析结果里挑第一个 IPv4 地址；没有 IPv4 时返回 null。 */
    static InetAddress firstIpv4(InetAddress[] addresses){
        if(addresses==null)return null;
        for(InetAddress address:addresses)if(address instanceof Inet4Address)return address;
        return null;
    }

    /**
     * 证书校验按原主机名进行。
     * 默认校验器在 URL 主机名是 IP 时会判定证书不匹配，这里改用原主机名，
     * 但仍交给平台默认校验器判断，绝不跳过校验。
     */
    private static void verify(HttpsURLConnection c,final String host){
        try {
            final HostnameVerifier verifier=HttpsURLConnection.getDefaultHostnameVerifier();
            c.setHostnameVerifier(new HostnameVerifier(){
                public boolean verify(String ignored,javax.net.ssl.SSLSession session){
                    return verifier.verify(host,session);
                }
            });
        }catch(Exception ignored){}
    }
}
