package ar.goro.ai.voice;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;
public class PcmSpeechSignalTest {
 public static byte[] tone(int ms,int level,int dc){
  byte[] b=new byte[ms*32];for(int i=0;i<b.length/2;i++){int v=dc+(int)(level*Math.sin(2*Math.PI*220*i/16000));b[i*2]=(byte)v;b[i*2+1]=(byte)(v>>8);}return b;
 }
 @Test public void constantNoiseAndSilenceAreNotSentToRecognition(){
  assertNull(PcmSpeechSignal.prepare(new byte[64000]));
  assertNull(PcmSpeechSignal.prepare(tone(1000,0,8000)));
  assertNull(PcmSpeechSignal.prepare(tone(1000,30,0)));
 }
 @Test public void lowAmplitudeVoiceStillPassesLocalSpeechGate(){
  byte[] result=PcmSpeechSignal.prepare(tone(1000,65,0));
  assertNotNull("Low-level speech must not be discarded before Parakeet",result);
  assertTrue(PcmSpeechSignal.rms(result,0,result.length)>100);
 }
 @Test public void quietVoiceIsTrimmedAmplifiedAndNotClipped(){
  byte[] p=new byte[32000*3];System.arraycopy(tone(1000,300,200),0,p,32000,32000);
  byte[] result=PcmSpeechSignal.prepare(p);assertNotNull(result);assertTrue(result.length<p.length);
  assertTrue(PcmSpeechSignal.rms(result,6400,32000)>500);
  for(int i=0;i<result.length;i+=2)assertTrue(Math.abs((short)((result[i]&255)|(result[i+1]<<8)))<32767);
 }
 @Test public void phraseEndsAfterEightTenthsOfASecondOfSilence(){
  PcmSpeechEndpoint e=new PcmSpeechEndpoint();assertFalse(e.accept(tone(700,2000,0)));
  assertFalse(e.accept(new byte[780*32]));assertTrue(e.accept(new byte[20*32]));
 }
 @Test public void shortPausesAndContinuousSpeechDoNotEndThePhrase(){
  PcmSpeechEndpoint e=new PcmSpeechEndpoint();assertFalse(e.accept(tone(1000,2000,0)));
  assertFalse(e.accept(new byte[300*32]));assertFalse(e.accept(tone(1000,2000,0)));
 }
 @Test public void byteFragmentsAndOddPacketLengthsPreserveEndpointTiming(){
  PcmSpeechEndpoint e=new PcmSpeechEndpoint();byte[] voice=tone(1000,1000,0);
  for(int i=0;i<voice.length;i+=137)assertFalse(e.accept(Arrays.copyOfRange(voice,i,Math.min(voice.length,i+137))));
  assertTrue(e.accept(new byte[800*32]));
 }
 @Test public void fourSecondsWithoutVoiceStopsCaptureWithoutACloudRequest(){
  PcmSpeechEndpoint e=new PcmSpeechEndpoint();assertFalse(e.accept(new byte[3980*32]));assertTrue(e.accept(new byte[20*32]));
 }
}
