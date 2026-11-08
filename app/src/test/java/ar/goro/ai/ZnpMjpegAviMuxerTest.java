package ar.goro.ai;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class ZnpMjpegAviMuxerTest {
 private static byte[] jpeg(int marker){
  return new byte[]{(byte)0xff,(byte)0xd8,(byte)marker,0x11,0x22,(byte)0xff,(byte)0xd9};
 }
 private static int find(byte[] data,String text){
  byte[] needle=text.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
  outer:for(int i=0;i<=data.length-needle.length;i++){
   for(int j=0;j<needle.length;j++)if(data[i+j]!=needle[j])continue outer;
   return i;
  }
  return -1;
 }
 private static long u32(byte[] b,int p){
  return (b[p]&255L)|((b[p+1]&255L)<<8)|((b[p+2]&255L)<<16)|((b[p+3]&255L)<<24);
 }
 @Test public void writesValidRiffAviWithMjpegVideoHeadersAndIndex()throws Exception{
  List<byte[]> frames=Arrays.asList(jpeg(1),jpeg(2),jpeg(3));
  byte[] avi=ZnpMjpegAviMuxer.encode(frames,240,296,30);
  assertEquals("RIFF",new String(avi,0,4,java.nio.charset.StandardCharsets.US_ASCII));
  assertEquals("AVI ",new String(avi,8,4,java.nio.charset.StandardCharsets.US_ASCII));
  assertEquals(avi.length-8,u32(avi,4));
  int avih=find(avi,"avih");assertTrue(avih>0);
  assertEquals(3,u32(avi,avih+8+16));
  assertEquals(240,u32(avi,avih+8+32));
  assertEquals(296,u32(avi,avih+8+36));
  int strf=find(avi,"strf");assertTrue(strf>0);
  assertEquals("MJPG",new String(avi,strf+8+16,4,java.nio.charset.StandardCharsets.US_ASCII));
  int movi=find(avi,"movi");assertTrue(movi>0);
  assertEquals("00dc",new String(avi,movi+4,4,java.nio.charset.StandardCharsets.US_ASCII));
  int idx1=find(avi,"idx1");assertTrue(idx1>movi);
  assertEquals(48,u32(avi,idx1+4));
  assertEquals(3,(avi.length>0?u32(avi,idx1+4)/16:0));
 }
 @Test public void rejectsNonJpegFrames()throws Exception{
  try{
   ZnpMjpegAviMuxer.encode(Arrays.asList(new byte[]{1,2,3,4}),240,296,30);
   fail("Expected invalid MJPEG frame to be rejected");
  }catch(IOException expected){assertTrue(expected.getMessage().contains("MJPEG"));}
 }
 @Test public void rejectsEmptyFrameLists()throws Exception{
  try{
   ZnpMjpegAviMuxer.encode(java.util.Collections.emptyList(),240,296,30);
   fail("Expected empty AVI to be rejected");
  }catch(IOException expected){assertTrue(expected.getMessage().contains("Parámetros"));}
 }
}
