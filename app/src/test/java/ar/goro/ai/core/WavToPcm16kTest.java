package ar.goro.ai.core;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;
public class WavToPcm16kTest {
 @Test public void preservesPcm16kSignsAndLength(){
  short[] samples=new short[3200];samples[0]=Short.MIN_VALUE;samples[1]=Short.MAX_VALUE;samples[2]=-1;
  byte[] pcm=WavToPcm16k.convert(DiagnosticWavFixture.wav(16000,1,samples));
  assertEquals(6400,pcm.length);
  for(int i=0;i<samples.length;i++)assertEquals(samples[i],DiagnosticWavFixture.value(pcm,i));
 }
 @Test public void downmixesOppositeStereoChannelsWithoutOverflow(){
  short[] samples=new short[6400];
  for(int i=0;i<samples.length;i+=2){samples[i]=30000;samples[i+1]=-30000;}
  byte[] pcm=WavToPcm16k.convert(DiagnosticWavFixture.wav(16000,2,samples));
  assertEquals(6400,pcm.length);
  for(byte value:pcm)assertEquals(0,value);
 }
 @Test public void resamplingKeepsDurationForDiegoAndLowerRateAudio(){
  assertEquals(32000,WavToPcm16k.convert(DiagnosticWavFixture.wav(22050,1,new short[22050])).length);
  assertEquals(32000,WavToPcm16k.convert(DiagnosticWavFixture.wav(8000,1,new short[8000])).length);
 }
 @Test public void fullScaleConstantRemainsClampedAndDoesNotWrap(){
  short[] samples=new short[4410];Arrays.fill(samples,Short.MAX_VALUE);
  byte[] pcm=WavToPcm16k.convert(DiagnosticWavFixture.wav(22050,1,samples));
  for(int i=0;i<pcm.length/2;i++)assertEquals(Short.MAX_VALUE,DiagnosticWavFixture.value(pcm,i));
 }
 @Test public void downsamplingFiltersFrequenciesAboveTargetNyquist(){
  byte[] low=tone(1000),high=tone(11000);
  assertTrue(rms(high)<rms(low)*0.08);
  assertTrue(rms(low)>5000);
 }
 private byte[] tone(int frequency){
  short[] samples=new short[12000];
  for(int i=0;i<samples.length;i++)samples[i]=(short)Math.round(12000*Math.sin(2*Math.PI*frequency*i/48000d));
  return WavToPcm16k.convert(DiagnosticWavFixture.wav(48000,1,samples));
 }
 private double rms(byte[] pcm){
  double squares=0;int count=0;
  for(int i=200;i<pcm.length/2-200;i++){double sample=DiagnosticWavFixture.value(pcm,i);squares+=sample*sample;count++;}
  return Math.sqrt(squares/count);
 }
 @Test public void rejectsTruncatedOrOverlongDiagnostic(){
  try{WavToPcm16k.convert(new byte[44]);fail();}catch(IllegalArgumentException expected){}
  try{WavToPcm16k.convert(DiagnosticWavFixture.wav(16000,1,new short[176000]));fail();}catch(IllegalArgumentException expected){}
 }
}
