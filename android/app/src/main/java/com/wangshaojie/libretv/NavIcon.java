package com.wangshaojie.libretv;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/**
 * 线性图标集（24×24 网格，1.7 描边，round 端点）。
 * 激活/聚焦时用香槟金，常态用中性灰 —— 与桌面端图标风格一致。
 *
 * 传入 forcedColor 时忽略状态色，固定用该颜色 ——
 * 放在金色实心按钮上的图标必须用深色，灰色在金底上对比度不够。
 */
final class NavIcon extends Drawable {
    private final String name;private final Paint paint=new Paint(3);private final int size;private int forced;
    NavIcon(String name,int size){this(name,size,0);}
    NavIcon(String name,int size,int forced){this.name=name;this.size=size;this.forced=forced;}
    /** 运行时改色（固定色模式下生效），并立刻重绘。 */
    void setColor(int color){this.forced=color;invalidateSelf();}
    @Override public boolean isStateful(){return forced==0;}
    @Override protected boolean onStateChange(int[] states){invalidateSelf();return true;}
    @Override public void draw(Canvas canvas){canvas.save();canvas.translate(getBounds().left,getBounds().top);canvas.scale(getBounds().width()/24f,getBounds().height()/24f);boolean active=false;for(int state:getState())if(state==android.R.attr.state_selected||state==android.R.attr.state_focused||state==android.R.attr.state_activated)active=true;paint.setColor(forced!=0?forced:(active?Ui.ACCENT:Ui.MUTED));
        // 实心图标（播放 / 暂停 / 更多）用填充画，其余用描边画
        boolean solid=solid(name);
        paint.setStyle(solid?Paint.Style.FILL:Paint.Style.STROKE);
        paint.setStrokeWidth(name.equals("search")?2.3f:1.7f);paint.setStrokeJoin(Paint.Join.ROUND);paint.setStrokeCap(Paint.Cap.ROUND);
        if(name.equals("home")){Path p=new Path();p.moveTo(3,11);p.lineTo(12,3);p.lineTo(21,11);p.moveTo(5,10);p.lineTo(5,21);p.lineTo(10,21);p.lineTo(10,15);p.lineTo(14,15);p.lineTo(14,21);p.lineTo(19,21);p.lineTo(19,10);canvas.drawPath(p,paint);}
        else if(name.equals("back")){Path p=new Path();p.moveTo(15,19);p.lineTo(8,12);p.lineTo(15,5);canvas.drawPath(p,paint);}
        else if(name.equals("search")){canvas.drawCircle(10.5f,10.5f,6.2f,paint);canvas.drawLine(15,15,20.5f,20.5f,paint);}
        else if(name.equals("close")){canvas.drawLine(6,6,18,18,paint);canvas.drawLine(18,6,6,18,paint);}
        else if(name.equals("vod")){canvas.drawCircle(10,10,6,paint);canvas.drawLine(15,15,21,21,paint);}
        else if(name.equals("live")){canvas.drawRoundRect(3,6,21,19,3,3,paint);canvas.drawLine(8,2,12,6,paint);canvas.drawLine(16,2,12,6,paint);canvas.drawLine(8,22,16,22,paint);}
        // ---- 播放页图标 ----
        // 播放：实心三角
        else if(name.equals("play")){Path p=new Path();p.moveTo(7,4.5f);p.lineTo(19.5f,12);p.lineTo(7,19.5f);p.close();canvas.drawPath(p,paint);}
        // 暂停：两根实心竖条
        else if(name.equals("pause")){canvas.drawRect(6.5f,4.5f,10.5f,19.5f,paint);canvas.drawRect(13.5f,4.5f,17.5f,19.5f,paint);}
        // 下一集：实心三角 + 竖条
        else if(name.equals("next")){Path p=new Path();p.moveTo(4,5);p.lineTo(14.5f,12);p.lineTo(4,19);p.close();canvas.drawPath(p,paint);canvas.drawRect(16.5f,5,19.5f,19,paint);}
        // 上一集：竖条 + 实心三角
        else if(name.equals("prev")){Path p=new Path();p.moveTo(20,5);p.lineTo(9.5f,12);p.lineTo(20,19);p.close();canvas.drawPath(p,paint);canvas.drawRect(4.5f,5,7.5f,19,paint);}
        // 选集：三行清单（左侧短竖杠 + 右侧长线）
        else if(name.equals("list")){for(int row=0;row<3;row++){float y=7+row*5;canvas.drawLine(3.5f,y-1.6f,3.5f,y+1.6f,paint);canvas.drawLine(7.5f,y,20.5f,y,paint);}}
        // 倍速：仪表盘（上半弧 + 指针）
        else if(name.equals("speed")){canvas.drawArc(new RectF(3.5f,5f,20.5f,22f),180,180,false,paint);canvas.drawLine(12f,14f,16.8f,8.6f,paint);canvas.drawCircle(12f,14f,1.3f,paint);}
        // 画面比例：方框 + 左右双向箭头
        else if(name.equals("aspect")){canvas.drawRoundRect(3f,6f,21f,18f,2f,2f,paint);canvas.drawLine(7f,12f,17f,12f,paint);canvas.drawLine(9f,10f,7f,12f,paint);canvas.drawLine(9f,14f,7f,12f,paint);canvas.drawLine(15f,10f,17f,12f,paint);canvas.drawLine(15f,14f,17f,12f,paint);}
        // 收藏：五角星
        else if(name.equals("star")){Path p=new Path();p.moveTo(12,3.2f);p.lineTo(14.5f,9.2f);p.lineTo(21,9.9f);p.lineTo(16.1f,14.2f);p.lineTo(17.5f,20.6f);p.lineTo(12,17.2f);p.lineTo(6.5f,20.6f);p.lineTo(7.9f,14.2f);p.lineTo(3,9.9f);p.lineTo(9.5f,9.2f);p.close();canvas.drawPath(p,paint);}
        // 全屏：四角向外
        else if(name.equals("fullscreen")){canvas.drawLine(3f,9f,3f,3f,paint);canvas.drawLine(3f,3f,9f,3f,paint);canvas.drawLine(15f,3f,21f,3f,paint);canvas.drawLine(21f,3f,21f,9f,paint);canvas.drawLine(21f,15f,21f,21f,paint);canvas.drawLine(21f,21f,15f,21f,paint);canvas.drawLine(9f,21f,3f,21f,paint);canvas.drawLine(3f,21f,3f,15f,paint);}
        // 退出全屏：四角向内
        else if(name.equals("exitfullscreen")){canvas.drawLine(6.5f,3f,3f,6.5f,paint);canvas.drawLine(3f,3f,3f,6.5f,paint);canvas.drawLine(17.5f,3f,21f,6.5f,paint);canvas.drawLine(21f,3f,21f,6.5f,paint);canvas.drawLine(21f,17.5f,21f,21f,paint);canvas.drawLine(17.5f,21f,21f,17.5f,paint);canvas.drawLine(6.5f,21f,3f,17.5f,paint);canvas.drawLine(3f,21f,3f,17.5f,paint);}
        // 换线路：两条反向箭头
        else if(name.equals("swap")){canvas.drawLine(3.5f,8.5f,18,8.5f,paint);Path a1=new Path();a1.moveTo(18,8.5f);a1.lineTo(14,5.6f);a1.lineTo(14,11.4f);a1.close();canvas.drawPath(a1,paint);canvas.drawLine(20.5f,15.5f,6,15.5f,paint);Path a2=new Path();a2.moveTo(6,15.5f);a2.lineTo(10,12.6f);a2.lineTo(10,18.4f);a2.close();canvas.drawPath(a2,paint);}
        // 更多：三个点
        else if(name.equals("more")){canvas.drawCircle(6f,12f,1.7f,paint);canvas.drawCircle(12f,12f,1.7f,paint);canvas.drawCircle(18f,12f,1.7f,paint);}
        // 重试：断口圆环 + 箭头
        else if(name.equals("retry")){canvas.drawArc(new RectF(4f,4f,20f,20f),40,285,false,paint);Path head=new Path();head.moveTo(15.2f,2.6f);head.lineTo(20.4f,7.4f);head.lineTo(13.6f,8.2f);head.close();canvas.drawPath(head,paint);}
        // 锁定：锁体 + 锁梁
        else if(name.equals("lock")){canvas.drawRoundRect(5f,10.5f,19f,20f,2f,2f,paint);canvas.drawArc(new RectF(7.5f,3.5f,16.5f,12.5f),180,180,false,paint);}
        else{canvas.drawCircle(12,7,4,paint);canvas.drawArc(4,13,20,27,180,180,false,paint);}
        canvas.restore();}
    /** 需要用实心画法（而不是描边）的图标。 */
    private static boolean solid(String name){return name.equals("play")||name.equals("pause")||name.equals("next")||name.equals("prev")||name.equals("more");}
    @Override public int getIntrinsicWidth(){return size;}@Override public int getIntrinsicHeight(){return size;}@Override public void setAlpha(int alpha){paint.setAlpha(alpha);}@Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);}@Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
