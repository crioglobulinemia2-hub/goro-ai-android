package ar.goro.ai;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

/** Minimal OpenDML-free AVI writer for the ZNP Z02 Motion-JPEG wallpaper format. */
final class ZnpMjpegAviMuxer {
 private ZnpMjpegAviMuxer(){}

 static void write(File destination,List<byte[]> frames,int width,int height,int fps)throws IOException{
  if(destination==null)throw new IOException("Destino AVI nulo");
  byte[] data=encode(frames,width,height,fps);
  File parent=destination.getParentFile();
  if(parent!=null&&!parent.exists()&&!parent.mkdirs())throw new IOException("No se pudo crear la carpeta AVI");
  try(FileOutputStream out=new FileOutputStream(destination)){out.write(data);out.flush();}
 }

 static byte[] encode(List<byte[]> frames,int width,int height,int fps)throws IOException{
  if(width<=0||height<=0||fps<=0||frames==null||frames.isEmpty())throw new IOException("Parámetros AVI inválidos");
  if(width>65535||height>65535||frames.size()>10000)throw new IOException("AVI excede los límites admitidos");
  int maxFrame=0;
  for(byte[] frame:frames){
   if(frame==null||frame.length<4||(frame[0]&255)!=0xff||(frame[1]&255)!=0xd8
      ||(frame[frame.length-2]&255)!=0xff||(frame[frame.length-1]&255)!=0xd9)
    throw new IOException("Fotograma MJPEG inválido");
   maxFrame=Math.max(maxFrame,frame.length);
  }
  ByteArrayOutputStream movi=new ByteArrayOutputStream();
  ByteArrayOutputStream index=new ByteArrayOutputStream(frames.size()*16);
  for(byte[] frame:frames){
   int offset=movi.size();
   fourcc(movi,"00dc");u32(movi,frame.length);movi.write(frame);
   if((frame.length&1)!=0)movi.write(0);
   fourcc(index,"00dc");u32(index,0x10);u32(index,offset);u32(index,frame.length);
  }
  ByteArrayOutputStream avih=new ByteArrayOutputStream(56);
  u32(avih,1000000/fps);u32(avih,(long)maxFrame*fps);u32(avih,0);u32(avih,0x10);
  u32(avih,frames.size());u32(avih,0);u32(avih,1);u32(avih,maxFrame);
  u32(avih,width);u32(avih,height);u32(avih,0);u32(avih,0);u32(avih,0);u32(avih,0);

  ByteArrayOutputStream strh=new ByteArrayOutputStream(56);
  fourcc(strh,"vids");fourcc(strh,"MJPG");u32(strh,0);u16(strh,0);u16(strh,0);
  u32(strh,0);u32(strh,1);u32(strh,fps);u32(strh,0);u32(strh,frames.size());
  u32(strh,maxFrame);u32(strh,0xffffffffL);u32(strh,0);
  u16(strh,0);u16(strh,0);u16(strh,width);u16(strh,height);

  ByteArrayOutputStream strf=new ByteArrayOutputStream(40);
  u32(strf,40);u32(strf,width);u32(strf,height);u16(strf,1);u16(strf,24);
  fourcc(strf,"MJPG");u32(strf,maxFrame);u32(strf,0);u32(strf,0);u32(strf,0);u32(strf,0);

  ByteArrayOutputStream strl=new ByteArrayOutputStream();
  strl.write(chunk("strh",strh.toByteArray()));strl.write(chunk("strf",strf.toByteArray()));
  ByteArrayOutputStream hdrl=new ByteArrayOutputStream();
  hdrl.write(chunk("avih",avih.toByteArray()));hdrl.write(list("strl",strl.toByteArray()));

  ByteArrayOutputStream body=new ByteArrayOutputStream();
  fourcc(body,"AVI ");body.write(list("hdrl",hdrl.toByteArray()));
  body.write(list("movi",movi.toByteArray()));body.write(chunk("idx1",index.toByteArray()));
  return chunk("RIFF",body.toByteArray());
 }

 private static byte[] chunk(String id,byte[] data)throws IOException{
  ByteArrayOutputStream out=new ByteArrayOutputStream(data.length+8);
  fourcc(out,id);u32(out,data.length);out.write(data);
  if((data.length&1)!=0)out.write(0);
  return out.toByteArray();
 }
 private static byte[] list(String type,byte[] data)throws IOException{
  ByteArrayOutputStream out=new ByteArrayOutputStream(data.length+4);
  fourcc(out,type);out.write(data);return chunk("LIST",out.toByteArray());
 }
 private static void fourcc(ByteArrayOutputStream out,String value)throws IOException{
  if(value.length()!=4)throw new IOException("FourCC inválido: "+value);
  out.write(value.getBytes("US-ASCII"));
 }
 private static void u16(ByteArrayOutputStream out,long value){
  out.write((int)value&255);out.write((int)(value>>>8)&255);
 }
 private static void u32(ByteArrayOutputStream out,long value){
  out.write((int)value&255);out.write((int)(value>>>8)&255);
  out.write((int)(value>>>16)&255);out.write((int)(value>>>24)&255);
 }
}
