package com.wangshaojie.libretv;
import java.io.*;
import java.net.*;
import java.nio.charset.Charset;
import java.util.*;
import javax.net.ssl.*;

public final class Net {
    static final String BROWSER_UA="Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 Chrome/122.0.0.0 Mobile Safari/537.36";
    private Net() {}

    /** 已经确认必须走 IPv4 的主机，避免每次都先撞一次不可达的 IPv6。 */
    private static final Set<String> pinnedIpv4=Collections.synchronizedSet(new HashSet<String>());

    public static String get(String address)throws IOException {
        return get(address,Collections.emptyMap());
    }
    public static String get(String address,Map<String,String> headers)throws IOException {
        String current=address;
        for(int hop=0;hop<6;hop++) {
            if(Thread.currentThread().isInterrupted())throw new IOException("已取消");
            if(Catalog.httpUrl(current,null)==null)throw new IOException("只支持 HTTP/HTTPS 地址");
            Response response=request(current,headers);
            if(response.location!=null){current=new URL(new URL(current),response.location).toString();continue;}
            if(response.code<200||response.code>=300)throw new IOException("HTTP "+response.code);
            return response.body;
        }throw new IOException("跳转次数过多");
    }

    /**
     * 发起一次请求。
     *
     * 默认完全走系统行为（域名解析、SNI、证书校验都不干预）。
     * 只有当失败原因是「IPv6 地址不可达」时，才改用该主机的 IPv4 地址重试一次。
     *
     * 背景：老 Android（尤其 5.0 的电视）的底层只把解析结果里的第一个地址交给 socket，
     * 而路由器常返回 AAAA 记录；这类电视往往只有链路本地 IPv6、没有 IPv6 路由，
     * 于是首个连接直接 ENETUNREACH，而且底层不会自动回退到 IPv4。
     */
    private static Response request(String address,Map<String,String> headers)throws IOException {
        String host=hostOf(address);
        if(host!=null&&pinnedIpv4.contains(host))return attempt(address,headers,host);
        try {
            return attempt(address,headers,null);
        }catch(IOException e){
            if(host==null||!unreachable(e))throw e;
            android.util.Log.w("LibreTV-Net","IPv6 unreachable for "+host+", retrying over IPv4");
            pinnedIpv4.add(host);
            try {
                return attempt(address,headers,host);
            }catch(IOException retry){
                // 回退失败时保留最初的错误，它更接近真实原因
                throw e;
            }
        }
    }

    /** 真正执行请求；ipv4Host 非空时改用该主机的 IPv4 地址建连。 */
    private static Response attempt(String address,Map<String,String> headers,String ipv4Host)throws IOException {
        URL target=new URL(address);
        HttpURLConnection c;
        if(ipv4Host==null)c=(HttpURLConnection)target.openConnection();
        else {
            InetAddress ipv4=ipv4Of(ipv4Host);
            if(ipv4==null)throw new IOException("没有可用的 IPv4 地址");
            URL byIp=new URL(target.getProtocol(),ipv4.getHostAddress(),target.getPort(),target.getFile());
            c=(HttpURLConnection)byIp.openConnection();
            // 底层按 URL 主机名做 SNI 与证书校验，这里把原主机名还回去
            c.setRequestProperty("Host",ipv4Host);
            if(c instanceof HttpsURLConnection)verify((HttpsURLConnection)c,ipv4Host);
        }
        c.setConnectTimeout(12000);c.setReadTimeout(15000);c.setInstanceFollowRedirects(false);
        c.setRequestProperty("User-Agent","LibreTV-App/1.0 Android");c.setRequestProperty("Accept","*/*");
        for(Map.Entry<String,String> h:headers.entrySet())c.setRequestProperty(h.getKey(),h.getValue());
        try {
            int code=c.getResponseCode();
            if(code==301||code==302||code==303||code==307||code==308){
                String location=c.getHeaderField("Location");if(location==null)throw new IOException("跳转地址缺失");
                return new Response(code,location,null);
            }
            Charset charset=Charset.forName("UTF-8");String type=c.getContentType();if(type!=null&&type.toLowerCase(Locale.ROOT).contains("charset="))try{charset=Charset.forName(type.substring(type.toLowerCase(Locale.ROOT).indexOf("charset=")+8).split(";")[0].trim());}catch(Exception ignored){}
            try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int count;while((count=in.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new IOException("已取消");if(out.size()+count>12*1024*1024)throw new IOException("列表超过 12 MB");out.write(buffer,0,count);}return new Response(code,null,new String(out.toByteArray(),charset));}
        }catch(IOException e){android.util.Log.w("LibreTV-Net","Request failed for "+target.getHost(),e);throw e;}finally{c.disconnect();}
    }

    /** 一次请求的结果：状态码、跳转地址或正文。 */
    private static final class Response {
        final int code;final String location;final String body;
        Response(int code,String location,String body){this.code=code;this.location=location;this.body=body;}
    }

    private static String hostOf(String address){try{String host=new URL(address).getHost();return host==null||host.isEmpty()?null:host;}catch(Exception e){return null;}}

    /** 从解析结果里挑第一个 IPv4 地址；没有 IPv4 时返回 null。 */
    static InetAddress firstIpv4(InetAddress[] addresses){
        if(addresses==null)return null;
        for(InetAddress address:addresses)if(address instanceof Inet4Address)return address;
        return null;
    }

    /** 解析主机名并返回它的第一个 IPv4 地址。 */
    static InetAddress ipv4Of(String host){
        try{return firstIpv4(InetAddress.getAllByName(host));}catch(Exception e){return null;}
    }

    /**
     * 判断异常是否由「IPv6 地址不可达」引起。
     *
     * 老系统上底层只尝试第一个（IPv6）地址，失败信息里会带上 ENETUNREACH；
     * 只有这种情况才值得换 IPv4 重试，其他失败（超时、证书、HTTP 错误）都应原样抛出。
     */
    static boolean unreachable(Throwable error){
        for(Throwable e=error;e!=null;e=e.getCause()){
            String message=e.getMessage();if(message==null)continue;
            if(message.contains("ENETUNREACH")||message.contains("Network is unreachable"))return true;
        }
        return false;
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
