package com.wangshaojie.libretv;
import java.io.*;
import java.net.*;
import java.util.*;
import javax.net.ssl.*;

/**
 * 自己建链的 HTTP(S) 通道，专门用于「域名直连失败、且原因是 IPv6 地址不可达」的情况。
 *
 * 老 Android（尤其 5.0 的电视）的底层只把解析结果里的第一个地址交给 socket，
 * 而路由器常返回 AAAA 记录；这类电视往往只有链路本地 IPv6、没有 IPv6 路由，
 * 于是首个连接直接 ENETUNREACH，而且底层不会自动回退到 IPv4。
 *
 * 用 HttpURLConnection 改成 IP 直连虽然能连上，却丢了 SNI：
 * Cloudflare 之类的站点会拿默认证书回应，证书对不上域名，只能报错。
 * 所以这里自己建链：连 IPv4 地址，SNI 与证书校验仍按原主机名进行。
 */
final class Ipv4Client {
    private Ipv4Client(){}

    static final class Response{
        int code;String location,type;byte[] data=new byte[0];
    }

    /**
     * 用该主机的 IPv4 地址发起一次请求，不跟随跳转。
     *
     * limit 是正文上限；sample 为真时只取开头一小段（用于流媒体探测）。
     */
    static Response get(URL target,Map<String,String> headers,String userAgent,int limit,boolean sample)throws IOException{
        String host=target.getHost();
        if(host==null||host.isEmpty())throw new IOException("地址缺少主机名");
        InetAddress ipv4=Net.ipv4Of(host);
        if(ipv4==null)throw new IOException("没有可用的 IPv4 地址");
        int port=target.getPort()>0?target.getPort():("https".equals(target.getProtocol())?443:80);
        boolean https="https".equals(target.getProtocol());
        // 与正常路径保持一致：系统配了代理就走代理，没配才直连。
        Proxy proxy=proxyFor(target);
        Socket socket=null;
        try{
            socket=open(proxy,ipv4,host,port);
            socket.setSoTimeout(15000);
            InputStream in=socket.getInputStream();OutputStream out=socket.getOutputStream();
            if(https){
                // 用主机名建链：Conscrypt 会据此发送 SNI，证书也按主机名校验
                SSLSocket ssl=(SSLSocket)HttpsURLConnection.getDefaultSSLSocketFactory().createSocket(socket,host,port,true);
                ssl.setUseClientMode(true);
                ssl.startHandshake();
                SSLSession session=ssl.getSession();
                if(!HttpsURLConnection.getDefaultHostnameVerifier().verify(host,session))throw new SSLPeerUnverifiedException("证书与域名不匹配："+host);
                socket=ssl;in=ssl.getInputStream();out=ssl.getOutputStream();
            }
            String path=target.getFile();if(path==null||path.isEmpty())path="/";
            // 走 HTTP 代理时，普通 HTTP 请求要用绝对地址
            String requestPath=proxy!=null&&proxy.type()==Proxy.Type.HTTP&&!https?target.toString():path;
            StringBuilder head=new StringBuilder("GET ").append(requestPath).append(" HTTP/1.1\r\nHost: ").append(host);
            if(target.getPort()>0)head.append(':').append(target.getPort());
            head.append("\r\nUser-Agent: ").append(userAgent).append("\r\nAccept: */*\r\nConnection: close\r\n");
            if(sample)head.append("Range: bytes=0-").append(Math.max(0,Math.min(limit,8192)-1)).append("\r\n");
            if(headers!=null)for(Map.Entry<String,String> header:headers.entrySet()){
                String name=header.getKey();
                if(name==null||header.getValue()==null)continue;
                if(name.equalsIgnoreCase("Host")||name.equalsIgnoreCase("User-Agent")||name.equalsIgnoreCase("Range")||name.equalsIgnoreCase("Connection"))continue;
                head.append(name).append(": ").append(header.getValue()).append("\r\n");
            }
            head.append("\r\n");
            out.write(head.toString().getBytes("ISO-8859-1"));out.flush();
            return read(in,limit,sample);
        }finally{
            try{if(socket!=null)socket.close();}catch(Exception ignored){}
        }
    }

    /** 系统代理：没有配置时返回 null（直连）。 */
    static Proxy proxyFor(URL target){
        try{
            ProxySelector selector=ProxySelector.getDefault();
            if(selector==null)return null;
            List<Proxy> proxies=selector.select(new URI(target.toString()));
            if(proxies==null)return null;
            // 只认 HTTP 代理：Android 的系统代理就是 HTTP 代理，
            // 而 SOCKS 在 Android 上没有可用的 SocksSocketFactory。
            // 用 SOCKS 的人一般开着 VPN，流量本来就整体走隧道。
            for(Proxy proxy:proxies)if(proxy!=null&&proxy.type()==Proxy.Type.HTTP)return proxy;
        }catch(Exception ignored){}
        return null;
    }

