package com.wangshaojie.libretv;

import android.content.Context;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.*;

/** Supplement old Android CA stores without bypassing chain or hostname verification. */
final class TlsCompat {
    private TlsCompat(){}
    /** 是否已把补充证书库装成默认 SSLSocketFactory。 */
    private static volatile boolean installed;
    /** 旧系统（Android 8.0 以下）是否已加载补充证书库。 */
    static boolean installed(){return installed;}
    static void install(Context context)throws Exception{
        SSLContext tls=SSLContext.getInstance("TLS");
        tls.init(null,new TrustManager[]{trustManager(context)},null);
        // HttpURLConnection and Media3's DefaultHttpDataSource share this factory.
        // Leave the platform HostnameVerifier unchanged.
        HttpsURLConnection.setDefaultSSLSocketFactory(new Upgrading(tls.getSocketFactory()));
        installed=true;
    }

    /**
     * 打开 TLS 1.1 / 1.2。
     *
     * 一些 Android 5 的电视默认只协商 TLS 1.0，而 Cloudflare 之类的站点已经
     * 关掉了 TLS 1.0/1.1，握手会直接收到「unsupported protocol」。
     * 这里在保留 TLS 1.0 的前提下把更高版本补进协商列表：
     * 服务器支持就用高版本，不支持仍旧回落到 TLS 1.0，不会影响原有站点。
     */
    private static final class Upgrading extends SSLSocketFactory {
        private final SSLSocketFactory base;
        Upgrading(SSLSocketFactory base){this.base=base;}
        private Socket enable(Socket socket){
            if(socket instanceof SSLSocket){
                try{
                    SSLSocket ssl=(SSLSocket)socket;List<String> supported=Arrays.asList(ssl.getSupportedProtocols());List<String> enabled=new ArrayList<>();
                    for(String protocol:new String[]{"TLSv1.3","TLSv1.2","TLSv1.1","TLSv1"})if(supported.contains(protocol))enabled.add(protocol);
                    if(!enabled.isEmpty())ssl.setEnabledProtocols(enabled.toArray(new String[0]));
                }catch(Exception ignored){}
            }
            return socket;
        }
        public String[] getDefaultCipherSuites(){return base.getDefaultCipherSuites();}
        public String[] getSupportedCipherSuites(){return base.getSupportedCipherSuites();}
        public Socket createSocket(Socket socket,String host,int port,boolean autoClose)throws IOException{return enable(base.createSocket(socket,host,port,autoClose));}
        public Socket createSocket(String host,int port)throws IOException{return enable(base.createSocket(host,port));}
        public Socket createSocket(String host,int port,InetAddress localHost,int localPort)throws IOException{return enable(base.createSocket(host,port,localHost,localPort));}
        public Socket createSocket(InetAddress host,int port)throws IOException{return enable(base.createSocket(host,port));}
        public Socket createSocket(InetAddress address,int port,InetAddress localAddress,int localPort)throws IOException{return enable(base.createSocket(address,port,localAddress,localPort));}
    }
    static X509TrustManager trustManager(Context context)throws Exception{
        KeyStore roots=KeyStore.getInstance(KeyStore.getDefaultType());roots.load(null,null);
        try(InputStream in=context.getAssets().open("cacert.pem")){
            // Some Android 5 vendors stop generateCertificates() at PEM comment lines.
            // Decode each block separately instead of relying on their bundle parser.
            ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int read;while((read=in.read(buffer))!=-1)out.write(buffer,0,read);
            Matcher blocks=Pattern.compile("-----BEGIN CERTIFICATE-----([\\s\\S]*?)-----END CERTIFICATE-----").matcher(out.toString("US-ASCII"));
            CertificateFactory parser=CertificateFactory.getInstance("X.509");int n=0;
            while(blocks.find()){
                byte[] der=android.util.Base64.decode(blocks.group(1),android.util.Base64.DEFAULT);
                Certificate cert=parser.generateCertificate(new ByteArrayInputStream(der));
                roots.setCertificateEntry("mozilla-"+(n++),cert);
            }
            if(n<100)throw new CertificateException("Incomplete bundled CA store: "+n);
        }
        final X509TrustManager bundled=manager(roots),system=manager(null);
        return new X509TrustManager(){
            public void checkClientTrusted(X509Certificate[] chain,String authType)throws CertificateException{system.checkClientTrusted(chain,authType);}
            public void checkServerTrusted(X509Certificate[] chain,String authType)throws CertificateException{
                try{bundled.checkServerTrusted(chain,authType);}
                catch(CertificateException missing){system.checkServerTrusted(chain,authType);}
            }
            public X509Certificate[] getAcceptedIssuers(){return bundled.getAcceptedIssuers();}
        };
    }
    private static X509TrustManager manager(KeyStore store)throws Exception{
        TrustManagerFactory factory=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());factory.init(store);
        for(TrustManager manager:factory.getTrustManagers())if(manager instanceof X509TrustManager)return (X509TrustManager)manager;
        throw new CertificateException("No X509 trust manager available");
    }
}
