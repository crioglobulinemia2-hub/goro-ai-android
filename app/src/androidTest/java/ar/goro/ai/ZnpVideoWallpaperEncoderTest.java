package ar.goro.ai;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileInputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ZnpVideoWallpaperEncoderTest {
 @Test public void realGoroGifBecomesThreeSecondZ02MjpegAvi()throws Exception{
  Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
  File gif=ZnpScreenAsset.forDisplay(context,240,296);
  File avi=ZnpVideoWallpaperEncoder.encode(context,gif,240,296,3);
  try{
   assertTrue("AVI must be larger than a trivial header",avi.isFile()&&avi.length()>1024);
   byte[] header=new byte[12];
   try(FileInputStream in=new FileInputStream(avi)){assertEquals(12,in.read(header));}
   assertEquals("RIFF",new String(header,0,4,java.nio.charset.StandardCharsets.US_ASCII));
   assertEquals("AVI ",new String(header,8,4,java.nio.charset.StandardCharsets.US_ASCII));
   byte[] data=new byte[(int)avi.length()];
   try(FileInputStream in=new FileInputStream(avi)){
    int read=0,n;while(read<data.length&&(n=in.read(data,read,data.length-read))>0)read+=n;
    assertEquals(data.length,read);
   }
   int avih=find(data,"avih"),strf=find(data,"strf"),movi=find(data,"movi"),idx1=find(data,"idx1");
   assertTrue(avih>0&&strf>0&&movi>0&&idx1>movi);
   assertEquals(90,u32(data,avih+8+16));
   assertEquals(240,u32(data,avih+8+32));
   assertEquals(296,u32(data,avih+8+36));
   assertEquals("MJPG",new String(data,strf+8+16,4,java.nio.charset.StandardCharsets.US_ASCII));
   assertEquals(90,u32(data,idx1+4)/16);
  }finally{
   avi.delete();gif.delete();
  }
 }
 private static int find(byte[] data,String value){
  byte[] needle=value.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
  outer:for(int i=0;i<=data.length-needle.length;i++){
   for(int j=0;j<needle.length;j++)if(data[i+j]!=needle[j])continue outer;
   return i;
  }
  return -1;
 }
 private static long u32(byte[] b,int p){
  return (b[p]&255L)|((b[p+1]&255L)<<8)|((b[p+2]&255L)<<16)|((b[p+3]&255L)<<24);
 }
}
