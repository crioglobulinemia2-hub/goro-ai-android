package ar.goro.ai.core;

/** Converts a short diagnostic WAV to the PCM16 mono format used by the Z02 recognizer. */
public final class WavToPcm16k {
 public static final int RATE=16000;
 private WavToPcm16k(){}
 public static byte[] convert(byte[] wav){
  NvidiaWavValidator.Info info=NvidiaWavValidator.inspect(wav);
  if(info.durationMs<100||info.durationMs>10000)
   throw new IllegalArgumentException("La comprobación de voz requiere entre 0,1 y 10 segundos.");
  int frames=info.dataLength/(info.channels*2);
  int outputFrames=(int)Math.round(frames*(double)RATE/info.sampleRate);
  if(outputFrames<1600||outputFrames>160000)throw new IllegalArgumentException("Duración de audio fuera de rango.");
  byte[] output=new byte[outputFrames*2];
  double cutoff=Math.min(1d,RATE/(double)info.sampleRate)*0.90d;
  double radius=16d/cutoff;
  for(int i=0;i<outputFrames;i++){
   if((i&1023)==0&&Thread.currentThread().isInterrupted())
    throw new IllegalStateException("Comprobación de voz cancelada.");
   double position=i*(double)info.sampleRate/RATE;
   double value;
   if(info.sampleRate==RATE){value=sample(wav,info,i);}
   else{
    double weighted=0,weights=0;
    int first=Math.max(0,(int)Math.ceil(position-radius));
    int last=Math.min(frames-1,(int)Math.floor(position+radius));
    for(int j=first;j<=last;j++){
     double distance=position-j,x=distance*cutoff;
     double sinc=Math.abs(x)<1e-12?1d:Math.sin(Math.PI*x)/(Math.PI*x);
     double window=0.5d+0.5d*Math.cos(Math.PI*distance/radius);
     double weight=cutoff*sinc*window;
     weighted+=sample(wav,info,j)*weight;weights+=weight;
    }
    value=Math.abs(weights)>1e-12?weighted/weights:0;
   }
   int pcm=(int)Math.max(Short.MIN_VALUE,Math.min(Short.MAX_VALUE,Math.round(value)));
   output[i*2]=(byte)pcm;output[i*2+1]=(byte)(pcm>>8);
  }
  return output;
 }
 private static double sample(byte[] wav,NvidiaWavValidator.Info info,int frame){
  int position=info.dataOffset+frame*info.channels*2;
  int sum=0;
  for(int channel=0;channel<info.channels;channel++){
   int p=position+channel*2;
   sum+=(short)((wav[p]&255)|((wav[p+1]&255)<<8));
  }
  return sum/(double)info.channels;
 }
}
