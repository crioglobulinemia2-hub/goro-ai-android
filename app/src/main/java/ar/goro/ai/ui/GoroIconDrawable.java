package ar.goro.ai.ui;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

/** Resolution-independent controls; the character itself remains a real 3D model. */
final class GoroIconDrawable extends Drawable {
 private final String name;
 private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
 private final int size;
 GoroIconDrawable(String name,int color,int size){
  this.name=name;this.size=size;paint.setColor(color);paint.setStyle(Paint.Style.STROKE);
  paint.setStrokeWidth(1.7f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);
  setBounds(0,0,size,size);
 }
 @Override public void draw(Canvas canvas){
  canvas.save();canvas.translate(getBounds().left,getBounds().top);
  canvas.scale(getBounds().width()/24f,getBounds().height()/24f);
  switch(name){
   case "Agenda":
    canvas.drawRoundRect(3,5,21,21,3,3,paint);line(canvas,3,10,21,10);
    line(canvas,8,3,8,7);line(canvas,16,3,16,7);
    for(int x=7;x<=17;x+=5)for(int y=14;y<=18;y+=4)canvas.drawPoint(x,y,paint);
    break;
   case "Gmail":
    canvas.drawRoundRect(2,5,22,20,3,3,paint);path(canvas,2,6,12,14,22,6);break;
   case "Proyectos":
    path(canvas,3,20,3,5,9,5,12,8,21,8,21,20,3,20);break;
   case "Dispositivos":
    canvas.drawRoundRect(5,5,19,19,4,4,paint);
    line(canvas,8,2,16,2);line(canvas,8,22,16,22);
    canvas.drawCircle(12,12,3,paint);break;
   case "Conversar":
    path(canvas,4,18,2,22,3,15);canvas.drawRoundRect(3,3,22,18,6,6,paint);
    canvas.drawPoint(8,10,paint);canvas.drawPoint(13,10,paint);canvas.drawPoint(18,10,paint);break;
   case "Explorar":
    for(int x=3;x<=14;x+=11)for(int y=3;y<=14;y+=11)canvas.drawRoundRect(x,y,x+7,y+7,2,2,paint);break;
   case "Automatizar":path(canvas,14,2,4,14,11,14,10,22,21,9,14,9,14,2);break;
   case "Ajustes":
    canvas.drawCircle(12,12,6,paint);canvas.drawCircle(12,12,2.3f,paint);
    for(int i=0;i<8;i++){double a=i*Math.PI/4;line(canvas,12+(float)Math.cos(a)*7,12+(float)Math.sin(a)*7,12+(float)Math.cos(a)*9,12+(float)Math.sin(a)*9);}break;
   case "Clima":
    canvas.drawCircle(9,8,4,paint);line(canvas,9,1,9,2);line(canvas,2,8,1,8);line(canvas,3,3,4,4);
    path(canvas,5,20,18,20,21,18,21,14,19,12,15,12,13,10,9,11,7,15,4,15,2,17,3,19,5,20);break;
   default:
    canvas.drawRoundRect(9,2,15,15,3,3,paint);
    canvas.drawArc(5,6,19,19,0,180,false,paint);line(canvas,12,19,12,23);line(canvas,8,23,16,23);
  }
  canvas.restore();
 }
 private void line(Canvas c,float a,float b,float d,float e){c.drawLine(a,b,d,e,paint);}
 private void path(Canvas c,float... points){Path path=new Path();path.moveTo(points[0],points[1]);for(int i=2;i<points.length;i+=2)path.lineTo(points[i],points[i+1]);c.drawPath(path,paint);}
 @Override public int getIntrinsicWidth(){return size;}
 @Override public int getIntrinsicHeight(){return size;}
 @Override public void setAlpha(int alpha){paint.setAlpha(alpha);invalidateSelf();}
 @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
 @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
