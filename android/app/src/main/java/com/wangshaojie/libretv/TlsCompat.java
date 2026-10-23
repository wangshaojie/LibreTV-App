package com.wangshaojie.libretv;

import android.content.Context;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.*;

/** Supplement old Android CA stores without bypassing chain or hostname verification. */
final class TlsCompat {
    private TlsCompat(){}
    static void install(Context context)throws Exception{
        SSLContext tls=SSLContext.getInstance("TLS");
        tls.init(null,new TrustManager[]{trustManager(context)},null);
        // HttpURLConnection and Media3's DefaultHttpDataSource share this factory.
        // Leave the platform HostnameVerifier unchanged.
        HttpsURLConnection.setDefaultSSLSocketFactory(tls.getSocketFactory());
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
