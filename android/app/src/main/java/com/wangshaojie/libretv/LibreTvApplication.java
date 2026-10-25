package com.wangshaojie.libretv;

import android.app.Application;
import android.os.Build;
import android.util.Log;

public final class LibreTvApplication extends Application {
    @Override public void onCreate(){
        super.onCreate();
        // 旧系统（Android 8.0 以下）的根证书库缺少部分现代 CA，装上 Mozilla 证书包补齐。
        // Android 8.0 及以上直接用系统证书库，不加载补丁。
        if(Build.VERSION.SDK_INT<26)try{TlsCompat.install(this);}
        catch(Exception e){Log.e("LibreTV-TLS","Cannot initialize legacy CA compatibility; retaining platform certificate verification",e);}
    }
}
