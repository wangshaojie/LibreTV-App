package com.wangshaojie.libretv;

import android.content.Context;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collection;
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
            Collection<? extends Certificate> certs=CertificateFactory.getInstance("X.509").generateCertificates(in);
            if(certs.size()<100)throw new CertificateException("Incomplete bundled CA store");
            int n=0;for(Certificate cert:certs)roots.setCertificateEntry("mozilla-"+(n++),cert);
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
