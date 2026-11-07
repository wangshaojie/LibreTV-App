package com.wangshaojie.libretv;
import android.app.*;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.drawable.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.media3.common.*;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.ui.PlayerView;
import org.json.*;
import java.util.*;

/**
 * 播放页。
 *
 * 两端各按各自的使用习惯排版，但共用同一套播放逻辑与同一份代码路径：
 *
 *   手机（爱奇艺 / 腾讯视频那一类）：画面全屏铺满，上下各压一层渐变蒙层。
 *   顶部是标题，底部是进度条 + 一排图标，点屏幕中间呼出/收起，单指左右滑动调进度，
 *   双击画面快进快退。这是「拿手机看视频」的习惯：手指在画面上滑动。
 *
 *   电视（TVBox 那一类）：画面铺满，控制区固定在下方一行，永远不自动隐藏 ——
 *   遥控器用户需要它一直在，因为没有触屏也没有手势。焦点用方向键在图标间移动，
 *   焦点框是一圈明显的描边，和电视上其他应用一致。
 *
 * 关键约束（Android 5.0）：
 *   - 自定义 Button 一律 stateListAnimator=null（Material 抬升动画在 5.0 上会 native abort）
 *   - 不用 View.animate()（WeakReference 目标为空会 abort）
 *   - 可见性判定用 isShown()，不用 getWindowVisibility()
 */
@androidx.media3.common.util.UnstableApi
public final class PlayerActivity extends Activity {
    private ExoPlayer player;private PlayerView video;private Store store;private List<Catalog.Item> items=new ArrayList<>();private int index;
    private FrameLayout root;private LinearLayout titleBar,panel;private TextView title,status,osd,clock;private SeekBar seek;private Button playButton,prevButton,nextButton;
    // 电视端的两行控制区：上行是进度条，下行是按钮。
    // 焦点要在两行之间搬，API 21 上没有 View.setNextFocusUp/Down（那是 API 26 才有的），
    // 所以自己在 dispatchKeyEvent 里搬。
    private LinearLayout tvProgRow,tvButtonRow;private Button titleBack;
    // 电视端按钮行的焦点顺序表（从左到右）。左右键按这张表走，不依赖框架的几何搜索。
    private final List<Button> tvButtons=new ArrayList<>();
    // 播放时长（毫秒）。SeekBar 的刻度是 0~10000，这里存真实时长用来换算
    private long durationMs;
    // 出错时才出现的两个动作键：重试（重新连接这条流）与换线路（退回详情页换一条线路）。
    // 平时藏起来 —— 播放页平时不该摆一排用不上的按钮；但出错的提示文案明确让用户
    // 「按重试」或「点换线路」，所以这两个键必须真的在面板上，不能只在文案里提。
    private Button retryButton,lineButton;
    private long resume;private boolean autoplay=true;private boolean panelOn,titleOn,osdOn,locked,dragging;
    private float playbackSpeed=1f;
    // 手机端全屏态：横屏 + 收起控制面板与标题栏，只剩画面。
    // 电视端恒为 false —— 电视本来就是横屏全屏，没有切换的必要。
    private boolean fullscreen;
    // Live reconnect bookkeeping: -1 means "never tried", recoverCount is the number of
    // consecutive failed reconnects and is reset as soon as the stream plays again.
    private long lastRecoveryAt=-1L;private int recoverCount;private boolean reconnecting,connecting;
    // 缓冲指示：播放中卡顿时画面是静止的，没有提示就会被当成应用死了。
    private View bufferView;private TextView bufferLabel;private boolean buffering;
    /** 加载态持续多久才判定为「缓冲中」——短暂卡顿不提示，提示闪一下更烦。 */
    private static final long BUFFER_HINT_DELAY=800;
    private final Handler ui=new Handler(Looper.getMainLooper());
    // 控制区空闲多久后自动淡出（毫秒）。
    // 电视端比手机端长：遥控器用户是「按一下扫一眼、再决定看多久」，
    // 8 秒足够看清时间码和按钮；太短的话控制区刚出现就被收走，
    // 反而让人以为按键没反应。
    private static final long PANEL_IDLE_MS=4200,TV_PANEL_IDLE_MS=8000;
    private final Runnable hideTitleTask=()->{if(!panelOn)hideTitle();};
    private final Runnable hidePanelTask=()->hidePanel(360);
    private final Runnable hideOsdTask=()->{osdOn=false;Ui.fade(osd,0f,280,()->{if(!osdOn)osd.setVisibility(View.GONE);});};
    private final Runnable tickTask=new Runnable(){@Override public void run(){refreshProgress();ui.postDelayed(this,400);}};
    // 手机端手势：单指横向拖动调进度，拖动时显示目标时间气泡
    private float touchDownX,touchDownY;private long touchDownAt;private boolean scrubbing;private long scrubBase;

    @Override public void onCreate(Bundle b){super.onCreate(b);store=new Store(this);if(Ui.tv(this))setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        try{String file=getIntent().getStringExtra("playlistFile");JSONArray a=new JSONArray(file==null?getIntent().getStringExtra("items"):Playback.read(this,file));for(int n=0;n<a.length();n++)items.add(Catalog.Item.from(a.getJSONObject(n)));index=Math.max(0,Math.min(items.size()-1,getIntent().getIntExtra("index",0)));if(items.isEmpty())throw new Exception();}catch(Exception e){finish();return;}
        resume=store.position(items.get(index).url);build();setContentView(root);immersive();
        // 缓冲提示靠轮询播放状态（这台设备上的 media3 不派发加载态回调）
        ui.postDelayed(bufferWatch,400);
    }

