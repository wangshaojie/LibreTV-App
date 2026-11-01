package com.wangshaojie.libretv;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Date;

public final class LibreTvApplication extends Application {
    /** 崩溃现场落盘位置：应用私有目录，不需要任何权限，卸载即清除。 */
    private static final String CRASH_FILE="crash.log";
    @Override public void onCreate(){
        super.onCreate();
        // 先装崩溃记录器，再做别的初始化 —— 越早装上，越可能记到真正的第一现场。
        recordCrashes(this);
        // 旧系统（Android 8.0 以下）的根证书库缺少部分现代 CA，装上 Mozilla 证书包补齐。
        // Android 8.0 及以上直接用系统证书库，不加载补丁。
        if(Build.VERSION.SDK_INT<26)try{TlsCompat.install(this);}
        catch(Exception e){Log.e("LibreTV-TLS","Cannot initialize legacy CA compatibility; retaining platform certificate verification",e);}
    }

    /**
     * 记录未捕获异常。
     *
     * 手机端不一定连得上 adb，闪退时拿不到 logcat，用户只能凭「闪退了」三个字描述，
     * 定位全靠猜。这里把崩溃堆栈写到应用私有目录，下次启动时由 MainActivity 弹出来，
     * 用户截图即可回传现场。
     *
     * 记录完仍然交给系统默认处理器，应用照常崩溃 —— 不能为了「不闪退」把异常吞掉，
     * 那样只会把一个明确的崩溃换成更诡异的行为。
     */
    private static void recordCrashes(Context context){
        final Thread.UncaughtExceptionHandler previous=Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread,error)->{
            try(PrintWriter writer=new PrintWriter(new OutputStreamWriter(new FileOutputStream(new File(context.getFilesDir(),CRASH_FILE),false),"UTF-8"))){
                writer.println(new Date().toString());
                writer.println(error.toString());
                for(StackTraceElement frame:error.getStackTrace())writer.println("\tat "+frame);
                Throwable cause=error.getCause();while(cause!=null){writer.println("Caused by: "+cause);for(StackTraceElement frame:cause.getStackTrace())writer.println("\tat "+frame);cause=cause.getCause();}
            }catch(Exception ignored){}
            if(previous!=null)previous.uncaughtException(thread,error);
        });
    }

    /** 读取上一次的崩溃现场；没有则返回空串。读取后现场即失效，避免反复弹。 */
    static String pendingCrash(Context context){
        File file=new File(context.getFilesDir(),CRASH_FILE);
        if(!file.exists())return "";
        StringBuilder out=new StringBuilder();
        try(java.io.BufferedReader reader=new java.io.BufferedReader(new java.io.InputStreamReader(new java.io.FileInputStream(file),"UTF-8"))){String line;while((line=reader.readLine())!=null)out.append(line).append('\n');}catch(Exception ignored){}
        try{file.delete();}catch(Exception ignored){}
        return out.toString().trim();
    }
}
