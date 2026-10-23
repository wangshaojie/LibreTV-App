package com.wangshaojie.libretv;

import android.content.*;
import java.io.*;
import java.util.*;
import org.json.*;

@androidx.media3.common.util.UnstableApi
final class Playback {
    static void open(Context context,List<Catalog.Item> items,int index){
        try{JSONArray json=new JSONArray();for(Catalog.Item i:items){JSONObject compact=i.json();compact.remove("summary");json.put(compact);}File file=new File(context.getCacheDir(),"play-"+UUID.randomUUID()+".json");try(OutputStream out=new FileOutputStream(file)){out.write(json.toString().getBytes("UTF-8"));}context.startActivity(new Intent(context,PlayerActivity.class).putExtra("playlistFile",file.getName()).putExtra("index",index));}
        catch(Exception e){Ui.message(context,"无法打开播放器："+e.getMessage());}
    }
    static String read(Context context,String name)throws IOException{
        File file=new File(context.getCacheDir(),name);if(!file.getCanonicalFile().getParentFile().equals(context.getCacheDir().getCanonicalFile()))throw new IOException("无效播放列表");try(InputStream in=new FileInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(out.size()+n>20*1024*1024)throw new IOException("播放列表过大");out.write(b,0,n);}return out.toString("UTF-8");}
    }
}