    /** 建立到底层服务器的连接：走代理就连代理，否则直连该主机的 IPv4。 */
    private static Socket open(Proxy proxy,InetAddress ipv4,String host,int port)throws IOException{
        if(proxy==null){
            Socket socket=new Socket();socket.connect(new InetSocketAddress(ipv4,port),12000);return socket;
        }
        // HTTP 代理：先 CONNECT 打通隧道，再在隧道里做 TLS，SNI 仍按原主机名
        InetSocketAddress endpoint=(InetSocketAddress)proxy.address();
        String proxyHost=endpoint.getHostName()!=null?endpoint.getHostName():endpoint.getAddress().getHostAddress();
        Socket socket=new Socket();socket.connect(new InetSocketAddress(proxyHost,endpoint.getPort()),12000);socket.setSoTimeout(15000);
        OutputStream out=socket.getOutputStream();
        out.write(("CONNECT "+host+":"+port+" HTTP/1.1\r\nHost: "+host+":"+port+"\r\n\r\n").getBytes("ISO-8859-1"));out.flush();
        String status=readProxyResponse(socket.getInputStream());
        if(status==null||!status.contains(" 200"))throw new IOException("代理隧道建立失败："+(status==null?"无响应":status));
        return socket;
    }

    /** 读完代理的响应头：逐字节读，避免多读进后面的 TLS 握手数据。 */
    private static String readProxyResponse(InputStream in)throws IOException{
        String status=null;StringBuilder line=new StringBuilder();
        while(true){
            int c=in.read();if(c==-1)break;
            if(c=='\n'){
                String text=line.toString().trim();line.setLength(0);
                if(status==null){status=text;continue;}
                if(text.isEmpty())break;
                continue;
            }
            if(c!='\r')line.append((char)c);
        }
        return status;
    }

    /** 解析一次 HTTP 响应；包内可见，方便单测覆盖分块与长度两种正文。 */
    static Response read(InputStream in,int limit,boolean sample)throws IOException{
        String status=readLine(in);
        if(status==null)throw new IOException("没有响应");
        String[] parts=status.split(" ");
        int code=0;
        if(parts.length>1){try{code=Integer.parseInt(parts[1].trim());}catch(Exception ignored){}}
        Map<String,String> headers=new HashMap<>();
        String line;
        while((line=readLine(in))!=null&&!line.isEmpty()){
            int at=line.indexOf(':');
            if(at>0)headers.put(line.substring(0,at).trim().toLowerCase(Locale.ROOT),line.substring(at+1).trim());
        }
        Response response=new Response();response.code=code;response.location=headers.get("location");response.type=headers.get("content-type")==null?"":headers.get("content-type");
        response.data=readBody(in,headers,limit,sample);
        return response;
    }

    private static byte[] readBody(InputStream in,Map<String,String> headers,int limit,boolean sample)throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        String encoding=headers.get("transfer-encoding");
        if(encoding!=null&&encoding.toLowerCase(Locale.ROOT).contains("chunked")){
            while(true){
                String sizeLine=readLine(in);if(sizeLine==null)break;
                int at=sizeLine.indexOf(';');if(at>0)sizeLine=sizeLine.substring(0,at);
                int size;try{size=Integer.parseInt(sizeLine.trim(),16);}catch(Exception e){break;}
                if(size<=0)break;
                byte[] chunk=new byte[size];int read=0;
                while(read<size){int n=in.read(chunk,read,size-read);if(n<0)break;read+=n;}
                out.write(chunk,0,read);
                readLine(in);
                if(out.size()>=limit)break;
            }
        }else{
            int max=limit;
            String length=headers.get("content-length");
            if(length!=null){try{max=Math.min(max,Integer.parseInt(length.trim()));}catch(Exception ignored){}}
            byte[] buffer=new byte[8192];int n;
            while(out.size()<max){
                int want=(int)Math.min(buffer.length,max-out.size());
                n=in.read(buffer,0,want);
                if(n<0)break;
                out.write(buffer,0,n);
                if(sample&&out.size()>=8192)break;
            }
        }
        return out.toByteArray();
    }

    private static String readLine(InputStream in)throws IOException{
        ByteArrayOutputStream line=new ByteArrayOutputStream();
        int c=in.read();
        while(c!=-1){
            if(c=='\n')break;
            if(c!='\r')line.write(c);
            c=in.read();
        }
        if(c==-1&&line.size()==0)return null;
        return new String(line.toByteArray(),"ISO-8859-1");
    }
}