    private void build(){
        // 播放页保持深色沉浸：根容器用深色版 inset（背景 DARK），视频区域仍是主体
        root=new FrameLayout(this);root.setFocusableInTouchMode(true);Ui.insetDark(this,root);
        // 视频要铺满整块屏幕，所以不把系统栏内边距加到根容器上（insetDark 默认会加），
        // 这里把内边距清零，只保留它的深色背景与沉浸式系统栏设置。
        root.setPadding(0,0,0,0);
        root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(0,0,0,0);return insets;});
        // 炭黑底（不是纯黑，纯黑在老电视上边缘会发毛），视频铺满后基本看不见它，
        // 露出来的那几像素缝隙也跟画面融在一起。
        root.setBackground(new android.graphics.drawable.ColorDrawable(0xFF0B0D10));
        video=new PlayerView(this);video.setUseController(false);video.setBackgroundColor(Color.BLACK);video.setFocusable(false);
        video.setOnTouchListener(this::onVideoTouch);
        root.addView(video,new FrameLayout.LayoutParams(-1,-1));

        // 扁平化之后不再有环境光渐变与胶片颗粒：两者都是装饰性图层，
        // 扁平主题只保留画面本身 + 纯色块控制层。
        osd=Ui.text(this,"",16);osd.setGravity(Gravity.CENTER);osd.setBackground(Ui.glass(0xCC12151A,0x26FFFFFF,20));osd.setTextColor(0xFFF2F4F3);osd.setVisibility(View.GONE);
        root.addView(osd,new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER));

        // 缓冲指示层：画面中央的转圈 + 「缓冲中…」。
        //
        // 为什么要有：播放中卡顿时画面是静止的，又没有提示，
        // 用户只会以为应用死了（或者以为电视坏了）—— 这是最容易被当成故障的体验。
        // 主流播放器都会在卡顿时给一个明确的加载提示。
        bufferView=buildBufferView();
        root.addView(bufferView,new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER));
        bufferView.setVisibility(View.GONE);

        // 标题栏：左上角一枚悬浮玻璃胶囊，而不是一整条贴边的横幅。
        titleBar=buildTitleBar();
        FrameLayout.LayoutParams titleLp=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.START);
        titleLp.setMargins(Ui.dp(this,14),Ui.dp(this,14),Ui.dp(this,14),0);
        root.addView(titleBar,titleLp);titleBar.setVisibility(View.GONE);

        // 控制区：底部悬浮的玻璃卡片，左右留边、底部留边，不再是贴着屏幕下沿的一条深色横条。
        // 直接把卡片挂在 root 上、用 FrameLayout 的 margin 控制边距 —— 多包一层容器的话，
        // 子节点的 bounds 坐标系会错位（相对父容器而不是相对 root），布局工具读到的位置全是错的。
        panel=buildPanel();
        int side=Ui.dp(this,Ui.tv(this)?56:14);
        FrameLayout.LayoutParams panelLp=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);
        panelLp.setMargins(side,0,side,Ui.dp(this,Ui.tv(this)?20:14));
        root.addView(panel,panelLp);panel.setVisibility(View.GONE);
    }

    // ================= 顶栏 =================
    /**
     * 缓冲指示层：深色直角块 + 转圈圆环 + 「缓冲中…」。
     *
     * 扁平风：纯色块、无描边、无投影；转圈是「本身就是圆」的地方，圆环保留。
     * 圆环自己画（Ui.RingDrawable）而不是用系统 ProgressBar：
     * 系统那个的颜色跟着系统主题走，老电视上浅色主题会变成一个深色圆盘，
     * 放在深色画面上等于看不见。
     */
    private View buildBufferView(){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setGravity(Gravity.CENTER);
        box.setBackground(Ui.glass(0xCC12151A,0x26FFFFFF,0));
        int pad=Ui.dp(this,18);box.setPadding(pad,pad,pad,pad);
        View ring=new View(this);
        ring.setBackground(new Ui.RingDrawable(0xFF6FA9A2,Ui.dp(this,4)));
        int size=Ui.dp(this,44);
        box.addView(ring,new LinearLayout.LayoutParams(size,size));
        bufferLabel=Ui.text(this,"缓冲中…",14);bufferLabel.setTextColor(0xFFEDEFF1);
        LinearLayout.LayoutParams labelLp=new LinearLayout.LayoutParams(-2,-2);labelLp.topMargin=Ui.dp(this,10);
        box.addView(bufferLabel,labelLp);
        // 转起来：直接对 View 的 rotation 做动画（不是 View.animate()，API 21 上那套有问题）
        android.animation.ObjectAnimator spin=android.animation.ObjectAnimator.ofFloat(ring,"rotation",0f,360f);
        spin.setDuration(900);spin.setRepeatCount(android.animation.ObjectAnimator.INFINITE);
        spin.setRepeatMode(android.animation.ObjectAnimator.RESTART);spin.start();
        return box;
    }
    /**
     * 顶栏：返回 + 标题。
     *
     * 两端结构一致，只是排版密度不同：
     *   手机 —— 返回箭头 + 标题单行（字号小一点，标题占满剩余宽度）
     *   电视 —— 返回箭头 + 标题（字号大一点，两端留白宽一点）
     *
     * 形态是一枚左上角的深色实色块：扁平化之后不再有半透明、发丝描边与大圆角。
     * 不是一条贴边的横幅 —— 横幅会把画面上沿整条压住，而画面才是主角。
     */
    private LinearLayout buildTitleBar(){
        LinearLayout bar=new LinearLayout(this);bar.setOrientation(LinearLayout.HORIZONTAL);bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackground(Ui.glass(0xB014171C,0x26FFFFFF,22));
        bar.setPadding(Ui.dp(this,6),Ui.dp(this,4),Ui.dp(this,16),Ui.dp(this,4));
        titleBack=darkIcon("back","返回上一页",this::finish);bar.addView(titleBack);
        title=Ui.text(this,"",Ui.tv(this)?17:15);title.setTextColor(0xF2F5F5F7);title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        // 胶囊宽度是自适应的，标题必须自己封顶，否则长片名会把胶囊拉成一条横幅
        title.setMaxWidth(Ui.dp(this,Ui.tv(this)?520:240));
        LinearLayout.LayoutParams titleLp=new LinearLayout.LayoutParams(-2,-2);titleLp.setMargins(Ui.dp(this,8),0,0,0);bar.addView(title,titleLp);
        return bar;
    }

    // ================= 控制区 =================
    /**
     * 底部控制区。
     *
     * 手机（爱奇艺式）：进度条独占一行，下面一行是图标按钮 —— 选集 / 倍速 / 画面比例 / 收藏 / 全屏。
     * 全部是图标 + 下方小字，一行排开，不要横向滚动条。
     *
     * 电视（TVBox 式）：一条横排 —— 上一集 / 播放暂停 / 下一集 在左，进度条居中，
     * 右侧一排功能图标（选集 / 倍速 / 画面 / 收藏）。电视上不自动隐藏控制区，
     * 遥控器随时能按方向键找到东西。
     */
    private LinearLayout buildPanel(){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);
        // 扁平化的控制条：纯深色实色块，直角、无描边、无投影。
        // 播放页保持深色（视频观看需要沉浸感），但不再有「磨砂玻璃」那一套装饰。
        box.setBackground(Ui.glass(0xB3171A20,0x24FFFFFF,Ui.tv(this)?22:20));
        int pad=Ui.dp(this,Ui.tv(this)?16:14);
        box.setPadding(pad,Ui.dp(this,Ui.tv(this)?12:10),pad,Ui.dp(this,Ui.tv(this)?12:10));
        status=Ui.text(this,"",13);status.setTextColor(0xCCEDEFF1);status.setVisibility(View.GONE);box.addView(status);
        if(Ui.tv(this))buildTvPanel(box);else buildPhonePanel(box);
        return box;
    }

    /** 手机端控制区：进度条一行 + 图标一行。 */
    private void buildPhonePanel(LinearLayout box){
        // 进度条：当前时间在左，总时长在右
        LinearLayout bar=new LinearLayout(this);bar.setOrientation(LinearLayout.HORIZONTAL);bar.setGravity(Gravity.CENTER_VERTICAL);
        clock=Ui.text(this,"00:00",12);clock.setTextColor(0xB3EDEFF1);bar.addView(clock);
        seek=new SeekBar(this);seek.setMax(10000);seek.setProgressDrawable(progressDrawable());seek.setThumb(thumbDrawable());seek.setKeyProgressIncrement(100);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){@Override public void onProgressChanged(SeekBar sb,int value,boolean fromUser){if(fromUser)clock.setText(fmt(toMs(value)));}@Override public void onStartTrackingTouch(SeekBar sb){dragging=true;}@Override public void onStopTrackingTouch(SeekBar sb){dragging=false;if(player!=null&&durationMs>0)player.seekTo(toMs(sb.getProgress()));}});
        LinearLayout.LayoutParams seekLp=new LinearLayout.LayoutParams(0,Ui.dp(this,34),1);seekLp.setMargins(Ui.dp(this,8),0,Ui.dp(this,8),0);bar.addView(seek,seekLp);
        TextView total=Ui.text(this,"00:00",12);total.setTextColor(0x99EDEFF1);bar.addView(total);total.setTag("total");
        box.addView(bar);

        // 图标行：图标 + 小字，纵向排列，宽度均分
        LinearLayout icons=new LinearLayout(this);icons.setOrientation(LinearLayout.HORIZONTAL);icons.setGravity(Gravity.CENTER);
        // 手机端以前没有播放 / 暂停键 —— 画面上单击只是呼出控制面板，
        // 面板里能选集、能倍速、能收藏，唯独不能暂停，非得退出播放页才算停。
        // 放在最左边，和电视端「上一集 / 播放 / 下一集」里的播放键一个位置。
        // 复用 playButton 字段，图标就跟着播放状态自动在「播放 / 暂停」之间换。
        playButton=darkIcon("play","播放",this::togglePlay,42);
        icons.addView(playButton);
        icons.addView(darkIcon("list","选集",this::pickEpisode,42));
        icons.addView(darkIcon("speed","倍速",this::pickSpeed,42));
        icons.addView(darkIcon("aspect","画面比例",this::pickRatio,42));
        icons.addView(darkIcon("star","收藏",this::toggleFavorite,42));
        icons.addView(darkIcon("fullscreen","全屏",this::toggleFullscreen,42));
        addErrorActions(icons);
        box.addView(icons);
    }

    /**
     * 挂上「重试」「换线路」两个键，默认隐藏，出错时才显示。
     *
     * 换线路 = 退回详情页（finish），那里可以换一条线路；
     * 重试 = 重新连接当前这条流。
     */
    private void addErrorActions(LinearLayout row){
        retryButton=darkIcon("retry","重试",this::prepare);retryButton.setVisibility(View.GONE);row.addView(retryButton);
        lineButton=darkIcon("swap","换线路",this::finish);lineButton.setVisibility(View.GONE);row.addView(lineButton);
    }
    /** 出错时显示两个动作键。 */
    private void showErrorActions(){if(retryButton!=null)retryButton.setVisibility(View.VISIBLE);if(lineButton!=null)lineButton.setVisibility(View.VISIBLE);}
    /** 重新连接 / 播放正常时收起它们。 */
    private void hideErrorActions(){if(retryButton!=null)retryButton.setVisibility(View.GONE);if(lineButton!=null)lineButton.setVisibility(View.GONE);}

    /**
     * 电视端控制区：两行。
     *
     *   上行：当前时间 — 进度条 — 总时长
     *   下行：上一集 / 播放 / 下一集 …… 选集 / 倍速 / 画面 / 收藏
     *
     * 为什么进度条要单独占一行（而不是塞在按钮中间）：
     *   横排时进度条夹在「下一集」和「选集」中间，焦点一旦落到它身上，
     *   左右键就被系统 SeekBar 吃掉 —— 焦点走不到右边的功能键；
     *   而且 SeekBar 改的是自己的刻度，并不会真的 seek 播放器，画面纹丝不动。
     *   这正是「电视上没法快进、也没法选中进度条后面的按钮」的原因。
     *
     * 独立成行之后：上下键在两行之间切换，左右键在进度条上做真正的快进快退
     * （按住连续加速），功能键那一行则一路畅通。
     */
    private void buildTvPanel(LinearLayout box){
        // ——— 上行：进度 ———
        LinearLayout prog=new LinearLayout(this);prog.setOrientation(LinearLayout.HORIZONTAL);prog.setGravity(Gravity.CENTER_VERTICAL);
        tvProgRow=prog;
        clock=Ui.text(this,"00:00",12);clock.setTextColor(0xB3EDEFF1);
        LinearLayout.LayoutParams clockLp=new LinearLayout.LayoutParams(-2,-2);clockLp.setMargins(0,0,Ui.dp(this,8),0);prog.addView(clock,clockLp);
        seek=new SeekBar(this);seek.setMax(10000);seek.setProgressDrawable(progressDrawable());seek.setThumb(thumbDrawable());seek.setKeyProgressIncrement(100);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){@Override public void onProgressChanged(SeekBar sb,int value,boolean fromUser){if(fromUser)clock.setText(fmt(toMs(value)));}@Override public void onStartTrackingTouch(SeekBar sb){dragging=true;}@Override public void onStopTrackingTouch(SeekBar sb){dragging=false;if(player!=null&&durationMs>0)player.seekTo(toMs(sb.getProgress()));}});
        // 进度条在电视上要能拿到焦点（否则遥控器根本没地方调进度）。
        // 电视可能仍处于 touch mode，focusableInTouchMode 不开的话
        // requestFocus 会静默失败 —— 焦点根本搬不过来。
        Ui.focusable(seek,this);
        seek.setOnFocusChangeListener((v,focused)->{
            // ProgressBar 不会把焦点状态同步给 thumb，这里手动换一次，
            // 焦点态的滑块明显更大更亮，电视上一眼能看出焦点在进度条上。
            try{((SeekBar)v).setThumb(thumbDrawable(focused));}catch(Throwable ignore){}
            if(focused)showOsd("← → 快进快退 · ↓ 回到按钮");
        });
        prog.addView(seek,new LinearLayout.LayoutParams(0,Ui.dp(this,30),1));
        TextView total=Ui.text(this,"00:00",12);total.setTextColor(0x99EDEFF1);
        LinearLayout.LayoutParams totalLp=new LinearLayout.LayoutParams(-2,-2);totalLp.setMargins(Ui.dp(this,8),0,0,0);prog.addView(total,totalLp);total.setTag("total");
        box.addView(prog);

        // ——— 下行：按钮 ———
        LinearLayout bar=new LinearLayout(this);bar.setOrientation(LinearLayout.HORIZONTAL);bar.setGravity(Gravity.CENTER_VERTICAL);
        tvButtonRow=bar;
        prevButton=darkIcon("prev","上一集",()->change(-1));bar.addView(prevButton);
        playButton=darkIcon("play","播放",this::togglePlay);bar.addView(playButton);
        nextButton=darkIcon("next","下一集",()->change(1));bar.addView(nextButton);
        // 中间留白：把右边的功能键推到面板右端，和桌面端「控件聚成一组」一个思路
        View spacer=new View(this);bar.addView(spacer,new LinearLayout.LayoutParams(0,1,1));

        // 右侧功能键：只留电视上真正常用的（选集 / 倍速 / 画面 / 收藏），其余收进「更多」弹层
        bar.addView(darkIcon("list","选集",this::pickEpisode));
        bar.addView(darkIcon("speed","倍速",this::pickSpeed));
        bar.addView(darkIcon("aspect","画面比例",this::pickRatio));
        bar.addView(darkIcon("star","收藏",this::toggleFavorite));
        addErrorActions(bar);
        box.addView(bar);

        // 按钮行的左右焦点顺序：自己排一张表，不依赖框架的几何搜索。
        // 框架的搜索在中间插了留白之后表现不稳定（会跳位或者走不出去），
        // 而「焦点卡住走不到后面的按钮」正是这次要修的问题，不能再交给它。
        tvButtons.clear();
        for(int n=0;n<bar.getChildCount();n++){
            View child=bar.getChildAt(n);
            if(child instanceof Button)tvButtons.add((Button)child);
        }
    }

    /** 收藏开关：图标点亮 + 文案反馈。 */
    private void toggleFavorite(){if(items.isEmpty())return;store.toggleFavorite(items.get(index));Ui.toast(this,store.favorite(items.get(index))?"已收藏":"已取消收藏");}

    /**
     * 深底图标按钮：白色线性图标 + 直角深色底，聚焦时换成青绿实色块。
     *
     * 不能直接套浅色主题的 Ui.focus()／Ui.button —— 那是白卡配深字，
     * 放在深色画面上是一块刺眼的白方块。焦点态换成青绿实色块，
     * 遥控器移动焦点时依然看得出来停在哪。
     */
    private Button darkIcon(String key,String label,Runnable run){return darkIcon(key,label,run,46);}

    /**
     * 深底图标按钮（可指定尺寸）。
     *
     * 手机竖屏放不下六个图标时把尺寸调小一档：一行六键 × 52dp = 312dp，
     * 在 360dp 宽的屏幕上只剩 20dp 余量，再多一个键就会挤到边缘。
     * 42dp 的图标在触摸设备上依然够点。
     */
    private Button darkIcon(String key,String label,Runnable run,int sizeDp){
        Button b=new Button(this);b.setAllCaps(false);b.setText("");b.setMinWidth(Ui.dp(this,sizeDp));b.setMinHeight(Ui.dp(this,sizeDp));b.setPadding(Ui.dp(this,12),0,Ui.dp(this,12),0);
        final NavIcon icon=new NavIcon(key,Ui.dp(this,22),0xFFF2F4F3);icon.setBounds(0,0,Ui.dp(this,22),Ui.dp(this,22));
        b.setCompoundDrawables(icon,null,null,null);b.setGravity(Gravity.CENTER);
        b.setBackground(darkState());
        b.setContentDescription(label);Ui.focusable(b,this);b.setOnClickListener(v->run.run());
        // 聚焦底是青绿实色块，图标必须换成白色才看得见；失焦后交回常态白图标
        b.setOnFocusChangeListener((v,focused)->icon.setColor(focused?0xFFFFFFFF:0xFFF2F4F3));
        if(android.os.Build.VERSION.SDK_INT>=21)b.setStateListAnimator(null);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(Ui.dp(this,sizeDp),Ui.dp(this,sizeDp));lp.setMargins(Ui.dp(this,3),Ui.dp(this,3),Ui.dp(this,3),Ui.dp(this,3));b.setLayoutParams(lp);return b;
    }
    /**
     * 深底按钮的背景：常态是略浅于面板的深色块，聚焦时是青绿实色块。
     *
     * 扁平化：直角、无描边、无投影，层次只靠深浅两个色块区分。
     */
    private android.graphics.drawable.Drawable darkState(){
        android.graphics.drawable.StateListDrawable s=new android.graphics.drawable.StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused},flat(Ui.ACCENT));
        s.addState(new int[]{},flat(0xFF2A2F37));return s;
    }
    /** 回首页：复用栈里的主界面实例，不重建（重建会丢掉当前浏览位置） */
    private void goHome(){Intent intent=new Intent(this,MainActivity.class);intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);startActivity(intent);finish();}
    /**
     * 进度条：细轨道 + 青绿渐变微光。
     *
     * 必须是 LayerDrawable（见 Ui.progressBar 的说明），并且 SeekBar 的 max 固定用
     * 10000 —— ClipDrawable 按 level/10000 做水平裁剪，而 ProgressBar 会把
     * seekbar 的 progress 直接当作 level 传下去，所以 max 取 10000 裁剪比例才准。
     */
    private Drawable progressDrawable(){
        android.graphics.drawable.ClipDrawable clip=new android.graphics.drawable.ClipDrawable(new Ui.BarDrawable(Ui.ACCENT,Ui.ACCENT_LIGHT,Ui.dp(this,3)),android.view.Gravity.START,android.graphics.drawable.ClipDrawable.HORIZONTAL);
        LayerDrawable l=new LayerDrawable(new Drawable[]{new Ui.TrackDrawable(0x1FFFFFFF,Ui.dp(this,3)),clip});
        l.setId(0,android.R.id.background);l.setId(1,android.R.id.progress);
        return l;
    }
    private Drawable flat(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);return d;}
    // 进度条：青绿进度 + 半透明白轨道，滑块是实心圆点（深底上黑描边会糊成一团）
    // 滑块有焦点态：电视上进度条拿到焦点时必须一眼看得出来，否则用户不知道
    // 「左右键现在是在调进度」而不是「按键没反应」。
    // 注意 ProgressBar 不会把焦点状态刷新给 thumb，所以焦点变化时要手动换一次 thumb。
    private Drawable thumbDrawable(){return thumbDot(Ui.ACCENT_LIGHT,Ui.dp(this,12));}
    private Drawable thumbDrawable(boolean focused){return focused?thumbDot(0xFFFFFFFF,Ui.dp(this,16)):thumbDrawable();}
    /** 滑块：正圆实色块（圆形本身就是形状语义），不再加描边 */
    private Drawable thumbDot(int fill,int size){GradientDrawable d=new GradientDrawable();d.setShape(GradientDrawable.OVAL);d.setColor(fill);d.setSize(size,size);return d;}

    // One player instance is reused for every episode, channel and route. Releasing the
    // player re-creates the vendor MediaCodec, and on older MStar TV firmware that teardown
    // aborts the process (ACodec.cpp CHECK_EQ on OMX_IndexParamAudioMp3), which leaves the
    // television's video path stuck and makes every later video play with sound but no picture.
    private ExoPlayer ensurePlayer(){
        if(player!=null)return player;
        player=new ExoPlayer.Builder(this,new DefaultRenderersFactory(this).setEnableDecoderFallback(true)).build();
        player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),true);player.setHandleAudioBecomingNoisy(true);
        player.addListener(new Player.Listener(){@Override public void onPlaybackStateChanged(int state){
            // 缓冲提示统一由 bufferWatch 轮询驱动（BufferHint）：
            // 这台设备上 onIsLoadingChanged / STATE_BUFFERING 回调都不一定派发，
            // 靠回调等着就永远等不到 —— 详见 BufferHint 的说明。
            if(state==Player.STATE_READY){connecting=false;locked=false;recoverCount=0;hideErrorActions();endBuffering();if(Ui.tv(PlayerActivity.this))setStatus("");// connecting 期间 scheduleHidePanel 会直接返回（那段时间必须留着控制区），
            // 所以连上之后要补排一次，否则控制区会一直挂在屏幕上不自动隐藏。
            scheduleHidePanel();}if(state==Player.STATE_ENDED&&index+1<items.size())change(1);}
            @Override public void onIsPlayingChanged(boolean playing){syncPlayIcon(playing);}
            @Override public void onPlayerError(PlaybackException e){handleError(e);}});
        video.setPlayer(player);return player;
    }
    private void releasePlayer(){if(player!=null){player.release();player=null;}}
    /** 播放 / 暂停图标跟随播放状态切换。 */
    private void syncPlayIcon(boolean playing){
        if(playButton==null)return;
        NavIcon icon=new NavIcon(playing?"pause":"play",Ui.dp(this,22),0xFFF2F4F3);icon.setBounds(0,0,Ui.dp(this,22),Ui.dp(this,22));
        playButton.setCompoundDrawables(icon,null,null,null);
        playButton.setContentDescription(playing?"暂停":"播放");
    }
    private void prepare(){
        if(items.isEmpty())return;
        // 切集 / 重连时把长按快进收掉：否则旧的定时器会继续在新的一集上乱跳
        endHoldSeek();
        hideErrorActions();Catalog.Item item=items.get(index);title.setText(item.title+" · "+(index+1)+"/"+items.size());
        if(prevButton!=null)prevButton.setContentDescription(item.live?"上一台":"上一集");
        if(nextButton!=null)nextButton.setContentDescription(item.live?"下一台":"下一集");
        setStatus("正在连接…");
        Map<String,String> headers=new LinkedHashMap<>(item.headers);String ua=headers.remove("User-Agent");
        DefaultHttpDataSource.Factory http=new DefaultHttpDataSource.Factory().setUserAgent(ua==null?"LibreTV-App/1.0 Android":ua).setAllowCrossProtocolRedirects(true).setConnectTimeoutMs(12000).setReadTimeoutMs(20000).setDefaultRequestProperties(headers);
        MediaItem.Builder media=new MediaItem.Builder().setUri(item.url);String path=item.url.toLowerCase(Locale.ROOT).split("\\?")[0];if(path.endsWith(".m3u8"))media.setMimeType(MimeTypes.APPLICATION_M3U8);
        MediaSource source=new DefaultMediaSourceFactory(new DefaultDataSource.Factory(this,http)).createMediaSource(media.build());
        ExoPlayer p=ensurePlayer();p.setMediaSource(source);p.prepare();if(!item.live&&resume>0)p.seekTo(resume);p.setPlayWhenReady(autoplay);
        video.setKeepScreenOn(true);store.remember(item,resume);
        // 焦点交给根容器：面板收起时按键要有落点。
        // 电视端例外 —— 电视上控制区常驻，焦点应该在进度条上（见 focusTvSeek），
        // 抢到根容器会让「一进播放页就能快进」这件事失效。
        if(!Ui.tv(this))root.requestFocus();
        // While the source is still loading the picture is black, so the panel stays up with
        // the "connecting" hint instead of leaving the viewer in front of an empty screen.
        // It fades away on its own once playback starts.
        if(!reconnecting){connecting=true;showPanel(false);}
        logFocus("prepare");
    }
    private boolean decodeFailure(PlaybackException e){int code=e.errorCode;return code==PlaybackException.ERROR_CODE_DECODING_FAILED||code==PlaybackException.ERROR_CODE_DECODER_INIT_FAILED||code==PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED||code==PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES;}
    private void handleError(PlaybackException e){
        int code=e.errorCode;
        android.util.Log.w("LibreTV-Player","Playback error "+e.getErrorCodeName()+" ("+code+") live="+live()+" recoveries="+recoverCount);
        // A live stream that falls behind the sliding live window is not a real failure: the
        // stream itself is fine, the player just has to join the live edge again. Reconnect
        // at most three times in a row and never faster than every 20 seconds, so a dead
        // channel can never turn into an endless reconnect loop.
        if(live()&&liveRecoverable(code)&&recoverCount<3&&(lastRecoveryAt<0||SystemClock.uptimeMillis()-lastRecoveryAt>20000)){
            lastRecoveryAt=SystemClock.uptimeMillis();recoverCount++;recoverLive();return;
        }
        if(decodeFailure(e))setStatus("本机硬件解码器无法处理这条视频（可能是清晰度过高或编码不兼容）。可按「重试」再试一次，或返回详情点「换线路」换一条线路 / 选择更低清晰度的源；若电视上所有视频都只有声音没有画面，需要重启电视。");
        else if(code==PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED)setStatus("当前地址未识别为视频，可能是网页播放线路。点「换线路」返回详情，选择 m3u8 / MP4 线路。");
        else if(liveRecoverable(code))setStatus("直播流落后于直播窗口，可按「重试」重新接入直播；也可以点「换线路」换一个频道。");
        else setStatus("播放失败："+e.getErrorCodeName()+"。可点「换线路」尝试其他线路，或按「重试」重新连接。");
        connecting=false;showErrorActions();showPanel(true);endBuffering();
    }
    private boolean live(){return !items.isEmpty()&&items.get(index).live;}
    private static boolean liveRecoverable(int code){return code==PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW||code==PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED||code==PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT||code==PlaybackException.ERROR_CODE_IO_UNSPECIFIED||code==PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS;}
    // Reconnect to the live edge: the same code path as the retry button, so the player
    // instance is reused and the vendor decoder is never torn down. The title bar stays
    // hidden, a short hint in the middle of the picture is enough.
    private void recoverLive(){
        if(player==null||items.isEmpty())return;
        reconnecting=true;
        showOsd("正在重新接入直播…");
        prepare();
        reconnecting=false;
    }
    private void setStatus(String text){status.setText(text);status.setVisibility(text==null||text.isEmpty()?View.GONE:View.VISIBLE);}

    /**
     * 缓冲提示的状态机见 {@link BufferHint}，这里只负责按它的动作显示 / 隐藏转圈。
     *
     * 简短卡顿（几百毫秒）不提示 —— 提示闪一下比不提示更烦；
     * 持续超过 800ms 才判定为「缓冲中」，给画面中央的转圈 + 文案，
     * 同时把控制区留着不淡出（画面停住的时候，面板也消失会让人更慌）。
     */
    private final BufferHint bufferHint = new BufferHint(BUFFER_HINT_DELAY);

    /**
     * 主动盯着播放状态，不等播放器派发回调。
     *
     * 实测这台设备上的 media3 不会派发 onIsLoadingChanged，
     * onPlaybackStateChanged 也未必带 BUFFERING —— 靠回调等着就永远等不到。
     * 所以每 400ms 自己问一次播放器现在什么状态，交给 BufferHint 判断该不该亮转圈。
     */
    private final Runnable bufferWatch = new Runnable() {
        @Override public void run() {
            if (player != null && !locked) {
                int state = Player.STATE_IDLE;
                boolean loading = false;
                try {
                    state = player.getPlaybackState();
                    loading = player.isLoading();
                } catch (Throwable ignore) {
                }
                int action = bufferHint.update(state, loading, SystemClock.uptimeMillis());
                if (action == BufferHint.SHOW) showBufferHint();
                else if (action == BufferHint.HIDE) hideBufferHint();
            }
            ui.postDelayed(this, 400);
        }
    };

    /** 亮转圈：画面中央一块深色底 + 转圈圆环 + 文案。 */
    private void showBufferHint() {
        buffering = true;
        // 「正在连接」和「缓冲中」都要有中央转圈：
        // 画面停在黑屏上、又没有提示，用户只会以为应用死了或者电视坏了。
        // 这两种状态都会让画面静止，区别只在文案上。
        if (bufferLabel != null) bufferLabel.setText(connecting ? "正在连接…" : "缓冲中…");
        if (bufferView != null) bufferView.setVisibility(View.VISIBLE);
        if (!connecting) setStatus("缓冲中…");
        android.util.Log.i("LibreTV-Player", "buffer-hint shown connecting=" + connecting);
        // 缓冲时控制区不淡出：画面停住的时候，面板也消失会让人更慌。
        scheduleHidePanel();
    }

    /** 收掉转圈与提示，面板回到正常淡出节奏。 */
    private void hideBufferHint() {
        buffering = false;
        if (bufferView != null) bufferView.setVisibility(View.GONE);
        if (!locked && !connecting) setStatus("");
    }

    /** 缓冲结束：收掉转圈与提示，面板回到正常淡出节奏。 */
    private void endBuffering() {
        buffering = false;
        bufferHint.reset();
        if (bufferView != null) bufferView.setVisibility(View.GONE);
        if (!locked && !connecting) setStatus("");
    }

    private void showTitle(){if(titleOn&&titleVisible())return;titleOn=true;titleBar.setAlpha(0f);titleBar.setVisibility(View.VISIBLE);Ui.fade(titleBar,1f,220,null);ui.removeCallbacks(hideTitleTask);ui.postDelayed(hideTitleTask,2800);}
    private void hideTitle(){
        // 焦点停在返回键上时不收标题栏 —— 遥控器用户按 UP 走到返回键，
        // 标题条却自己消失了，焦点就丢了，按 BACK 也回不到上一层。
        if(titleBack!=null&&titleBack.hasFocus()){ui.removeCallbacks(hideTitleTask);ui.postDelayed(hideTitleTask,1200);return;}
        if(!titleOn)return;titleOn=false;ui.removeCallbacks(hideTitleTask);Ui.fade(titleBar,0f,220,()->{if(titleOn)return;titleBar.setVisibility(View.GONE);titleBar.setAlpha(1f);});
    }
    private void showPanel(boolean lock){
        // 状态位与实际可见性必须一致：只要面板已经不可见了，就重新弹一次，
        // 否则一次失败的显示会让 panelOn 永远停在 true，之后所有按键都被挡住。
        if(panelOn&&panel.getVisibility()==View.VISIBLE&&panel.getAlpha()>0f){
            if(lock){locked=true;ui.removeCallbacks(hidePanelTask);}
            // 面板本来就在（比如切集时），但焦点可能停在别的地方 ——
            // 切集后要把焦点交回进度条，用户才能直接按左右键。
            focusTvSeek();
            scheduleHidePanel();
            return;
        }
        panelOn=true;locked=lock;panel.setAlpha(0f);panel.setVisibility(View.VISIBLE);Ui.fade(panel,1f,220,null);
        showTitle();ui.removeCallbacks(tickTask);ui.postDelayed(tickTask,300);refreshProgress();
        scheduleHidePanel();
        // 电视端初始焦点落在进度条上：
        // 一进播放页就能直接按左右键快进快退，不用先按 UP 去找进度条。
        // 这是遥控器上最常用的操作 —— 错过了这一下，用户就得先翻两下方向键。
        focusTvSeek();
    }
    /**
     * 排「空闲自动隐藏控制区」的定时器。
     *
     * 手机端 4.2 秒、电视端 8 秒：
     *   电视用遥控器，用户是「先按一下看看、再决定看多久」，8 秒足够扫一眼；
     *   太短的话控制区刚出现就被收走，反而让人以为按键没反应。
     *
     * connecting（还在连流）和 locked（错误态，等用户按「重试」）期间不排 ——
     * 这两种时候控制区必须留着。
     */
    private void scheduleHidePanel(){
        ui.removeCallbacks(hidePanelTask);
        if(locked||connecting||buffering)return;
        ui.postDelayed(hidePanelTask,Ui.tv(this)?tvPanelIdleMs():PANEL_IDLE_MS);
    }
    /**
     * 电视端：控制区该多久后淡出。
     *
     * 焦点还在按钮行上时，说明用户正在挑按钮（上一集 / 选集 / 倍速…），
     * 这时候把它收走会打断操作 —— 给他更长的缓冲。
     * 焦点在进度条上或者已经离开控制区，就是「看一眼就走」，用标准时长。
     */
    private long tvPanelIdleMs(){
        View f=focused();
        if(f!=null&&tvButtonRow!=null&&isDescendantOf(f,tvButtonRow))return TV_PANEL_IDLE_MS*2;
        return TV_PANEL_IDLE_MS;
    }
    /** 电视端：把焦点交给进度条（遥控器最常用的操作是调进度，不是按暂停）。 */
    private void focusTvSeek(){
        if(!Ui.tv(this)||seek==null)return;
        // 长按加速期间不要反复弹这条提示（面板被唤回时会走这里）
        if(requestTvFocus(seek)){if(holdDir==0)showOsd("← → 快进快退 · ↓ 回到按钮");logFocus("seek");return;}
        // 进度条拿不到焦点就退到播放键 —— 焦点绝不能空着。
        // 空焦点会让所有方向键都落空：moveButtonFocus 找不到起点就返回 false，
        // moveFocusUp/Down 也直接返回 false，最后交给框架的默认焦点搜索，
        // 在这套两行面板的布局里它多半什么也找不到 —— 用户看到的就是「按什么都没反应」，
        // 只能靠返回键收起控制区再唤出，焦点才回来。
        if(requestTvFocus(playButton)){logFocus("play");return;}
        // 刚把面板从 GONE 切回 VISIBLE 时，进度条可能还没完成布局，
        // 这时候 requestFocus 会失败 —— 补一次延迟请求，焦点才真的落上去。
        ui.post(()->{if(!requestTvFocus(seek))requestTvFocus(playButton);logFocus("retry");});
    }
    /**
     * 焦点状态日志。
     *
     * 焦点一旦空掉，从代码上很难看出它是在哪一步丢的 —— 现场只有一次。
     * 这行日志能在电视上用 adb logcat -s LibreTV-Player 直接还原：
     * 哪个环节之后 focused() 变成了 null。
     */
    private void logFocus(String where){
        View f=focused();
        String who=f==null?"null":f.getClass().getSimpleName()+(f.getContentDescription()==null?"":("["+f.getContentDescription()+"]"));
        android.util.Log.i("LibreTV-Player","focus@"+where+"="+who);
    }
    /**
     * 请求焦点，并确认真的拿到了。
     *
     * 直接调 View.requestFocus() 是不够的：它在失败时只返回 false，
     * 调用点很容易忽略掉「焦点其实没抢到」这件事，于是焦点就悄悄空了。
     */
    private boolean requestTvFocus(View v){
        if(v==null)return false;
        try{if(v.requestFocus())return true;}catch(Throwable ignore){}
        return false;
    }
    private void hidePanel(){hidePanel(220);}
    private void hidePanel(int duration){
        // 这里不能用 connecting 当拦截条件：直播源连不上时 connecting 会一直停在 true，
        // 面板就再也收不起来，返回键被吞掉，用户被困在播放页里。
        // 自动隐藏的时机本来就已经由 showPanel 里的「connecting 时不排 hidePanelTask」控制，
        // 这里只管「被显式要求收起」，一律照做。
        if(!panelOn)return;panelOn=false;locked=false;ui.removeCallbacks(hidePanelTask);ui.removeCallbacks(tickTask);
        // 焦点还留在控制区里（进度条 / 按钮）的话，先把它挪到根容器。
        // 不挪的话，面板淡出后 setVisibility(GONE) 会把带焦点的视图整个摘掉，
        // 焦点当场变成空 —— 之后所有方向键都失灵，用户只能按返回再唤出才恢复。
        View f=focused();
        if(f!=null&&panel!=null&&isDescendantOf(f,panel)){try{root.requestFocus();}catch(Throwable ignore){}}
        Ui.fade(panel,0f,duration,()->{if(panelOn)return;panel.setVisibility(View.GONE);panel.setAlpha(1f);if(root.isShown())root.requestFocus();});
        hideTitle();
    }
    private void togglePanel(){if(panelVisible())hidePanel();else showPanel(false);}
    /**
     * 面板是否真的在屏幕上。
     *
     * 状态位（panelOn/titleOn）必须和实际可见性一致，否则一次失败的显示会让
     * panelOn 永远停在 true，之后所有按键都被挡住。
     * 判断用「可见 + 不透明」，不用 getWindowVisibility() —— 刚 setVisibility(VISIBLE)
     * 的那一帧窗口还没完成绘制，用窗口可见性会把正在显示的面板误判成隐藏。
     */
    private boolean panelVisible(){return panel.getVisibility()==View.VISIBLE&&panel.getAlpha()>0f;}
    /** 标题条是否真的在屏幕上：可见 + 不透明（不用 getWindowVisibility 那一套）。 */
    private boolean titleVisible(){return titleBar.getVisibility()==View.VISIBLE&&titleBar.getAlpha()>0f;}
    private void showOsd(String text){osdOn=true;osd.setText(text);osd.setVisibility(View.VISIBLE);osd.setAlpha(1f);ui.removeCallbacks(hideOsdTask);ui.postDelayed(hideOsdTask,1500);}
    private void refreshProgress(){
        if(player==null)return;long d=player.getDuration(),p=player.getCurrentPosition();
        durationMs=d>0?d:0;
        // SeekBar 刻度固定 0~10000（ClipDrawable 按 level/10000 裁剪），这里换算成毫秒比例
        if(!dragging){seek.setProgress(d>0?(int)Math.max(0,Math.min(10000L,p*10000L/d)):0);clock.setText(fmt(p));setTotal(d);}
    }
    /** SeekBar 刻度（0~10000）换算回毫秒。 */
    private long toMs(int value){return durationMs>0?Math.max(0,Math.min(durationMs,value*durationMs/10000L)):0L;}
    /** 总时长文本：跟在进度条右端的那一个。 */
    private void setTotal(long d){TextView total=findTotal();if(total!=null)total.setText(fmt(d));}
    private TextView findTotal(){if(panel==null)return null;for(int n=0;n<panel.getChildCount();n++){View child=panel.getChildAt(n);if(child instanceof LinearLayout)for(int k=0;k<((LinearLayout)child).getChildCount();k++){View v=((LinearLayout)child).getChildAt(k);if("total".equals(v.getTag()))return (TextView)v;}}return null;}
    private void togglePlay(){endHoldSeek();if(player!=null)player.setPlayWhenReady(!player.getPlayWhenReady());}

    // ================= 手机端手势 =================
    /**
     * 画面上的触摸手势。
     *
     * 手机上看视频的习惯是「手指直接在画面上操作」：
     *   - 点一下画面：呼出 / 收起控制层（全屏态下改为退出全屏）
     *   - 双击：快退 / 快进 10 秒（双击在手机上比拖进度条顺手得多）
     *   - 横向拖动：连续调进度，拖动时屏幕上显示目标时间
     *
     * 电视上不启用这套手势 —— 电视是遥控器，没有触屏，留着只会误触发。
     */
    private boolean onVideoTouch(View v,MotionEvent e){
        if(Ui.tv(this)){if(e.getAction()==MotionEvent.ACTION_UP)togglePanel();return true;}
        switch(e.getAction()){
            case MotionEvent.ACTION_DOWN:
                touchDownX=e.getX();touchDownY=e.getY();touchDownAt=SystemClock.uptimeMillis();scrubbing=false;return true;
            case MotionEvent.ACTION_MOVE:{
                float dx=e.getX()-touchDownX,dy=e.getY()-touchDownY;
                // 只有明显的横向拖动才当作调进度：竖向拖动多半是误触
                if(!scrubbing&&Math.abs(dx)>Ui.dp(this,24)&&Math.abs(dx)>Math.abs(dy)*1.5f){
                    scrubbing=true;scrubBase=player==null?0:player.getCurrentPosition();
                    showPanel(false);
                }
                if(scrubbing){
                    long d=player==null?0:player.getDuration();
                    long target=d>0?scrubBase+(long)(dx*durationScale(d)):scrubBase;
                    if(d>0)target=Math.max(0,Math.min(d,target));
                    clock.setText(fmt(target));
                    showOsd(fmt(target)+" / "+fmt(d));
                }
                return true;}
            case MotionEvent.ACTION_UP:{
                long now=SystemClock.uptimeMillis();
                if(scrubbing){
                    scrubbing=false;
                    long d=player==null?0:player.getDuration();
                    long target=d>0?scrubBase+(long)((e.getX()-touchDownX)*durationScale(d)):scrubBase;
                    if(d>0)target=Math.max(0,Math.min(d,target));
                    if(player!=null)player.seekTo(target);
                    showOsd(fmt(target)+" / "+fmt(d));
                    return true;
                }
                // 双击：左右分屏，左边快退右边快进
                if(now-touchDownAt<260&&Math.abs(e.getY()-touchDownY)<Ui.dp(this,30)){
                    if(lastTapAt>0&&now-lastTapAt<320){
                        lastTapAt=0;
                        boolean right=e.getX()>root.getWidth()/2f;
                        seekBy(right?10000:-10000);
                        return true;
                    }
                    lastTapAt=now;
                    // 单击延后处理：等一等看会不会变成双击
                    ui.postDelayed(()->{if(lastTapAt==now){lastTapAt=0;if(fullscreen)exitFullscreen();else togglePanel();}},320);
                    return true;
                }
                return true;}
            case MotionEvent.ACTION_CANCEL:scrubbing=false;return true;
        }
        return true;
    }
    /** 横向拖动一整屏对应的时长：按进度条可用的宽度折算。 */
    private float durationScale(long duration){float width=Math.max(Ui.dp(this,200),seek.getWidth());return duration/width;}
    private long lastTapAt;

    // ================= 手机端全屏 =================
    /**
     * 全屏 / 退出全屏。
     *
     * 全屏 = 横屏 + 收起控制面板与标题栏，画面铺满整屏，什么都不挡。
     * 退出全屏 = 切回竖屏 + 把面板照旧还回来（不重新呼出，让用户自己点）。
     *
     * Manifest 里声明了 configChanges 含 orientation|screenSize，
     * 所以切方向不会重建 Activity，播放器实例和播放进度都保留着。
     */
    private void toggleFullscreen(){
        if(Ui.tv(this))return;
        if(fullscreen){exitFullscreen();return;}
        fullscreen=true;
        // 先切横屏，等方向稳定后再收面板 —— 反过来的话旋转动画会带着面板一起闪
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        // 面板直接同步收起，不走淡出动画：
        // 旋转过程中动画帧会被打断，半透明的面板会残留在画面上，
        // 而且淡出回调里还有 requestFocus()，在方向切换的中间态调用容易出错。
        collapseChrome();
        showOsd("全屏 · 再点一次画面退出");
    }
    /** 退出全屏：切回竖屏，面板与标题栏恢复到退出前的常态。 */
    private void exitFullscreen(){
        if(!fullscreen)return;
        fullscreen=false;
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
        // 面板不主动呼出：用户退出全屏后画面干干净净，需要控制条时再点一下屏幕
        collapseChrome();
    }
    /** 同步收起控制面板与标题栏：直接改状态，不走动画，避免旋转时留下残影。 */
    private void collapseChrome(){
        ui.removeCallbacks(hidePanelTask);ui.removeCallbacks(hideTitleTask);
        panelOn=false;locked=false;
        panel.setVisibility(View.GONE);panel.setAlpha(1f);
        titleOn=false;
        titleBar.setVisibility(View.GONE);titleBar.setAlpha(1f);
    }
    /**
     * 切回竖屏时系统可能重建 Activity（某些 ROM 不吃 configChanges），
     * 或者用户自己旋转了手机 —— 这两种情况下全屏态必须跟着走，
     * 否则退出全屏后又停在横屏，跟按钮说的「退出全屏」对不上。
     */
    @Override public void onConfigurationChanged(android.content.res.Configuration config){
        super.onConfigurationChanged(config);
        if(Ui.tv(this))return;
        boolean landscape=config.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        // 用户手动转屏也算全屏：面板收起来，只留画面
        if(landscape&&!fullscreen){fullscreen=true;collapseChrome();}
        else if(!landscape&&fullscreen){fullscreen=false;collapseChrome();}
    }
    private void seekBy(long ms){if(player==null)return;long d=player.getDuration(),p=player.getCurrentPosition();long t=Math.max(0,p+ms);if(d>0)t=Math.min(t,d);player.seekTo(t);showOsd((ms>0?"快进 ":"快退 ")+(Math.abs(ms)/1000)+" 秒 · "+fmt(t));}
    /**
     * 长按连续快进 / 快退（电视端进度条上按住左右键）。
     *
     * 首次按下走 10 秒，之后**越按越快**：
     *   按住 0~1.5 秒：每 200ms 走 5 秒（25 秒/秒）
     *   按住 1.5~4 秒：每 200ms 走 8 秒（40 秒/秒）
     *   按住 4 秒以上：每 150ms 走 12 秒（80 秒/秒）
     *
     * 以前是恒定的 3 秒/250ms —— 折合 12 秒/秒，跟点按一次的 10 秒几乎没差别。
     * 看上去"在动"，其实并不比点按快：用户想跳过片头片尾，还是得一直按着，
     * 按住三秒也只多走二十几秒。
     *
     * 用自己的定时器而不是系统的按键重复：不同电视 ROM 的重复速率差得很远，
     * 系统重复在某些机器上要么太快（一下跳掉几分钟）要么根本不触发。
     */
    private int holdDir;private long holdSince;private int holdTier;
    private final Runnable holdTask=new Runnable(){@Override public void run(){
        if(holdDir==0)return;
        long held=SystemClock.uptimeMillis()-holdSince;
        // 档位跳变时提示一次，让用户知道「越按越快」已经生效
        int tier=held<1500?2:held<4000?4:8;
        if(tier!=holdTier){holdTier=tier;showOsd("加速 ×"+tier);}
        // 长按期间控制区必须留在屏幕上，两个原因：
        //   1) 控制区一旦被空闲计时隐藏，焦点会跟着面板一起被摘走，
        //      松手那个 ACTION_UP 就再也走不到 endHoldSeek，定时器会一路快进到片尾
        //      （这是实测出来的 bug：按住 8 秒后工具栏消失，松手还在快）；
        //   2) 用户按住快进时本来就要看着进度条在动。
        // 每跳都重排一次空闲计时，计时永远到不了 8 秒 —— 面板就常驻到松手为止，
        // 松手后再按正常节奏淡出。这跟主流播放器（B 站 / 爱奇艺 / YouTube）一致。
        scheduleHidePanel();
        if(panel==null||panel.getVisibility()!=View.VISIBLE)showPanel(false);
        seekQuiet(holdDir*holdStep(held));
        if(holdDir!=0)ui.postDelayed(this,holdInterval(held));
    }};
    private void beginHoldSeek(int dir){holdDir=dir;holdSince=SystemClock.uptimeMillis();holdTier=0;seekQuiet(dir*10000);ui.removeCallbacks(holdTask);ui.postDelayed(holdTask,200);}
    /**
     * 长按加速的档位（按住时长 → 这一跳走多少毫秒）。
     *
     * 纯计算，抽出来是为了能写单测：这几档数字是「长按比点按快多少」的全部依据，
     * 靠手测遥控器既难稳定复现，也说不清到底快了几倍。
     */
    static int holdStep(long held){return held<1500?5000:held<4000?8000:12000;}
    /** 长按加速的间隔（毫秒）：按住越久，跳得越密。 */
    static long holdInterval(long held){return held<4000?200:150;}
    /**
     * 长按快进顶到片尾时是否该停。
     *
     * 一直往下跳只会把播放位置钉在片尾，画面看着像卡住 —— 该停就停。
     * 留 500ms 的余量，免得差最后一点就判定为到底了。
     */
    static boolean holdReachedEnd(long position,long duration){return duration>0&&position>=duration-500;}
    private void endHoldSeek(){
        if(holdDir==0)return;
        holdDir=0;ui.removeCallbacks(holdTask);
        // 松手后面板回到正常的淡出节奏
        scheduleHidePanel();
        if(player!=null){long d=player.getDuration();showOsd(fmt(player.getCurrentPosition())+(d>0?" / "+fmt(d):""));}
    }
    /** 只 seek，不弹提示 —— 连续快进时每 200ms 弹一次提示会糊满屏幕。 */
    private void seekQuiet(long ms){
        if(player==null)return;
        long d=player.getDuration(),p=player.getCurrentPosition();
        long t=Math.max(0,p+ms);if(d>0)t=Math.min(t,d);
        player.seekTo(t);
        if(clock!=null)clock.setText(fmt(t));
        android.util.Log.i("LibreTV-Player","seek "+(t-p)+"ms -> "+t+"/"+d);
        // 顶到片尾就收手，别一路钉在片尾
        if(holdDir!=0&&holdReachedEnd(t,d))endHoldSeek();
    }
    private static String fmt(long ms){if(ms<0)return "--:--";long s=ms/1000;long h=s/3600,m=(s/60)%60,x=s%60;return h>0?String.format(Locale.getDefault(),"%d:%02d:%02d",h,m,x):String.format(Locale.getDefault(),"%02d:%02d",m,x);}

    // 选集 / 倍速 / 画面比例都用自绘弹框：系统 AlertDialog 是 Material 蓝白样式，
    // 和播放页的深色沉浸完全是两套东西，焦点反馈也不统一。
    private void pickEpisode(){String[] labels=new String[items.size()];for(int n=0;n<labels.length;n++)labels[n]=items.get(n).title;UiDialog.list(this,"选集",labels,index,n->{save();index=n;resume=store.position(items.get(index).url);prepare();});}
    // 当前倍速自己记一份：media3 这个版本的 ExoPlayer 没有 getPlaybackSpeed()，读不到当前值
    private void pickSpeed(){final float[] speeds={.75f,1f,1.25f,1.5f,2f};String[] labels={"0.75×","1.0×","1.25×","1.5×","2.0×"};int checked=1;for(int n=0;n<speeds.length;n++)if(Math.abs(playbackSpeed-speeds[n])<0.01f)checked=n;UiDialog.list(this,"播放速度",labels,checked,n->{playbackSpeed=speeds[n];if(player!=null)player.setPlaybackSpeed(playbackSpeed);});}
    private void pickRatio(){String[] labels={"适应屏幕","填满屏幕","裁切放大"};UiDialog.list(this,"画面显示",labels,-1,n->video.setResizeMode(new int[]{androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT,androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL,androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM}[n]));}
    private void save(){if(player!=null&&!items.isEmpty()){resume=player.getCurrentPosition();autoplay=player.getPlayWhenReady();store.remember(items.get(index),resume);}}
    private void change(int delta){int next=index+delta;if(next<0||next>=items.size()){Ui.toast(this,"没有更多剧集");return;}save();index=next;resume=store.position(items.get(index).url);autoplay=true;prepare();}
    // Hiding the system bars in an immersive way makes Android 11 and newer raise the
    // "immersive mode confirmation" overlay, which takes the input focus and swallows the
    // next key press. Televisions keep the immersive flags, where no such overlay exists,
    // while phones only hide the status bar and stay edge to edge.
    private void immersive(){
        if(Ui.tv(this))getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        else getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }
    @Override public void onWindowFocusChanged(boolean has){super.onWindowFocusChanged(has);if(has)immersive();}
    @Override protected void onStart(){super.onStart();if(!items.isEmpty())prepare();}
    @Override protected void onResume(){super.onResume();}
    @Override protected void onPause(){endHoldSeek();super.onPause();}
    @Override protected void onStop(){endHoldSeek();endBuffering();ui.removeCallbacks(bufferWatch);save();releasePlayer();if(video!=null)video.setKeepScreenOn(false);super.onStop();}
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        if(mediaKey(event))return true;
        // 长按快进的「松手」必须无条件收尾。
        //
        // 以前 endHoldSeek 只写在「焦点正好在进度条上」那条分支里，
        // 而控制区被空闲计时隐藏时，面板连同焦点一起被摘掉 ——
        // 这时左右键的 ACTION_UP 走不到那条分支，定时器就一直快进到片尾。
        // 所以：左右键抬起（或者被系统取消）时，不管面板可不可见、焦点在哪，
        // 先把长按停下来。主流播放器也是「松手即停」，不会因为 UI 状态而停不下来。
        int eventAction=event.getAction(),eventCode=event.getKeyCode();
        if(eventAction!=KeyEvent.ACTION_DOWN
                &&(eventCode==KeyEvent.KEYCODE_DPAD_LEFT||eventCode==KeyEvent.KEYCODE_DPAD_RIGHT)
                &&holdDir!=0){
            endHoldSeek();
            return true;
        }
        // 用实际可见性判断，而不是只看 panelOn：
        // 面板若因动画没跑完而停在不可见状态，按键必须仍然能把它唤出来。
        boolean shown=panelVisible();
        if(!shown&&event.getAction()==KeyEvent.ACTION_DOWN){switch(event.getKeyCode()){
            case KeyEvent.KEYCODE_DPAD_CENTER:case KeyEvent.KEYCODE_ENTER:case KeyEvent.KEYCODE_SPACE:togglePlay();return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:case KeyEvent.KEYCODE_DPAD_RIGHT:{
                int dir=event.getKeyCode()==KeyEvent.KEYCODE_DPAD_RIGHT?1:-1;
                if(Ui.tv(this)){
                    // 电视端：先把控制区唤回。
                    // 用户按了快进，就得看得见进度条在动、时间码在跳 ——
                    // 只弹一行提示的话，画面看起来什么都没发生。
                    // 唤回后焦点落在进度条上，按住不放就是连续加速
                    //（松手由下面 ACTION_UP 的分支收尾）。
                    showPanel(false);
                    beginHoldSeek(dir);
                    return true;
                }
                seekBy(dir*10000);return true;
            }
            case KeyEvent.KEYCODE_DPAD_UP:case KeyEvent.KEYCODE_DPAD_DOWN:case KeyEvent.KEYCODE_MENU:showPanel(false);return true;
        }}
        // 电视端：控制区是两行（上行进度条 / 下行按钮），
        // 左右键由焦点系统在各行的按钮之间移动，
        // 上下键负责在「进度条 ↔ 按钮 ↔ 标题栏」之间搬焦点。
        //
        // 这里必须自己搬：View.setNextFocusUp/Down 是 API 26 才有的，
        // 这台电视是 Android 5.0（API 21），用不了。
        if(Ui.tv(this)&&shown){
            // 焦点一旦空掉，所有方向键都会落空：moveButtonFocus 找不到起点就返回 false，
            // moveFocusUp/Down 也直接返回 false，最后交给框架的默认焦点搜索 ——
            // 在这套两行面板的布局里它多半什么也找不到，用户看到的就是「按什么都没反应」。
            // 所以只要发现焦点是空的，先把它抢回进度条，再照常处理这个按键。
            if(focused()==null){logFocus("lost");focusTvSeek();}
            int code=event.getKeyCode();
            boolean down=event.getAction()==KeyEvent.ACTION_DOWN;
            // 任何按键都算「用户还在看」——重置空闲计时，
            // 不然用户正盯着控制区挑按钮，它自己淡出去了。
            if(down&&code!=KeyEvent.KEYCODE_BACK)scheduleHidePanel();
            // 焦点在进度条上时，OK 键报一下当前播放位置。
            // 系统 SeekBar 会把 ENTER 当成「开始拖动」——在这套布局里没有意义
            // （进度条本来就跟着播放位置走），而且会吃掉按键。
            if(code==KeyEvent.KEYCODE_DPAD_CENTER||code==KeyEvent.KEYCODE_ENTER){
                if(focused()==seek){
                    if(!down&&player!=null){
                        long d=player.getDuration();
                        showOsd(fmt(player.getCurrentPosition())+(d>0?" / "+fmt(d):""));
                    }
                    return true;
                }
            }
            // 焦点在进度条上时，左右键 = 真正的快进快退（按住连续加速）。
            // 必须在系统 SeekBar 拿到这个键之前截下来 —— 它只会改自己的刻度，
            // 不会 seek 播放器，而且焦点就再也出不去那一行了。
            if(code==KeyEvent.KEYCODE_DPAD_LEFT||code==KeyEvent.KEYCODE_DPAD_RIGHT){
                if(focused()==seek){
                    int dir=code==KeyEvent.KEYCODE_DPAD_RIGHT?1:-1;
                    if(down){if(event.getRepeatCount()==0)beginHoldSeek(dir);}
                    else endHoldSeek();
                    return true;
                }
                // 焦点在按钮行时，左右键按顺序表走。
                // 框架自带的焦点搜索在这个布局里会跳位（中间有留白），
                // 而且进度条混在按钮中间时会直接把按键吃掉，焦点再也走不出去。
                //
                // 只在 ACTION_DOWN 时移动：一次按键会派发 DOWN 和 UP 两个事件，
                // 两个都移动的话焦点会一次跳两格，中间的按钮就被吞掉了。
                if(down&&moveButtonFocus(code==KeyEvent.KEYCODE_DPAD_RIGHT?1:-1)){scheduleHidePanel();return true;}
                if(down)return true;
            }
            if(down){
                // 焦点搬走之后必须重新排一次空闲计时。
                // 上面那次 scheduleHidePanel() 是按「搬之前」的焦点算的时长：
                // 用户刚从按钮行挪到进度条，本该按 8 秒算，却还挂着按钮行的 16 秒双倍缓冲，
                // 控制区于是迟迟不隐藏，看起来像「自动隐藏坏了」。
                if(code==KeyEvent.KEYCODE_DPAD_UP){if(moveFocusUp()){scheduleHidePanel();return true;}}
                else if(code==KeyEvent.KEYCODE_DPAD_DOWN){if(moveFocusDown()){scheduleHidePanel();return true;}}
            }
            // 上下左右这四个键在本页完全由我们自己处理，
            // UP（松手）事件必须一并吃掉 —— 否则框架会在松手时再搬一次焦点，
            // 表现为「按一次跳两个键」。
            if(code==KeyEvent.KEYCODE_DPAD_LEFT||code==KeyEvent.KEYCODE_DPAD_RIGHT
                    ||code==KeyEvent.KEYCODE_DPAD_UP||code==KeyEvent.KEYCODE_DPAD_DOWN)return true;
        }
        return super.dispatchKeyEvent(event);
    }
    /** 当前获得焦点的控件。 */
    private View focused(){return getCurrentFocus();}
    /**
     * 电视端：按钮行的左右导航。
     *
     * 按 tvButtons 的顺序表走，走到底就停住（不循环，也不跳到别的行）。
     * 焦点不在按钮行上时返回 false，交给别人处理。
     */
    private boolean moveButtonFocus(int dir){
        if(tvButtons.isEmpty())return false;
        View f=focused();
        int idx=-1;
        for(int n=0;n<tvButtons.size();n++)if(tvButtons.get(n)==f){idx=n;break;}
        if(idx<0)return false;
        // 顺着方向找第一个可见的键。「重试」「换线路」平时是隐藏的，
        // 不能让焦点停在它们身上 —— 那样焦点就「消失」了，看起来像是卡住。
        for(int step=1;step<=tvButtons.size();step++){
            int next=idx+dir*step;
            if(next<0||next>=tvButtons.size())break;// 到头了，停在原地
            View target=tvButtons.get(next);
            if(target.getVisibility()!=View.VISIBLE)continue;
            target.requestFocus();
            return true;
        }
        return true;// 到头了：吃掉按键，别让它乱跳
    }
    /**
     * 电视端：焦点往上搬。
     *
     * 进度条 → 标题栏返回键；按钮行 → 进度条。
     * 已经在最上面了就返回 false，交给系统处理（不动）。
     */
    private boolean moveFocusUp(){
        View f=focused();
        if(f==null)return false;
        if(f==seek){
            if(titleBack!=null&&titleVisible()&&titleBack.requestFocus())return true;
            return false;
        }
        if(tvButtonRow!=null&&isDescendantOf(f,tvButtonRow)){
            if(seek!=null&&seek.requestFocus())return true;
        }
        return false;
    }
    /**
     * 电视端：焦点往下搬。
     *
     * 标题栏返回键 → 进度条；进度条 → 按钮行（播放键）。
     */
    private boolean moveFocusDown(){
        View f=focused();
        if(f==null)return false;
        if(f==titleBack){
            if(seek!=null&&seek.requestFocus())return true;
            return false;
        }
        if(f==seek){
            if(playButton!=null&&playButton.requestFocus())return true;
        }
        return false;
    }
    /** 判断 v 是否在某个祖先容器之内（焦点行归属判定）。 */
    private static boolean isDescendantOf(View v,ViewGroup ancestor){View p=v;while(p!=null){if(p==ancestor)return true;p=(p.getParent() instanceof View)?(View)p.getParent():null;}return false;}
    // PlayerView only forwards media keys to the player while its built in controller is
    // enabled. That controller is off here so the immersive screen stays free of chrome, and
    // the same key handling is done by hand instead.
    private boolean mediaKey(KeyEvent event){
        if(player==null)return false;int code=event.getKeyCode();
        if(event.getAction()!=KeyEvent.ACTION_DOWN)return isMediaKey(code);
        if(code==KeyEvent.KEYCODE_MEDIA_FAST_FORWARD){player.seekForward();return true;}
        if(code==KeyEvent.KEYCODE_MEDIA_REWIND){player.seekBack();return true;}
        if(event.getRepeatCount()!=0)return true;
        switch(code){
            case KeyEvent.KEYCODE_MUSIC:case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:togglePlay();return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY:player.play();return true;
            case KeyEvent.KEYCODE_MEDIA_PAUSE:player.pause();return true;
            case KeyEvent.KEYCODE_MEDIA_NEXT:if(player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT))player.seekToNext();return true;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:if(player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS))player.seekToPrevious();return true;
        }
        return false;
    }
    private static boolean isMediaKey(int code){return code==KeyEvent.KEYCODE_MUSIC||code==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE||code==KeyEvent.KEYCODE_MEDIA_PLAY||code==KeyEvent.KEYCODE_MEDIA_PAUSE||code==KeyEvent.KEYCODE_MEDIA_NEXT||code==KeyEvent.KEYCODE_MEDIA_PREVIOUS||code==KeyEvent.KEYCODE_MEDIA_FAST_FORWARD||code==KeyEvent.KEYCODE_MEDIA_REWIND;}
    @Override public boolean onKeyDown(int key,KeyEvent event){
        if(!items.isEmpty()&&items.get(index).live&&(key==KeyEvent.KEYCODE_CHANNEL_UP||key==KeyEvent.KEYCODE_CHANNEL_DOWN)){change(key==KeyEvent.KEYCODE_CHANNEL_UP?1:-1);return true;}
        if(panelVisible()&&key==KeyEvent.KEYCODE_BACK)return true;
        return super.onKeyDown(key,event);
    }
    /**
     * 返回键：全屏时先退全屏，再收起控制面板，最后才退出播放页。
     *
     * 面板收起不能被 connecting 拦住 —— 直播源连不上时 connecting 一直是 true，
     * 面板就永远收不起来，返回键被吞掉，用户会被困在播放页里出不来。
     * 所以这里用「面板是否真的可见」判断，connecting 不参与。
     */
    @Override public boolean onKeyUp(int key,KeyEvent event){if(key==KeyEvent.KEYCODE_BACK){if(fullscreen){exitFullscreen();return true;}if(panelVisible()){hidePanel();return true;}finish();return true;}return super.onKeyUp(key,event);}
    @Override protected void onDestroy(){String file=getIntent().getStringExtra("playlistFile");if(isFinishing()&&file!=null&&file.startsWith("play-")&&!file.contains("/"))new java.io.File(getCacheDir(),file).delete();super.onDestroy();}
}
