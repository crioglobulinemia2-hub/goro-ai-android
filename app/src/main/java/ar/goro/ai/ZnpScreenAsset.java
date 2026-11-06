package ar.goro.ai;

import android.content.Context;
import android.util.Base64;
import java.io.*;

/** Selects the GORO animation matching the physical screen reported by the selected ZNP. */
final class ZnpScreenAsset {
 private static final String[] LANDSCAPE_PARTS={
  "znp_faces_00.b64","znp_faces_00b.b64","znp_faces_01.b64","znp_faces_02.b64",
  "znp_faces_03.b64","znp_faces_04.b64","znp_faces_05.b64","znp_faces_05b.b64",
  "znp_faces_06.b64","znp_faces_07.b64","znp_faces_08.b64","znp_faces_09.b64",
  "znp_faces_10.b64","znp_faces_11.b64","znp_faces_12.b64","znp_faces_12b.b64",
  "znp_faces_13.b64","znp_faces_14.b64","znp_faces_15.b64","znp_faces_16.b64","znp_faces_17.b64"
 };
 static File forDisplay(Context context,int width,int height)throws IOException{
  byte[] gif;
  if(width==320&&height==172){
   StringBuilder encoded=new StringBuilder();
   for(String part:LANDSCAPE_PARTS){
    try(InputStream in=context.getAssets().open("goro/"+part);
        ByteArrayOutputStream out=new ByteArrayOutputStream()){
     byte[] buffer=new byte[8192];int n;
     while((n=in.read(buffer))!=-1){
      out.write(buffer,0,n);
      if(out.size()>512*1024)throw new IOException("Fragmento de animación demasiado grande");
     }
     encoded.append(out.toString("US-ASCII").trim());
    }
   }
   try{gif=Base64.decode(encoded.toString(),Base64.DEFAULT);}
   catch(IllegalArgumentException invalid){throw new IOException("Base64 de animación horizontal inválido",invalid);}
  }else if(width==240&&height==296){
   try(InputStream in=context.getAssets().open("goro/znp_faces_240x296.b64");
       ByteArrayOutputStream out=new ByteArrayOutputStream()){
    byte[] buffer=new byte[8192];int n;
    while((n=in.read(buffer))!=-1){
     out.write(buffer,0,n);
     if(out.size()>2*1024*1024)throw new IOException("Animación demasiado grande");
    }
    try{gif=Base64.decode(out.toByteArray(),Base64.DEFAULT);}
    catch(IllegalArgumentException invalid){throw new IOException("Base64 de animación vertical inválido",invalid);}
   }
  }else{
   throw new IOException("No hay animación GORO preparada para "+width+"×"+height);
  }
  if(gif.length<10||(gif[0]!='G'||gif[1]!='I'||gif[2]!='F')
      ||((gif[6]&255)|((gif[7]&255)<<8))!=width
      ||((gif[8]&255)|((gif[9]&255)<<8))!=height)
   throw new IOException("La animación GORO no coincide con la resolución física del visor");
  File file=new File(context.getCacheDir(),"GORO-ZNP-"+width+"x"+height+".gif");
  try(FileOutputStream out=new FileOutputStream(file)){out.write(gif);out.flush();}
  return file;
 }
}
