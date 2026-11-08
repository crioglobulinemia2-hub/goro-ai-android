package ar.goro.ai.voice;

import java.util.Arrays;

/** PCM16 little-endian mono signal checks. Never classifies content or stores audio. */
public final class PcmSpeechSignal {
 public static final int FRAME_SAMPLES=320; // 20 ms at 16 kHz
 public static final double VOICE_RMS=45;
 private PcmSpeechSignal(){}
 public static double rms(byte[] pcm,int start,int bytes){
  int count=bytes/2;if(count==0)return 0;
  double sum=0,squares=0;
  for(int i=start;i<start+count*2;i+=2){int v=(short)((pcm[i]&255)|(pcm[i+1]<<8));sum+=v;squares+=(double)v*v;}
  double mean=sum/count;return Math.sqrt(Math.max(0,squares/count-mean*mean));
 }
 /** Remove quiet padding and modestly amplify weak speech, preserving its sample rate. */
 public static byte[] prepare(byte[] pcm){
  if(pcm==null||pcm.length<3200||pcm.length%2!=0)return null;
  int first=-1,last=-1,active=0;
  for(int i=0;i+FRAME_SAMPLES*2<=pcm.length;i+=FRAME_SAMPLES*2){
   if(rms(pcm,i,FRAME_SAMPLES*2)>=VOICE_RMS){if(first<0)first=i;last=i+FRAME_SAMPLES*2;active++;}
  }
  if(active<4)return null; // Reject silence / isolated clicks before any cloud request.
  first=Math.max(0,first-6400);last=Math.min(pcm.length,last+6400);
  byte[] result=Arrays.copyOfRange(pcm,first,last);
  double mean=0;for(int i=0;i<result.length;i+=2)mean+=(short)((result[i]&255)|(result[i+1]<<8));mean/=result.length/2;
  double peak=0;for(int i=0;i<result.length;i+=2)peak=Math.max(peak,Math.abs((short)((result[i]&255)|(result[i+1]<<8))-mean));
  double gain=peak>0?Math.max(1,Math.min(4,12000/peak)):1;
  for(int i=0;i<result.length;i+=2){
   int sample=(int)Math.round(((short)((result[i]&255)|(result[i+1]<<8))-mean)*gain);
   sample=Math.max(-32768,Math.min(32767,sample));result[i]=(byte)sample;result[i+1]=(byte)(sample>>8);
  }
  return result;
 }
}
