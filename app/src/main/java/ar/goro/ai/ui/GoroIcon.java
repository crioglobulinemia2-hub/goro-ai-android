package ar.goro.ai.ui;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** Consistent line icons for the approved GORO navigation, independent of fonts. */
final class GoroIcon extends Drawable {
 private final String name;
 private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
 private final int size;
 GoroIcon(String name,int color,int size){this.name=name;this.size=size;paint.setColor(color);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.65f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);setBounds(0,0,size,size);}
 @Override public void draw(Canvas c){
  c.save();c.translate(getBounds().left,getBounds().top);c.scale(getBounds().width()/24f,getBounds().height()/24f);
  switch(name){
   case "agenda":c.drawRoundRect(new RectF(3,5,21,21),3,3,paint);line(c,3,10,21,10);line(c,8,3,8,7);line(c,16,3,16,7);for(int x=7;x<20;x+=5)for(int y=14;y<20;y+=4)c.drawCircle(x,y,.5f,paint);break;
   case "mail":c.drawRoundRect(new RectF(2.5f,5,21.5f,19),3,3,paint);path(c,3,6,12,13,21,6);break;
   case "mic":c.drawRoundRect(new RectF(9,2,15,15),3,3,paint);c.drawArc(new RectF(5,5,19,19),0,180,false,paint);line(c,5,11,5,12);line(c,19,11,19,12);line(c,12,19,12,22);line(c,8,22,16,22);break;
   case "folder":path(c,2,8,2,5,9,5,12,8,21,8,21,20,2,20,2,8);break;
   case "devices":c.drawRoundRect(new RectF(6,5,18,19),4,4,paint);line(c,9,2,15,2);line(c,9,22,15,22);c.drawCircle(12,12,3,paint);break;
   case "chat":c.drawRoundRect(new RectF(2,3,22,18),5,5,paint);path(c,8,18,6,22,13,18);for(int x=7;x<19;x+=5)c.drawCircle(x,10,.5f,paint);break;
   case "grid":for(int x=3;x<18;x+=11)for(int y=3;y<18;y+=11)c.drawRoundRect(new RectF(x,y,x+7,y+7),1.5f,1.5f,paint);break;
   case "bolt":path(c,14,2,4,14,11,14,10,22,20,9,13,9,14,2);break;
   case "settings":c.drawCircle(12,12,6,paint);c.drawCircle(12,12,2,paint);for(int n=0;n<8;n++){double a=n*Math.PI/4;line(c,(float)(12+7*Math.cos(a)),(float)(12+7*Math.sin(a)),(float)(12+10*Math.cos(a)),(float)(12+10*Math.sin(a)));}break;
   case "back":path(c,15,4,7,12,15,20);break;
   case "menu":line(c,4,6,20,6);line(c,4,12,16,12);line(c,4,18,20,18);break;
   case "sun":c.drawCircle(12,12,4,paint);for(int n=0;n<8;n++){double a=n*Math.PI/4;line(c,(float)(12+7*Math.cos(a)),(float)(12+7*Math.sin(a)),(float)(12+9*Math.cos(a)),(float)(12+9*Math.sin(a)));}break;
   case "link":c.drawArc(new RectF(2,8,15,21),40,280,false,paint);c.drawArc(new RectF(9,2,22,15),220,280,false,paint);line(c,8,16,16,8);break;
   case "face":c.drawCircle(12,12,9,paint);c.drawArc(new RectF(7,10,17,17),10,160,false,paint);c.drawCircle(8,9,.6f,paint);c.drawCircle(16,9,.6f,paint);break;
   default:c.drawCircle(12,12,8,paint);
  }
  c.restore();
 }
 private void line(Canvas c,float a,float b,float x,float y){c.drawLine(a,b,x,y,paint);}
 private void path(Canvas c,float...v){Path p=new Path();p.moveTo(v[0],v[1]);for(int n=2;n<v.length;n+=2)p.lineTo(v[n],v[n+1]);c.drawPath(p,paint);}
 @Override public void setAlpha(int alpha){paint.setAlpha(alpha);invalidateSelf();}
 @Override public void setColorFilter(ColorFilter f){paint.setColorFilter(f);invalidateSelf();}
 @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
 @Override public int getIntrinsicWidth(){return size;}
 @Override public int getIntrinsicHeight(){return size;}
}
