package ar.goro.ai.avatar;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.junit.Test;
import static org.junit.Assert.*;

public class AvatarSpeechEnvelopeTest {
 @Test public void readsFormatAfterOddMetadataChunk()throws Exception{
  AvatarSpeechEnvelope envelope=new AvatarSpeechEnvelope(wav(1,16000,1600,4096,0,true,false));
  assertEquals(100,envelope.durationMs());
  assertEquals(.9f,envelope.energyAt(20),.0001f);
 }

 @Test public void measuresRightChannelWhenLeftIsSilent()throws Exception{
  AvatarSpeechEnvelope envelope=new AvatarSpeechEnvelope(wav(2,24000,2400,0,4096,false,false));
  assertEquals(100,envelope.durationMs());
  assertEquals((float)(.9/Math.sqrt(2)),envelope.energyAt(20),.0001f);
 }

 @Test public void ignoresMetadataAfterPcmAndClosesAtEnd()throws Exception{
  AvatarSpeechEnvelope envelope=new AvatarSpeechEnvelope(wav(1,16000,1600,0,0,false,true));
  assertEquals(0f,envelope.energyAt(99),0f);
  assertEquals(0f,envelope.energyAt(100),0f);
  assertEquals(0f,envelope.energyAt(110),0f);
  assertEquals(0f,envelope.energyAt(Long.MAX_VALUE),0f);
  assertEquals(0f,envelope.energyAt(-1),0f);
 }

 @Test(expected=IllegalArgumentException.class)
 public void rejectsMalformedWav(){new AvatarSpeechEnvelope(new byte[44]);}

 private static byte[] wav(int channels,int rate,int frames,int left,int right,
     boolean prefixMetadata,boolean suffixMetadata)throws IOException{
  ByteArrayOutputStream body=new ByteArrayOutputStream();
  body.write(new byte[]{'W','A','V','E'});
  if(prefixMetadata)chunk(body,"JUNK",new byte[]{1,2,3});
  ByteArrayOutputStream fmt=new ByteArrayOutputStream();
  le16(fmt,1);le16(fmt,channels);le32(fmt,rate);le32(fmt,rate*channels*2);
  le16(fmt,channels*2);le16(fmt,16);
  chunk(body,"fmt ",fmt.toByteArray());
  ByteArrayOutputStream data=new ByteArrayOutputStream();
  for(int i=0;i<frames;i++){
   le16(data,left);
   if(channels==2)le16(data,right);
  }
  chunk(body,"data",data.toByteArray());
  if(suffixMetadata){
   byte[] metadata=new byte[1024];java.util.Arrays.fill(metadata,(byte)0x7f);
   chunk(body,"LIST",metadata);
  }
  ByteArrayOutputStream file=new ByteArrayOutputStream();
  file.write(new byte[]{'R','I','F','F'});le32(file,body.size());file.write(body.toByteArray());
  return file.toByteArray();
 }
 private static void chunk(ByteArrayOutputStream out,String name,byte[] data)throws IOException{
  out.write(name.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
  le32(out,data.length);out.write(data);if((data.length&1)!=0)out.write(0);
 }
 private static void le16(ByteArrayOutputStream out,int value){
  out.write(value&255);out.write((value>>8)&255);
 }
 private static void le32(ByteArrayOutputStream out,int value){
  le16(out,value);le16(out,value>>16);
 }
}
