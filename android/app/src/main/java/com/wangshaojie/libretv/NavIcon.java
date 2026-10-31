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
    @Override public void draw(Canvas canvas){canvas.save();canvas.translate(getBounds().left,getBounds().top);canvas.scale(getBounds().width()/24f,getBounds().height()/24f);boolean active=false;for(int state:getState())if(state==android.R.attr.state_selected||state==android.R.attr.state_focused||state==android.R.attr.state_activated)active=true;paint.setColor(forced!=0?forced:(active?Ui.ACCENT:Ui.MUTED));paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(name.equals("search")?2.3f:1.7f);paint.setStrokeJoin(Paint.Join.ROUND);paint.setStrokeCap(Paint.Cap.ROUND);
        if(name.equals("home")){Path p=new Path();p.moveTo(3,11);p.lineTo(12,3);p.lineTo(21,11);p.moveTo(5,10);p.lineTo(5,21);p.lineTo(10,21);p.lineTo(10,15);p.lineTo(14,15);p.lineTo(14,21);p.lineTo(19,21);p.lineTo(19,10);canvas.drawPath(p,paint);}
        else if(name.equals("back")){Path p=new Path();p.moveTo(15,19);p.lineTo(8,12);p.lineTo(15,5);canvas.drawPath(p,paint);}
        else if(name.equals("search")){canvas.drawCircle(10.5f,10.5f,6.2f,paint);canvas.drawLine(15,15,20.5f,20.5f,paint);}
        else if(name.equals("close")){canvas.drawLine(6,6,18,18,paint);canvas.drawLine(18,6,6,18,paint);}
        else if(name.equals("vod")){canvas.drawCircle(10,10,6,paint);canvas.drawLine(15,15,21,21,paint);}
        else if(name.equals("live")){canvas.drawRoundRect(3,6,21,19,3,3,paint);canvas.drawLine(8,2,12,6,paint);canvas.drawLine(16,2,12,6,paint);canvas.drawLine(8,22,16,22,paint);}
        else{canvas.drawCircle(12,7,4,paint);canvas.drawArc(4,13,20,27,180,180,false,paint);}
        canvas.restore();}
    @Override public int getIntrinsicWidth(){return size;}@Override public int getIntrinsicHeight(){return size;}@Override public void setAlpha(int alpha){paint.setAlpha(alpha);}@Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);}@Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
