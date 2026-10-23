package com.wangshaojie.libretv;

import android.graphics.*;
import android.graphics.drawable.Drawable;

final class NavIcon extends Drawable {
    private final String name;private final Paint paint=new Paint(3);private final int size;
    NavIcon(String name,int size){this.name=name;this.size=size;}
    @Override public boolean isStateful(){return true;}
    @Override protected boolean onStateChange(int[] states){invalidateSelf();return true;}
    @Override public void draw(Canvas canvas){canvas.save();canvas.translate(getBounds().left,getBounds().top);canvas.scale(getBounds().width()/24f,getBounds().height()/24f);boolean active=false;for(int state:getState())if(state==android.R.attr.state_selected||state==android.R.attr.state_focused)active=true;paint.setColor(active?Ui.ACCENT:Ui.MUTED);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.7f);paint.setStrokeJoin(Paint.Join.ROUND);paint.setStrokeCap(Paint.Cap.ROUND);
        if(name.equals("home")){Path p=new Path();p.moveTo(3,11);p.lineTo(12,3);p.lineTo(21,11);p.moveTo(5,10);p.lineTo(5,21);p.lineTo(10,21);p.lineTo(10,15);p.lineTo(14,15);p.lineTo(14,21);p.lineTo(19,21);p.lineTo(19,10);canvas.drawPath(p,paint);}
        else if(name.equals("vod")){canvas.drawCircle(10,10,6,paint);canvas.drawLine(15,15,21,21,paint);}
        else if(name.equals("live")){canvas.drawRoundRect(3,6,21,19,3,3,paint);canvas.drawLine(8,2,12,6,paint);canvas.drawLine(16,2,12,6,paint);canvas.drawLine(8,22,16,22,paint);}
        else{canvas.drawCircle(12,7,4,paint);canvas.drawArc(4,13,20,27,180,180,false,paint);}
        canvas.restore();}
    @Override public int getIntrinsicWidth(){return size;}@Override public int getIntrinsicHeight(){return size;}@Override public void setAlpha(int alpha){paint.setAlpha(alpha);}@Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);}@Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
