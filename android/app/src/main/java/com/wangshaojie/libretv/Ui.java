package com.wangshaojie.libretv;
import android.app.*;
import android.content.*;
import android.content.res.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.os.Build;
import android.view.*;
import android.widget.*;

/**
 * 全局设计系统 ——「静观 TV」扁平主题。
 *
 * 方向：暖白底 + 雾霭青绿。低饱和、单强调色、大量留白。
 * 强调色只出现在主按钮、激活态、焦点态、评分徽章上，不铺满界面。
 *
 * 扁平主题的三个关键取舍：
 *   1. 去阴影、去描边、去大圆角、去渐变 —— 层次只靠纯色块（背景色差异）区分。
 *      所以 shape()/pill() 一律画直角纯色，elevate() 是空实现，fade() 返回纯色。
 *   2. 焦点态必须看得见 —— 去掉描边后「描边 + 卡片」那一套就没了，
 *      所以焦点一律换成淡青（ACCENT_SOFT）实心色块，与常态的白卡片对比明显。
 *      不用深青 ACCENT 实心：图标（NavIcon）在聚焦时本身就是青绿色，
 *      深青底 + 青绿图标会糊成一片，等于把焦点藏起来。
 *   3. 播放页保持深色：视频观看场景需要沉浸感，浅色边框会非常刺眼。
 */
final class Ui {
    // 晨雾白：暖白页面底 + 白卡 + 雾霭青绿强调色
    static final int BG=Color.rgb(242,240,234),SURFACE=Color.rgb(255,255,255),CARD=Color.rgb(255,255,255),
        CHIP=Color.rgb(238,234,226),LINE=Color.rgb(224,219,208),
        TEXT=Color.rgb(34,37,43),MUTED=Color.rgb(110,116,128),FAINT=Color.rgb(154,160,166),
        ACCENT=Color.rgb(63,125,119),ACCENT_LIGHT=Color.rgb(111,169,162),ACCENT_DEEP=Color.rgb(45,94,90),
        ACCENT_SOFT=Color.rgb(226,240,237),ON_ACCENT=Color.rgb(255,255,255),
        RATING=Color.rgb(184,128,52),WARN=Color.rgb(184,128,52),BAD=Color.rgb(192,82,79),
        DARK=Color.rgb(20,22,26),DARK_CARD=Color.rgb(28,31,36);
    static int dp(Context c,int n){return Math.round(c.getResources().getDisplayMetrics().density*n);}
    /**
     * 判断是不是电视。
     *
     * 只认系统的 UI_MODE_TYPE_TELEVISION —— 电视装 TV 版、手机装手机版，
     * 界面按各自的形态来排。没有额外的调试开关：调试用的「在电视上强制手机布局」
     * 一旦留在发布包里，电视用户就会看到手机版的底部导航。
     */
    static boolean tv(Context c){return ((UiModeManager)c.getSystemService(Context.UI_MODE_SERVICE)).getCurrentModeType()==Configuration.UI_MODE_TYPE_TELEVISION;}
    /**
     * 扁平底色块：纯色 + 直角，不描边。
     *
     * stroke 参数保留只是为了不改所有调用点 —— 扁平化之后一律不画描边。
     */
    static GradientDrawable shape(int color,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(color);return d;}
    /** 自定义圆角（扁平化后同样退化成直角纯色，签名保留） */
    static GradientDrawable shape(int color,int stroke,int radiusDp){GradientDrawable d=new GradientDrawable();d.setColor(color);return d;}
    /**
     * 海报裁剪：扁平化后不再有圆角，radiusDp 传 0 时完全不裁剪。
     *
     * 传 0 以外的值时也只做直角裁剪（setRect），不画圆角。
     */
    static void clipRound(final android.widget.ImageView v,final int radiusDp){
        if(Build.VERSION.SDK_INT<21)return;
        if(radiusDp<=0){v.setClipToOutline(false);return;}
        v.setClipToOutline(true);
        v.setOutlineProvider(new ViewOutlineProvider(){public void getOutline(View view,android.graphics.Outline outline){outline.setRect(0,0,view.getWidth(),view.getHeight());}});
    }
    /** 轻投影：扁平化后不再有投影，层次只靠色块深浅区分（签名保留，调用点不必改） */
    static void elevate(View v,float amount){}
    /**
     * 渐变底：扁平化后退化成纯色。
     *
     * 只取起始色 from，Orientation 与 to 都不再起作用 —— 纯色块不需要渐变。
     */
    static GradientDrawable fade(Context c,GradientDrawable.Orientation o,int from,int to){GradientDrawable d=new GradientDrawable();d.setColor(from);return d;}
    /** 胶囊底：扁平化后是直角色块 */
    static GradientDrawable pill(Context c,int color){GradientDrawable d=new GradientDrawable();d.setColor(color);return d;}
    /** 正圆背景（圆形图标底 / 头像位 / 品牌标 —— 形状本身就是圆，不是圆角装饰） */
    static GradientDrawable circle(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setShape(GradientDrawable.OVAL);return d;}
    /**
     * 播放页的面板底：纯深色不透明色块，直角、无描边、无半透明。
     *
     * 扁平化之后不再有「磨砂玻璃」：半透明会透出画面颜色、描边会形成亮边，
     * 两者都是装饰性细节。改成一块不透明的深色块，靠色块本身把控制层与画面分开。
     */
    static GradientDrawable glass(int fill,int stroke,int radiusDp){GradientDrawable d=new GradientDrawable();d.setColor(0xFF000000|(fill&0x00FFFFFF));return d;}

