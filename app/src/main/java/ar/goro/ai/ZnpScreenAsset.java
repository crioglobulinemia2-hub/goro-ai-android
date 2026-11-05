package ar.goro.ai;

import android.content.Context;
import android.util.Base64;
import java.io.*;

/** Picks a prevalidated animation using the RCSP-reported physical resolution. */
final class ZnpScreenAsset {
 static File forDisplay(Context context,int width,int height)throws IOException{
  if(width!=240||height!=296)throw new IOException("No hay animación preparada para "+width+"×"+height);
  byte[] encoded;
  try(InputStream in=context.getAssets().open("goro/znp_faces_240x296.b64");ByteArrayOutputStream out=new ByteArrayOutputStream()){
   byte[] buffer=new byte[8192];int n;
   while((n=in.read(buffer))!=-1){out.write(buffer,0,n);if(out.size()>2*1024*1024)throw new IOException("Animación demasiado grande");}
   encoded=out.toByteArray();
  }
  byte[] gif=Base64.decode(encoded,Base64.DEFAULT);
  if(gif.length<10||gif[0]!='G'||gif[1]!='I'||gif[2]!='F'
      ||((gif[6]&255)|((gif[7]&255)<<8))!=width||((gif[8]&255)|((gif[9]&255)<<8))!=height)
   throw new IOException("Recurso de pantalla inválido");
  File file=new File(context.getCacheDir(),"GORO-Z02-"+width+"x"+height+".gif");
  try(FileOutputStream out=new FileOutputStream(file)){out.write(gif);}
  return file;
 }
}
