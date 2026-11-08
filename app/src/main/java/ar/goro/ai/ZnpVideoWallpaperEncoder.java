package ar.goro.ai;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Movie;
import android.graphics.Paint;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Converts the authored GORO GIF into the Z02's documented video-wallpaper container. */
final class ZnpVideoWallpaperEncoder {
 static final int Z02_WIDTH=240;
 static final int Z02_HEIGHT=296;
 static final int FPS=30;
 static final int DEFAULT_SECONDS=3;
 private ZnpVideoWallpaperEncoder(){}

 static File encode(Context context,File sourceGif,int width,int height,int seconds)throws IOException{
  if(context==null)throw new IOException("Contexto Android no disponible");
  File destination=new File(context.getCacheDir(),"GORO-Z02-240x296.avi");
  encode(sourceGif,destination,width,height,seconds);
  return destination;
 }

 static void encode(File sourceGif,File destination,int width,int height,int seconds)throws IOException{
  if(width!=Z02_WIDTH||height!=Z02_HEIGHT)
   throw new IOException("El video wallpaper Z02 requiere 240×296; visor informado: "+width+"×"+height);
  if(seconds<3||seconds>8)throw new IOException("El Z02 admite videos de 3 a 8 segundos");
  if(sourceGif==null||!sourceGif.isFile()||!sourceGif.canRead())
   throw new IOException("No se encuentra el GIF original de GORO");
  final Movie movie;
  try(FileInputStream in=new FileInputStream(sourceGif)){movie=Movie.decodeStream(in);}
  if(movie==null||movie.width()<=0||movie.height()<=0)
   throw new IOException("Android no pudo decodificar el GIF de GORO");
  int sourceWidth=movie.width(),sourceHeight=movie.height();
  int reportedDuration=movie.duration();
  int naturalDuration=reportedDuration>0?reportedDuration:1000;
  int frameCount=FPS*seconds;
  float scale=Math.max(width/(float)sourceWidth,height/(float)sourceHeight);
  float drawWidth=sourceWidth*scale,drawHeight=sourceHeight*scale;
  float left=(width-drawWidth)/2f,top=(height-drawHeight)/2f;
  Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
  Canvas canvas=new Canvas(bitmap);
  Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
  List<byte[]> frames=new ArrayList<>(frameCount);
  try{
   for(int frame=0;frame<frameCount;frame++){
    int time=(int)(((long)frame*1000/FPS)%naturalDuration);
    movie.setTime(time);
    canvas.drawColor(Color.BLACK);
    movie.draw(canvas,left,top,paint);
    ByteArrayOutputStream jpeg=new ByteArrayOutputStream(24*1024);
    if(!bitmap.compress(Bitmap.CompressFormat.JPEG,80,jpeg)||jpeg.size()<4)
     throw new IOException("No se pudo codificar el fotograma "+(frame+1));
    frames.add(jpeg.toByteArray());
   }
   ZnpMjpegAviMuxer.write(destination,frames,width,height,FPS);
  }finally{
   bitmap.recycle();
   frames.clear();
  }
  if(!destination.isFile()||destination.length()<1024)
   throw new IOException("El AVI GORO quedó incompleto");
 }
}
