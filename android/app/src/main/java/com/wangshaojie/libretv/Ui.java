package com.wangshaojie.libretv;
import android.app.*;
import android.content.*;
import android.content.res.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

final class Ui {
    static final int BG=Color.rgb(11,16,26),TEXT=Color.rgb(232,239,249),MUTED=Color.rgb(157,177,201),ACCENT=Color.rgb(0,185,222);
    static int dp(Context c,int n){return Math.round(c.getResources().getDisplayMetrics().density*n);}
    static boolean tv(Context c){return ((UiModeManager)c.getSystemService(Context.UI_MODE_SERVICE)).getCurrentModeType()==Configuration.UI_MODE_TYPE_TELEVISION;}
    static GradientDrawable shape(int color,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(12);if(stroke!=0)d.setStroke(3,stroke);return d;}
    static StateListDrawable focus(){StateListDrawable d=new StateListDrawable();for(int state:new int[]{android.R.attr.state_focused,android.R.attr.state_selected,android.R.attr.state_activated,android.R.attr.state_pressed})d.addState(new int[]{state},shape(Color.rgb(17,68,88),ACCENT));d.addState(new int[]{},shape(Color.rgb(23,36,54),0));return d;}
    static TextView text(Context c,String value,int size){TextView t=new TextView(c);t.setText(value);t.setTextSize(size);t.setTextColor(TEXT);t.setPadding(dp(c,8),dp(c,8),dp(c,8),dp(c,8));return t;}
    static Button button(Context c,String name,Runnable action){Button b=new Button(c);b.setText(name);b.setTextColor(TEXT);b.setTextSize(15);b.setAllCaps(false);b.setBackground(focus());b.setMinHeight(dp(c,48));b.setPadding(dp(c,14),0,dp(c,14),0);b.setOnClickListener(v->action.run());LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(c,48));lp.setMargins(dp(c,3),dp(c,3),dp(c,3),dp(c,3));b.setLayoutParams(lp);return b;}
    static LinearLayout column(Context c){LinearLayout l=new LinearLayout(c);l.setOrientation(LinearLayout.VERTICAL);return l;}
    static EditText input(Context c,String hint){EditText e=new EditText(c);e.setSingleLine(true);e.setTextSize(17);e.setTextColor(TEXT);e.setHintTextColor(MUTED);e.setHint(hint);return e;}
    static void message(Context c,String text){new AlertDialog.Builder(c).setMessage(text).setPositiveButton("确定",null).show();}
    static void toast(Context c,String text){Toast.makeText(c,text,Toast.LENGTH_SHORT).show();}
}
