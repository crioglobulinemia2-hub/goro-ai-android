package ar.goro.ai.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class NvidiaWavValidatorTest {
 private static void write16(byte[] b,int i,int n){b[i]=(byte)n;b[i+1]=(byte)(n>>>8);}
 private static void write32(byte[] b,int i,int n){
  b[i]=(byte)n;b[i+1]=(byte)(n>>>8);b[i+2]=(byte)(n>>>16);b[i+3]=(byte)(n>>>24);
 }
 private static void ascii(byte[] b,int i,String text){
  for(int q=0;q<text.length();q++)b[i+q]=(byte)text.charAt(q);
 }
 private static byte[] wav(int rate,int samples){
  byte[] b=new byte[44+samples*2];ascii(b,0,"RIFF");write32(b,4,b.length-8);
  ascii(b,8,"WAVE");ascii(b,12,"fmt ");write32(b,16,16);
  write16(b,20,1);write16(b,22,1);write32(b,24,rate);
  write32(b,28,rate*2);write16(b,32,2);write16(b,34,16);
  ascii(b,36,"data");write32(b,40,samples*2);
  for(int i=44;i<b.length;i+=2)write16(b,i,(i-44)%65536);
  return b;
 }
 @Test public void authenticPcmAudioParses(){
  NvidiaWavValidator.Info i=NvidiaWavValidator.inspect(wav(22050,22050));
  assertEquals(22050,i.sampleRate);assertEquals(1,i.channels);
  assertEquals(16,i.bitsPerSample);assertEquals(1000,i.durationMs);
  assertEquals(44,i.dataOffset);assertEquals(44100,i.dataLength);
 }
 @Test public void rejectsTruncation(){
  byte[] b=wav(22050,128);
  write32(b,4,b.length+2048);
  try{NvidiaWavValidator.inspect(b);fail("Should reject");}
  catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains("truncado"));}
 }
 @Test public void rejectsEmptyPayload(){
  try{NvidiaWavValidator.inspect(wav(22050,0));fail("Should reject");}
  catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains("muestras"));}
 }
 @Test public void rejectsUnsupportedFormat(){
  byte[] b=wav(22050,128);write16(b,20,3);
  try{NvidiaWavValidator.inspect(b);fail("Should reject");}
  catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains("PCM16"));}
 }
 @Test public void rejectsFakeRiff(){
  byte[] b=wav(22050,128);b[0]=0;
  try{NvidiaWavValidator.inspect(b);fail("Should reject");}
  catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains("RIFF"));}
 }
}
