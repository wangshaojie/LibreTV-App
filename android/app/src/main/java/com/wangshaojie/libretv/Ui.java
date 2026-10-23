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
    static final int BG=Color.rgb(9,14,22),SURFACE=Color.rgb(19,28,40),TEXT=Color.rgb(240,245,250),MUTED=Color.rgb(144,161,182),ACCENT=Color.rgb(64,222,194),WARN=Color.rgb(246,191,86),BAD=Color.rgb(245,115,127);
    static int dp(Context c,int n){return Math.round(c.getResources().getDisplayMetrics().density*n);}
    static boolean tv(Context c){return ((UiModeManager)c.getSystemService(Context.UI_MODE_SERVICE)).getCurrentModeType()==Configuration.UI_MODE_TYPE_TELEVISION;}
    static GradientDrawable shape(int color,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(12);if(stroke!=0)d.setStroke(3,stroke);return d;}
    static StateListDrawable focus(){StateListDrawable d=new StateListDrawable();for(int state:new int[]{android.R.attr.state_focused,android.R.attr.state_selected,android.R.attr.state_activated,android.R.attr.state_pressed})d.addState(new int[]{state},shape(Color.rgb(17,68,88),ACCENT));d.addState(new int[]{},shape(Color.rgb(23,36,54),0));return d;}
    static TextView text(Context c,String value,int size){TextView t=new TextView(c);t.setText(value);t.setTextSize(size);t.setTextColor(TEXT);t.setPadding(dp(c,8),dp(c,8),dp(c,8),dp(c,8));return t;}
    static Button button(Context c,String name,Runnable action){Button b=new Button(c);b.setText(name);b.setTextColor(TEXT);b.setTextSize(15);b.setAllCaps(false);b.setBackground(focus());b.setMinHeight(dp(c,48));b.setPadding(dp(c,14),0,dp(c,14),0);b.setOnClickListener(v->action.run());LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(c,48));lp.setMargins(dp(c,3),dp(c,3),dp(c,3),dp(c,3));b.setLayoutParams(lp);return b;}
    static LinearLayout column(Context c){LinearLayout l=new LinearLayout(c);l.setOrientation(LinearLayout.VERTICAL);return l;}
    static TextView label(Context c,String value,int size,int color){TextView t=text(c,value,size);t.setTextColor(color);t.setPadding(0,0,0,0);return t;}
    static TextView heading(Context c,String value){TextView t=label(c,value,tv(c)?24:21,TEXT);t.setTypeface(null,Typeface.BOLD);return t;}
    static void padding(View v,int n){int p=dp(v.getContext(),n);v.setPadding(p,p,p,p);}
    static View gap(Context c,int height){View v=new View(c);v.setLayoutParams(new LinearLayout.LayoutParams(1,dp(c,height)));return v;}
    static LinearLayout row(Context c){LinearLayout l=new LinearLayout(c);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    static LinearLayout panel(Context c){LinearLayout l=column(c);l.setBackground(shape(SURFACE,0));padding(l,16);return l;}
    static ScrollView scroll(Context c,View child){ScrollView s=new ScrollView(c);s.setFillViewport(true);s.setClipToPadding(false);s.addView(child);return s;}
    static Button primary(Context c,String value,Runnable action){Button b=button(c,value,action);b.setTextColor(BG);StateListDrawable states=new StateListDrawable();states.addState(new int[]{android.R.attr.state_focused},shape(Color.WHITE,ACCENT));states.addState(new int[]{},shape(ACCENT,0));b.setBackground(states);return b;}
    static void inset(android.app.Activity a,View root){root.setBackgroundColor(BG);root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets.consumeSystemWindowInsets();});if(tv(a))a.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);}
    static View poster(Context c,Catalog.Item item,int width,Runnable click){
        LinearLayout card=column(c);card.setBackground(focus());card.setPadding(dp(c,3),dp(c,3),dp(c,3),dp(c,8));card.setFocusable(true);card.setClickable(true);card.setContentDescription(item.title);card.setOnClickListener(v->click.run());
        FrameLayout art=new FrameLayout(c);art.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Color.rgb(35,59,72),Color.rgb(22,30,48)}));ImageView image=new ImageView(c);image.setScaleType(ImageView.ScaleType.CENTER_CROP);art.addView(image,new FrameLayout.LayoutParams(-1,-1));
        TextView rating=label(c,item.rating.isEmpty()?item.year:item.rating,12,WARN);rating.setBackground(shape(0xCC0B121C,0));padding(rating,4);FrameLayout.LayoutParams badge=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.RIGHT);badge.setMargins(0,dp(c,5),dp(c,5),0);art.addView(rating,badge);if(rating.getText().length()==0)rating.setVisibility(View.GONE);
        TextView placeholder=label(c,"LIBRE TV",12,0x778FA9BA);placeholder.setGravity(Gravity.CENTER);art.addView(placeholder,new FrameLayout.LayoutParams(-1,-1));if(!item.poster.isEmpty()){placeholder.setVisibility(View.GONE);Images.load(c,item.poster,image);}
        card.addView(art,new LinearLayout.LayoutParams(-1,dp(c,width*3/2)));TextView title=label(c,item.title,tv(c)?16:14,TEXT);title.setTypeface(null,Typeface.BOLD);title.setMaxLines(1);title.setEllipsize(android.text.TextUtils.TruncateAt.END);title.setPadding(dp(c,6),dp(c,8),dp(c,4),0);card.addView(title);
        String subtitle=!item.remarks.isEmpty()?item.remarks:(!item.category.isEmpty()?item.category:item.sourceName);TextView sub=label(c,subtitle,12,MUTED);sub.setMaxLines(1);sub.setEllipsize(android.text.TextUtils.TruncateAt.END);sub.setPadding(dp(c,6),dp(c,4),dp(c,4),0);card.addView(sub);
        if(tv(c))card.setOnFocusChangeListener((v,focused)->v.animate().scaleX(focused?1.035f:1f).scaleY(focused?1.035f:1f).setDuration(120).start());return card;
    }
    static EditText input(Context c,String hint){EditText e=new EditText(c);e.setSingleLine(true);e.setTextSize(17);e.setTextColor(TEXT);e.setHintTextColor(MUTED);e.setHint(hint);return e;}
    static void message(Context c,String text){new AlertDialog.Builder(c).setMessage(text).setPositiveButton("确定",null).show();}
    static void toast(Context c,String text){Toast.makeText(c,text,Toast.LENGTH_SHORT).show();}
}