    /**
     * 播放进度条：3px 细轨道 + 青绿实色已播段。
     *
     * 踩过的坑：
     *   1. 进度条必须是一个 LayerDrawable —— ProgressBar.setProgressDrawable 会从中
     *      挑出 id=android.R.id.progress 的那一层当进度层，其余当背景层。传一个
     *      普通 Drawable 的话，ProgressBar 只会当背景画，水平进度永远画不出来。
     *   2. 两层都得自己画 —— LayerDrawable 的子层会被拉伸到整个 SeekBar 高度
     *      （34dp），直接放 GradientDrawable 会变成一块灰砖。用 setLayerSize 固定
     *      高度也不行，那个方法是 @hide，API 21 上运行期直接 NoSuchMethodError。
     *      所以轨道和色条都在自己的 draw() 里按「垂直居中 + 固定 3px」画。
     *   3. 色条外面套 ClipDrawable —— 水平方向的裁剪交给系统那套 level 机制，
     *      我们不用再自己算进度比例。
     */
    /** 轨道：一条 3px 的浅色细线，垂直居中（直角，无圆角）。 */
    static final class TrackDrawable extends android.graphics.drawable.Drawable{
        private final android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final int color;private final float heightPx;
        TrackDrawable(int color,float heightPx){this.color=color;this.heightPx=heightPx;}
        @Override public void draw(android.graphics.Canvas canvas){
            android.graphics.Rect b=getBounds();
            if(b.width()<=0||b.height()<=0)return;
            float cy=(b.top+b.bottom)/2f,h=heightPx/2f;
            paint.setShader(null);paint.setColor(color);
            canvas.drawRect(b.left,cy-h,b.right,cy+h,paint);
        }
        @Override protected void onBoundsChange(android.graphics.Rect b){}
        @Override public void setAlpha(int alpha){paint.setAlpha(alpha);}
        @Override public void setColorFilter(android.graphics.ColorFilter colorFilter){paint.setColorFilter(colorFilter);}
        @Override public int getOpacity(){return android.graphics.PixelFormat.TRANSLUCENT;}
    }

    /** 已播段：青绿实色条，垂直居中，高度与轨道一致。宽度由外层 ClipDrawable 裁剪。 */
    static final class BarDrawable extends android.graphics.drawable.Drawable{
        private final android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final int startColor,endColor;private final float heightPx;
        BarDrawable(int startColor,int endColor,float heightPx){this.startColor=startColor;this.endColor=endColor;this.heightPx=heightPx;}
        @Override public void draw(android.graphics.Canvas canvas){
            android.graphics.Rect b=getBounds();
            if(b.width()<=0||b.height()<=0)return;
            float cy=(b.top+b.bottom)/2f,h=heightPx/2f;
            // 扁平化：不再用渐变着色，只画起始色的实色条
            paint.setShader(null);paint.setColor(startColor);
            canvas.drawRect(b.left,cy-h,b.right,cy+h,paint);
        }
        @Override protected void onBoundsChange(android.graphics.Rect b){}
        @Override public void setAlpha(int alpha){paint.setAlpha(alpha);}
        @Override public void setColorFilter(android.graphics.ColorFilter colorFilter){paint.setColorFilter(colorFilter);}
        @Override public int getOpacity(){return android.graphics.PixelFormat.TRANSLUCENT;}
    }

