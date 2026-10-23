package com.wangshaojie.libretv;

import android.app.Application;
import android.os.Build;
import android.util.Log;

public final class LibreTvApplication extends Application {
    @Override public void onCreate(){
        super.onCreate();
        if(Build.VERSION.SDK_INT<26)try{TlsCompat.install(this);}
        catch(Exception e){Log.e("LibreTV-TLS","Cannot initialize legacy CA compatibility; retaining platform certificate verification",e);}
    }
}