    /** 轨道 + 青绿已播段的组合，供 SeekBar 直接用。 */
    static android.graphics.drawable.Drawable progressBar(int trackColor,int startColor,int endColor,int heightPx){
        android.graphics.drawable.ClipDrawable clip=new android.graphics.drawable.ClipDrawable(new BarDrawable(startColor,endColor,heightPx),android.view.Gravity.START,android.graphics.drawable.ClipDrawable.HORIZONTAL);
        LayerDrawable l=new LayerDrawable(new android.graphics.drawable.Drawable[]{new TrackDrawable(trackColor,heightPx),clip});
        l.setId(0,android.R.id.background);l.setId(1,android.R.id.progress);
        return l;
    }
    /**
     * 缓冲指示用的转圈圆环。
     *
     * 不用系统 ProgressBar 的原因：它的 indeterminate 颜色跟着系统主题走，
     * 老电视上浅色主题会变成一个深色圆盘，在深色画面上几乎看不见。
     * 自己画四段递减透明度的圆弧，再把承载它的 View 转起来 —— 颜色、线宽、
     * 透明度都可控，扁平风也统一（转圈本身就是「圆」的语义，不算圆角装饰）。
     */
    public static final class RingDrawable extends android.graphics.drawable.Drawable{
        private final android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final int color;private final float strokePx;
        RingDrawable(int color,float strokePx){this.color=color;this.strokePx=strokePx;}
        @Override public void draw(android.graphics.Canvas canvas){
            android.graphics.Rect b=getBounds();
            if(b.width()<=0||b.height()<=0)return;
            float r=Math.min(b.width(),b.height())/2f-strokePx/2f;
            if(r<=0)return;
            float cx=b.exactCenterX(),cy=b.exactCenterY();
            paint.setShader(null);paint.setStyle(android.graphics.Paint.Style.STROKE);
            paint.setStrokeWidth(strokePx);paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
            // 四段弧，透明度依次递减 —— 看起来就是一圈在转
            for(int i=0;i<4;i++){
                paint.setColor(color);
                paint.setAlpha((int)(255*(1.0-i*0.22)));
                canvas.drawArc(cx-r,cy-r,cx+r,cy+r,i*90f+18f,56f,false,paint);
            }
        }
        @Override protected void onBoundsChange(android.graphics.Rect b){}
        @Override public void setAlpha(int alpha){paint.setAlpha(alpha);}
        @Override public void setColorFilter(android.graphics.ColorFilter colorFilter){paint.setColorFilter(colorFilter);}
        @Override public int getOpacity(){return android.graphics.PixelFormat.TRANSLUCENT;}
    }
    /**
     * 胶片颗粒：扁平化之后整体移除。
     *
     * 返回 null，调用点直接不设背景 —— 噪点属于「暗夜毛玻璃」那一套装饰语言，
     * 扁平主题里没有它的位置。
     */
    static android.graphics.drawable.Drawable grain(final Context c){return null;}
    /**
     * 焦点态：实心淡青块；常态：白卡纯色。
     *
     * 去掉描边之后，「白卡 + 青绿描边」那一套就失效了 —— 焦点必须换成一块
     * 实心的强调色，遥控器移动焦点时才有明确的「我现在停在这里」。
     *
     * 用淡青（ACCENT_LIGHT）而不是深青（ACCENT）做实心底：焦点态的背景上
     * 往往还压着深色文字（卡片标题、菜单项、弹框行），深青底会把文字吃掉，
     * 淡青底上深色文字仍然清晰，焦点也依然一眼可见。
     * 纯图标控件（顶栏图标、侧边导航）另走一档：深青底 + 白色图标。
     */
    static StateListDrawable focus(){StateListDrawable d=new StateListDrawable();for(int state:new int[]{android.R.attr.state_focused,android.R.attr.state_selected,android.R.attr.state_activated,android.R.attr.state_pressed})d.addState(new int[]{state},shape(ACCENT_LIGHT,0));d.addState(new int[]{},shape(CARD,0));return d;}
    static TextView text(Context c,String value,int size){TextView t=new TextView(c);t.setText(value);t.setTextSize(size);t.setTextColor(TEXT);t.setPadding(dp(c,8),dp(c,8),dp(c,8),dp(c,8));return t;}
    static Button button(Context c,String name,Runnable action){Button b=new Button(c);b.setText(name);b.setTextColor(TEXT);b.setTextSize(15);b.setAllCaps(false);b.setBackground(focus());b.setMinHeight(dp(c,48));b.setPadding(dp(c,14),0,dp(c,14),0);b.setOnClickListener(v->action.run());
        focusable(b,c);plain(b);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(c,48));lp.setMargins(dp(c,3),dp(c,3),dp(c,3),dp(c,3));b.setLayoutParams(lp);return b;}
    /**
     * 焦点能力按端区分。
     *
     * 电视端：系统可能仍处于 touch mode，此时 focusableInTouchMode=false 的控件
     * requestFocus 会静默失败 —— 焦点怎么都抢不到导航栏。所以电视端必须显式允许。
     *
     * 手机端：必须反过来关掉。框架 View.onTouchEvent 在 ACTION_UP 时的逻辑是
     * 「若控件 focusableInTouchMode 且当前没有焦点，就先 requestFocus 抢焦点，
     * 抢到了就不走 performClick」—— 于是第一次点按钮只会让它获得焦点、
     * 不触发点击，用户体感就是「点了没反应」，每个控件都得点两次。
     * 遥控器路径不受影响（按下时按钮早已持有焦点），所以这个问题只在触摸设备上出现。
     */
    static void focusable(View v,Context c){v.setFocusable(true);v.setFocusableInTouchMode(tv(c));}
    /**
     * 去掉 Material 按钮自带的抬升动画。
     *
     * Android 5.0 的 Widget.Material.Button 默认挂 stateListAnimator
     * （anim/button_state_list_anim_material），里面是两组 objectAnimator，
     * 分别动 translationZ 和 elevation。这两个 ObjectAnimator 的目标就是按钮本身，
     * 而它们的 PropertyValuesHolder 通过 JNI 反射调用 setTranslationZ/setElevation。
     *
     * 按钮一旦在动画播放期间被移除或重建，ObjectAnimator 的 target 会变成 null，
     * 反射调用就在 native 层被 check_jni 判定为
     * "JNI DETECTED ERROR IN APPLICATION: obj == null"，
     * 直接 abort 整个进程 —— 在电视上表现为点开详情页立刻闪退。
     *
     * 我们的按钮本来就有自己的焦点态，不需要系统抬升效果，直接关掉最稳。
     */
    private static void plain(Button b){if(Build.VERSION.SDK_INT>=21)b.setStateListAnimator(null);}
    static LinearLayout column(Context c){LinearLayout l=new LinearLayout(c);l.setOrientation(LinearLayout.VERTICAL);return l;}
    static TextView label(Context c,String value,int size,int color){TextView t=text(c,value,size);t.setTextColor(color);t.setPadding(0,0,0,0);return t;}
    static TextView heading(Context c,String value){TextView t=label(c,value,tv(c)?24:21,TEXT);t.setTypeface(null,Typeface.BOLD);return t;}
    static void padding(View v,int n){int p=dp(v.getContext(),n);v.setPadding(p,p,p,p);}
    static View gap(Context c,int height){View v=new View(c);v.setLayoutParams(new LinearLayout.LayoutParams(1,dp(c,height)));return v;}
    static LinearLayout row(Context c){LinearLayout l=new LinearLayout(c);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    /** 面板：纯白色块，扁平化后无描边无投影 */
    static LinearLayout panel(Context c){LinearLayout l=column(c);l.setBackground(shape(SURFACE,0));padding(l,16);return l;}
    static ScrollView scroll(Context c,View child){ScrollView s=new ScrollView(c);s.setFillViewport(true);s.setClipToPadding(false);s.addView(child);return s;}
    /** 主按钮：青绿实心 + 白字；聚焦时加深一档 */
    static Button primary(Context c,String value,Runnable action){Button b=button(c,value,action);b.setTextColor(ON_ACCENT);b.setTypeface(null,Typeface.BOLD);StateListDrawable states=new StateListDrawable();states.addState(new int[]{android.R.attr.state_focused},shape(ACCENT_DEEP,0));states.addState(new int[]{},shape(ACCENT,0));b.setBackground(states);plain(b);return b;}
    /** 次级按钮：常态白块，聚焦实心淡青 */
    static Button outline(Context c,String value,Runnable action){Button b=button(c,value,action);b.setBackground(focus());return b;}
    /** 弱强调按钮：淡青底 + 青绿字，用于「更多」「加载更多」这类轻动作 */
    static Button soft(Context c,String value,Runnable action){Button b=button(c,value,action);b.setTextSize(14);StateListDrawable s=new StateListDrawable();s.addState(new int[]{android.R.attr.state_focused},shape(ACCENT,0));s.addState(new int[]{},shape(ACCENT_SOFT,0));b.setBackground(s);b.setTextColor(new android.content.res.ColorStateList(new int[][]{{android.R.attr.state_focused},{-android.R.attr.state_focused}},new int[]{ON_ACCENT,ACCENT_DEEP}));return b;}
    /** 文字按钮：无底色，聚焦时才出现淡青底 —— 用于「更多」这类不该抢戏的次要动作 */
    static Button link(Context c,String value,Runnable action){Button b=button(c,value,action);b.setTextSize(14);b.setPadding(dp(c,12),0,dp(c,12),0);b.setMinHeight(dp(c,36));b.setBackground(linkState());b.setTextColor(new android.content.res.ColorStateList(new int[][]{{android.R.attr.state_focused},{-android.R.attr.state_focused}},new int[]{ON_ACCENT,ACCENT_DEEP}));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(c,36));lp.setMargins(dp(c,3),dp(c,3),dp(c,3),dp(c,3));b.setLayoutParams(lp);return b;}
    private static android.graphics.drawable.Drawable linkState(){StateListDrawable s=new StateListDrawable();s.addState(new int[]{android.R.attr.state_focused},shape(ACCENT,0));s.addState(new int[]{},shape(0x00000000,0));return s;}
    /**
     * 左侧导航项：常态透明底灰字，聚焦是深青实色块 + 白字白图标，激活是淡青底。
     *
     * 侧边栏本身是白色卡片，导航项如果常态也带边框，整栏会显得全是框。
     */
    static Button navigation(Context c,String name,String key,Runnable action){Button b=button(c,name,action);b.setBackground(navState());b.setTextColor(new android.content.res.ColorStateList(new int[][]{{android.R.attr.state_focused},{android.R.attr.state_selected},{-android.R.attr.state_selected}},new int[]{ON_ACCENT,ACCENT_DEEP,MUTED}));final NavIcon icon=new NavIcon(key,dp(c,22));icon.setBounds(0,0,dp(c,22),dp(c,22));b.setTag(icon);if(tv(c)){b.setCompoundDrawables(icon,null,null,null);b.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);}else{b.setCompoundDrawables(null,icon,null,null);b.setGravity(Gravity.CENTER);}b.setCompoundDrawablePadding(dp(c,10));b.setPadding(dp(c,14),dp(c,4),dp(c,14),dp(c,4));b.setTextSize(tv(c)?16:12);
        // 聚焦底是深青实色块，图标必须换成白色才看得见。
        // 颜色在这里显式定死，不依赖 compound drawable 的状态回传 —— 在 Android 5.0 上
        // 有失焦后图标仍是白色的风险。
        b.setOnFocusChangeListener((v,focused)->icon.setColor(focused?ON_ACCENT:(b.isSelected()?ACCENT:MUTED)));return b;}
    /**
     * 标记导航项的激活态。
     *
     * 选中态除了底色与文字，图标也要从灰变青绿 —— 一并在这里处理，
     * 免得图标颜色留在上一次聚焦时留下的白色。
     */
    static void selectNav(Button b,boolean selected){b.setSelected(selected);Object tag=b.getTag();if(tag instanceof NavIcon)((NavIcon)tag).setColor(b.isFocused()?ON_ACCENT:(selected?ACCENT:MUTED));}
    private static android.graphics.drawable.Drawable navState(){StateListDrawable s=new StateListDrawable();s.addState(new int[]{android.R.attr.state_focused},shape(ACCENT_DEEP,0));s.addState(new int[]{android.R.attr.state_selected},shape(ACCENT_SOFT,0));s.addState(new int[]{},shape(0x00000000,0));return s;}
    /**
     * 方块图标按钮：返回 / 回首页等二级页面入口统一用它。
     * 电视端没有浏览器地址栏，返回必须是显式可见的实体按钮。
     */
    static Button icon(Context c,String key,String label,Runnable action){Button b=new Button(c);b.setBackground(iconState());b.setMinWidth(dp(c,48));b.setMinHeight(dp(c,48));b.setPadding(dp(c,12),0,dp(c,12),0);final NavIcon icon=new NavIcon(key,dp(c,22),ACCENT);icon.setBounds(0,0,dp(c,22),dp(c,22));b.setCompoundDrawables(icon,null,null,null);b.setGravity(Gravity.CENTER);b.setContentDescription(label);focusable(b,c);b.setOnClickListener(v->action.run());plain(b);b.setOnFocusChangeListener((v,focused)->icon.setColor(focused?ON_ACCENT:ACCENT));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(c,48),dp(c,48));lp.setMargins(dp(c,3),dp(c,3),dp(c,3),dp(c,3));b.setLayoutParams(lp);return b;}
    /**
     * 图标按钮的底：常态白块，聚焦深青实色块（配白色图标）。
     *
     * 不复用 focus()：那里的淡青块适合压深色文字，而图标按钮里是图标，
     * 淡青底上的青绿图标几乎看不见，必须用深底白图标。
     */
    private static android.graphics.drawable.Drawable iconState(){StateListDrawable s=new StateListDrawable();s.addState(new int[]{android.R.attr.state_focused},shape(ACCENT_DEEP,0));s.addState(new int[]{},shape(CARD,0));return s;}
    /** 元信息标签：类型 / 年份 / 地区 */
    static TextView tag(Context c,String value){TextView t=text(c,value,13);t.setTextColor(MUTED);t.setBackground(shape(CHIP,0));t.setPadding(dp(c,10),dp(c,5),dp(c,10),dp(c,5));t.setSingleLine(true);return t;}
    /** 标签：键 + 值（类型 欧美剧） */
    static TextView tagPair(Context c,String key,String value){TextView t=text(c,"",13);t.setTextColor(MUTED);t.setBackground(shape(CHIP,0));t.setPadding(dp(c,10),dp(c,5),dp(c,10),dp(c,5));t.setSingleLine(true);t.setText(key+" "+value);return t;}
    /**
     * 可切换方块：选中为淡青底 + 青绿字（片源 / 线路）。
     *
     * 除了选中态，还必须有独立的聚焦态 ——
     * 电视上用遥控器移动焦点时，如果一个控件只有「选中」和「常态」两种样子，
     * 焦点移过去是完全没有视觉反馈的，用户根本不知道自己停在哪一项、
     * 按 OK 会不会有反应。
     *
     * 扁平化之后没有描边可用，靠色块深浅分三档：
     *   常态 = 白卡 + 深字；选中 = 淡青底 + 青绿字；
     *   聚焦 = 青绿实心 + 白字（同时选中时更深一档）。
     */
    static Button chipOutline(Context c,String name,boolean active,Runnable action){Button b=button(c,name,action);b.setTextSize(14);b.setSingleLine(true);b.setSelected(active);StateListDrawable s=new StateListDrawable();s.addState(new int[]{android.R.attr.state_selected,android.R.attr.state_focused},shape(ACCENT_DEEP,0));s.addState(new int[]{android.R.attr.state_selected},shape(ACCENT_SOFT,0));s.addState(new int[]{android.R.attr.state_focused},shape(ACCENT,0));s.addState(new int[]{},shape(CARD,0));b.setBackground(s);b.setTextColor(new android.content.res.ColorStateList(new int[][]{{android.R.attr.state_selected,android.R.attr.state_focused},{android.R.attr.state_selected},{android.R.attr.state_focused},{-android.R.attr.state_selected}},new int[]{ON_ACCENT,ACCENT_DEEP,ON_ACCENT,TEXT}));plain(b);return b;}
    /** 可切换方块：选中为青绿实心（当前剧集），聚焦为深青实色块 */
    static Button chipSolid(Context c,String name,boolean active,Runnable action){Button b=button(c,name,action);b.setTextSize(14);b.setSingleLine(true);b.setSelected(active);StateListDrawable s=new StateListDrawable();s.addState(new int[]{android.R.attr.state_selected,android.R.attr.state_focused},shape(ACCENT_DEEP,0));s.addState(new int[]{android.R.attr.state_selected},shape(ACCENT,0));s.addState(new int[]{android.R.attr.state_focused},shape(ACCENT_DEEP,0));s.addState(new int[]{},shape(CARD,0));b.setBackground(s);b.setTextColor(new android.content.res.ColorStateList(new int[][]{{android.R.attr.state_selected,android.R.attr.state_focused},{android.R.attr.state_selected},{android.R.attr.state_focused},{-android.R.attr.state_selected}},new int[]{ON_ACCENT,ON_ACCENT,ON_ACCENT,MUTED}));plain(b);return b;}
    /**
     * 搜索框：输入框与青绿搜索按钮拼成一个整体。
     *
     * 结构（与桌面端 search 一致）：
     *   [ 输入文字 + 清除 ×            | 🔍 ]
     *   ↑ 浅灰底                          ↑ 青绿实心 + 白色放大镜
     * 扁平化后两半都是直角色块，中间不留缝、不留描边断口。
     *
     * 聚焦态：输入区底色换成实心淡青 —— 描边去掉之后只能靠底色区分焦点，
     * 遥控器把焦点移进输入框时必须能看出来「现在可以打字了」。
     */
    static LinearLayout searchField(Context c,String hint,EditText field,Runnable action){
        LinearLayout row=new LinearLayout(c);row.setOrientation(LinearLayout.HORIZONTAL);
        int h=dp(c,tv(c)?52:48),r=dp(c,12);

        // 输入区：直角色块，右边留给搜索按钮
        EditText e=field!=null?field:input(c,hint);
        e.setBackground(null);e.setPadding(dp(c,14),0,dp(c,8),0);e.setSingleLine(true);
        e.setTextSize(17);e.setTextColor(TEXT);e.setHintTextColor(FAINT);
        if(e.getHint()==null||e.getHint().length()==0)e.setHint(hint);

        // 清除按钮：输入有内容时才出现，用 × 划掉当前输入
        Button clear=new Button(c);clear.setPadding(dp(c,6),0,dp(c,14),0);
        NavIcon cross=new NavIcon("close",dp(c,18),MUTED);cross.setBounds(0,0,dp(c,18),dp(c,18));
        clear.setCompoundDrawables(null,null,cross,null);clear.setGravity(Gravity.CENTER);
        clear.setContentDescription("清除搜索词");clear.setFocusable(true);clear.setVisibility(View.GONE);
        clear.setOnClickListener(v->{e.setText("");});
        // 遥控器聚焦到 × 上时给一个淡青圆底，图标同时转深青
        clear.setOnFocusChangeListener((v,focused)->{
            clear.setBackground(focused?circle(ACCENT_SOFT):null);
            cross.setColor(focused?ACCENT_DEEP:MUTED);
        });

        // 容器右边不留内边距 —— 输入区必须和青绿按钮严丝合缝；
        // × 与按钮之间的空隙由 × 按钮自身的右内边距提供
        LinearLayout input=new LinearLayout(c);input.setOrientation(LinearLayout.HORIZONTAL);input.setGravity(Gravity.CENTER_VERTICAL);
        input.setPadding(dp(c,6),0,0,0);
        input.setBackground(searchInput(r,false));
        input.addView(e,new LinearLayout.LayoutParams(0,-1,1));
        input.addView(clear,new LinearLayout.LayoutParams(dp(c,36),-1));
        row.addView(input,new LinearLayout.LayoutParams(0,h,1));

        // 搜索按钮：青绿实心 + 白色放大镜，紧贴输入区
        //
        // 用 ImageButton 而不是 Button + compoundDrawables：
        // Android 5.0 的 Button 即使文本为空、drawable 放在 drawableStart，
        // 也会在「文本块」前预留 compoundDrawablePadding，图标整体左移近 30px
        // （实测偏移 -28.5px，明显不在按钮正中）。ImageButton 由 ImageView 自行居中。
        ImageButton go=new ImageButton(c);
        NavIcon glass=new NavIcon("search",dp(c,26),ON_ACCENT);
        go.setImageDrawable(glass);
        go.setScaleType(ImageView.ScaleType.FIT_CENTER);
        go.setPadding(0,0,0,0);
        go.setBackground(searchButton(r));go.setContentDescription("搜索");
        focusable(go,c);
        go.setOnClickListener(v->action.run());
        if(Build.VERSION.SDK_INT>=21)go.setStateListAnimator(null);
        // 负边距：按钮往左压 2dp，两块看上去是同一个控件的两半，而不是拼在一起的两个控件
        LinearLayout.LayoutParams goLp=new LinearLayout.LayoutParams(dp(c,tv(c)?64:58),h);
        goLp.setMargins(-dp(c,2),0,0,0);
        row.addView(go,goLp);

        // 聚焦输入框时输入区换底色、清除按钮跟着亮起
        e.setOnFocusChangeListener((v,focused)->{
            input.setBackground(searchInput(r,focused));
            cross.setColor(focused?ACCENT_DEEP:MUTED);
        });
        // 有文字才显示清除按钮；文字变化时同步
        e.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int a,int b,int c){}
            public void onTextChanged(CharSequence s,int a,int b,int c){}
            public void afterTextChanged(android.text.Editable s){clear.setVisibility(s.length()==0?View.GONE:View.VISIBLE);}
        });
        clear.setVisibility(e.getText().length()==0?View.GONE:View.VISIBLE);

        e.setOnEditorActionListener((v,id,event)->{if(id==android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH){action.run();return true;}return false;});
        return row;
    }
    /**
     * 输入区背景：纯色块，直角无描边。
     *
     * 聚焦态换成实心淡青块 —— 描边去掉之后只能靠底色区分焦点，
     * 「现在可以打字了」这件事必须一眼看得出来。
     */
    private static android.graphics.drawable.Drawable searchInput(int radius,boolean focused){
        GradientDrawable d=new GradientDrawable();
        d.setColor(focused?ACCENT_LIGHT:Color.rgb(238,235,228));
        return d;
    }
    /** 搜索按钮背景：青绿实心；聚焦时加深一档，遥控器下更显眼 */
    private static android.graphics.drawable.Drawable searchButton(int radius){
        StateListDrawable s=new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused},shape(ACCENT_DEEP,0));
        s.addState(new int[]{},shape(ACCENT,0));
        return s;
    }
    /** 统一顶栏：左箭头返回 + 标题（+ 右侧动作） */
    static LinearLayout topBar(Context c,String title,Runnable back,Runnable home){LinearLayout bar=row(c);bar.setPadding(dp(c,10),dp(c,8),dp(c,10),dp(c,8));if(back!=null)bar.addView(icon(c,"back","返回上一页",back));TextView titleView=heading(c,title);titleView.setSingleLine(true);titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);bar.addView(titleView,new LinearLayout.LayoutParams(0,-2,1));if(home!=null)bar.addView(icon(c,"home","回首页",home));return bar;}
    static void inset(android.app.Activity a,View root){root.setBackgroundColor(BG);root.setOnApplyWindowInsetsListener((v,insets)->{safeInsets(v,insets);return insets;});if(tv(a))a.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);else lightBars(a);}
    /** 播放页用深色底：视频观看需要沉浸感，浅色边框非常刺眼 */
    static void insetDark(android.app.Activity a,View root){root.setBackgroundColor(DARK);root.setOnApplyWindowInsetsListener((v,insets)->{safeInsets(v,insets);return insets;});if(tv(a))a.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);}
    /**
     * 系统栏避让的兜底入口。
     *
     * 系统栏数值只影响留白，不影响功能，所以任何异常都不能让它把应用带崩 ——
     * 数值算不出来时保持原样（不设内边距），页面照样能用。
     */
    private static void safeInsets(View v,android.view.WindowInsets insets){try{applyInsets(v,insets);}catch(Throwable ignored){}}
    /**
     * 系统栏避让。
     *
     * Android 15（API 35）对 targetSdk 35 的应用强制全面屏：窗口不再被系统栏内缩，
     * 内容会一直画到屏幕边缘，必须自己把系统栏的高度让出来。
     *
     * 只有 Android 15 需要改口径。API 21~34 上窗口仍由系统内缩，
     * 内容拿到的旧接口数值就是当前已生效的内边距，再加一次会变成双重内边距。
     * API 35 起旧接口在全面屏下取不到真实的状态栏/导航栏高度，
     * 必须换成 getInsets(Type.systemBars())，否则底部导航会被压在系统手势条下面、点不到。
     */
    private static void applyInsets(View v,android.view.WindowInsets insets){
        int left,top,right,bottom;
        if(Build.VERSION.SDK_INT>=35){
            android.graphics.Insets bars=insets.getInsets(android.view.WindowInsets.Type.systemBars()|android.view.WindowInsets.Type.displayCutout());
            left=bars.left;top=bars.top;right=bars.right;bottom=bars.bottom;
            // 键盘弹出时也要让开：全面屏下窗口不再为键盘缩高，
            // 不主动避让的话软键盘会正好盖住底部的导航栏，导航按钮又点不到了。
            //
            // 但 IME 的 inset 只能在键盘可见时取 —— 官方约定 getInsets(Type.ime())
            // 在键盘不可见时会抛 IllegalArgumentException（键盘高度取决于当前
            // EditorInfo 与输入法状态，不可见时拿不到）。
            // onApplyWindowInsets 的第一次回调恰好就发生在键盘还没弹出的时刻，
            // 所以这里必须先用 isVisible(Type.ime()) 判断，否则应用一打开就崩。
            if(insets.isVisible(android.view.WindowInsets.Type.ime()))bottom=Math.max(bottom,insets.getInsets(android.view.WindowInsets.Type.ime()).bottom);
        }else{
            left=insets.getSystemWindowInsetLeft();top=insets.getSystemWindowInsetTop();
            right=insets.getSystemWindowInsetRight();bottom=insets.getSystemWindowInsetBottom();
        }
        v.setPadding(left,top,right,bottom);
    }
    /**
     * 浅色系统栏图标。
     *
     * 只在 Android 15 起生效：targetSdk 35 的应用在 Android 15 上系统栏强制透明，
     * 底下就是我们的暖白底 —— 默认的白色图标压在暖白底上几乎看不见，必须换成深色。
     * Android 11~14 的系统栏仍是主题里的深色底（#0B101A），白色图标本来就对，
     * 那几版维持原样，不做改动。
     *
     * 整段包 try/catch：系统栏外观只是「好不好看」，算不出来就保持系统默认，
     * 绝不能因为它把应用带崩 —— 这条路径只在 API 35 上执行，出错就是白屏闪退。
     */
    private static void lightBars(android.app.Activity a){
        if(Build.VERSION.SDK_INT<35)return;
        try{
            android.view.WindowInsetsController controller=a.getWindow().getInsetsController();
            if(controller==null)return;
            int mask=android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS;
            if(Build.VERSION.SDK_INT>=26)mask|=android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            controller.setSystemBarsAppearance(mask,mask);
        }catch(Throwable ignored){}
    }
    static View poster(Context c,Catalog.Item item,int width,Runnable click){
        LinearLayout card=column(c);card.setBackground(focus());card.setPadding(dp(c,3),dp(c,3),dp(c,3),dp(c,8));focusable(card,c);card.setClickable(true);card.setContentDescription(item.title);card.setOnClickListener(v->click.run());
        FrameLayout art=new FrameLayout(c);art.setBackground(shape(Color.rgb(238,235,228),0));TextView placeholder=label(c,"静观 TV",12,0x55A1A1AA);placeholder.setGravity(Gravity.CENTER);art.addView(placeholder,new FrameLayout.LayoutParams(-1,-1));ImageView image=new ImageView(c);image.setScaleType(ImageView.ScaleType.CENTER_CROP);art.addView(image,new FrameLayout.LayoutParams(-1,-1));
        TextView rating=label(c,item.rating.isEmpty()?item.year:item.rating,12,RATING);rating.setBackground(pill(c,0xF2FFFFFF));rating.setPadding(dp(c,8),dp(c,3),dp(c,8),dp(c,3));FrameLayout.LayoutParams badge=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.RIGHT);badge.setMargins(0,dp(c,5),dp(c,5),0);art.addView(rating,badge);if(rating.getText().length()==0)rating.setVisibility(View.GONE);
        if(!item.poster.isEmpty())Images.load(c,item.poster,image);
        card.addView(art,new LinearLayout.LayoutParams(-1,dp(c,width*3/2)));TextView title=label(c,item.title,tv(c)?16:14,TEXT);title.setTypeface(null,Typeface.BOLD);title.setMaxLines(1);title.setEllipsize(android.text.TextUtils.TruncateAt.END);title.setPadding(dp(c,6),dp(c,8),dp(c,4),0);card.addView(title);
        String subtitle=!item.remarks.isEmpty()?item.remarks:(!item.category.isEmpty()?item.category:item.sourceName);TextView sub=label(c,subtitle,12,MUTED);sub.setMaxLines(1);sub.setEllipsize(android.text.TextUtils.TruncateAt.END);sub.setPadding(dp(c,6),dp(c,4),dp(c,4),0);card.addView(sub);
        // 聚焦时整张卡片换成实心淡青底，副标题的灰字在淡青底上偏糊 —— 聚焦时换成深色字
        final TextView subView=sub;
        card.setOnFocusChangeListener((v,focused)->{subView.setTextColor(focused?TEXT:MUTED);if(tv(c))scale(v,focused?1.035f:1f);});return card;
    }
    /**
     * 聚焦时的轻微放大。
     *
     * 不用 View.animate()：它在 Android 5.0 上用 WeakReference 持有目标视图，
     * 视图一旦被回收或移出窗口，PropertyValuesHolder 就会拿到 null 目标，
     * 在 nCallFloatMethod 里触发 JNI 检查失败并 abort 整个进程（电视上直接闪退）。
     * 改为自己管理缩放值与动画，只在视图仍挂在窗口上时才启动。
     */
    private static void scale(View v,float value){
        v.setScaleX(value);v.setScaleY(value);
        android.animation.ValueAnimator animator=android.animation.ValueAnimator.ofFloat(v.getScaleX(),value);
        animator.setDuration(120);animator.addUpdateListener(a->{Object value2=a.getAnimatedValue();if(value2==null)return;float f=(Float)value2;if(v.isShown()||f==value)v.setScaleX(f);v.setScaleY(f);});
        animator.start();
    }
    /**
     * 淡入淡出。
     *
     * 同样避开 View.animate()：Android 5.0 的 ViewPropertyAnimator 用 WeakReference
     * 持有目标，视图被移除后动画目标会变成 null 并在 JNI 层 abort 进程。
     * 这里自己驱动 alpha 值，结束后再执行收尾动作。
     *
     * 可见性判断用 isShown()：它在视图挂到已显示的窗口上时才为 true。
     * 不能用 getWindowVisibility() —— 刚 setVisibility(VISIBLE) 的那一帧，
     * 窗口还没完成绘制，它返回 GONE，于是 alpha 永远停在 0，
     * 界面看着是「面板没弹出来」，而调用方的状态位却已经是「已显示」，
     * 后续按键全部被状态判断挡掉，播放器就再也唤不出控制条了。
     */
    static void fade(View v,float to,int duration,Runnable end){
        android.animation.ValueAnimator animator=android.animation.ValueAnimator.ofFloat(v.getAlpha(),to);
        animator.setDuration(duration);animator.addUpdateListener(a->{Object value=a.getAnimatedValue();if(value==null)return;float f=(Float)value;if(v.isShown()||f==to)v.setAlpha(f);});
        animator.addListener(new android.animation.AnimatorListenerAdapter(){
            @Override public void onAnimationEnd(android.animation.Animator animation){if(end!=null)end.run();}
        });
        animator.start();
    }
    /**
     * 复用已有卡片：只更新内容，不重建视图层级。
     *
     * 网格滚动时 Adapter 复用被回收的卡片，若每次都新建 View，
     * 会持续创建 View 并触发图片加载，滚动明显发涩。
     * 卡片层级固定为 [封面容器, 标题, 副标题]，封面容器内固定为 [占位, 图片, 角标]。
     */
    static void rebindPoster(Context c,View card,Catalog.Item item,Runnable click){
        if(!(card instanceof LinearLayout))return;
        LinearLayout root=(LinearLayout)card;
        if(root.getChildCount()<3||!(root.getChildAt(0) instanceof FrameLayout))return;
        FrameLayout art=(FrameLayout)root.getChildAt(0);
        if(art.getChildCount()<3)return;
        ImageView image=(ImageView)art.getChildAt(1);
        TextView rating=(TextView)art.getChildAt(2);
        TextView title=(TextView)root.getChildAt(1);
        TextView sub=(TextView)root.getChildAt(2);
        card.setContentDescription(item.title);
        card.setOnClickListener(v->click.run());
        String badge=item.rating.isEmpty()?item.year:item.rating;
        rating.setText(badge);rating.setVisibility(badge.length()==0?View.GONE:View.VISIBLE);
        title.setText(item.title);
        String subtitle=!item.remarks.isEmpty()?item.remarks:(!item.category.isEmpty()?item.category:item.sourceName);
        sub.setText(subtitle);
        if(!item.poster.isEmpty())Images.load(c,item.poster,image);
        else{image.setTag(null);image.setImageDrawable(null);}
    }
    /** 按文案把胶囊行里对应的一项标成选中态（单选组用） */
    static void markChip(LinearLayout row,String text){for(int n=0;n<row.getChildCount();n++)if(row.getChildAt(n) instanceof Button){Button b=(Button)row.getChildAt(n);b.setSelected(text.equals(b.getText().toString()));}}
    /** 单行输入框：纯白色块，替代系统下划线样式 */
    static EditText input(Context c,String hint){EditText e=new EditText(c);e.setSingleLine(true);e.setTextSize(16);e.setTextColor(TEXT);e.setHintTextColor(FAINT);e.setHint(hint);e.setBackground(shape(SURFACE,0));e.setPadding(dp(c,14),0,dp(c,14),0);return e;}
    static void message(Context c,String text){UiDialog.message((Activity)c,"提示",text);}
    static void toast(Context c,String text){Toast.makeText(c,text,Toast.LENGTH_SHORT).show();}
}
