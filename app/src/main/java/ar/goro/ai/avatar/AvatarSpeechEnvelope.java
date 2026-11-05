package ar.goro.ai.avatar;

import ar.goro.ai.core.NvidiaWavValidator;

/** Measures only the validated PCM payload, including both channels when present. */
final class AvatarSpeechEnvelope {
 private final byte[] wav;
 private final NvidiaWavValidator.Info info;

 AvatarSpeechEnvelope(byte[] wav){
  info=NvidiaWavValidator.inspect(wav);
  this.wav=wav;
 }

 long durationMs(){return info.durationMs;}

 float energyAt(long elapsedMs){
  if(elapsedMs<0||elapsedMs>=info.durationMs)return 0f;
  int blockAlign=info.channels*2;
  long frame=elapsedMs*info.sampleRate/1000;
  long byteOffset=frame*blockAlign;
  if(byteOffset>=info.dataLength)return 0f;
  int position=info.dataOffset+(int)byteOffset;
  int end=(int)Math.min((long)info.dataOffset+info.dataLength,
      (long)position+blockAlign*256);
  double squared=0;
  int count=0;
  for(int i=position;i+1<end;i+=2){
   int sample=(short)((wav[i]&255)|((wav[i+1]&255)<<8));
   double normalized=sample/32768.0;
   squared+=normalized*normalized;
   count++;
  }
  return count==0?0f:Math.min(1f,(float)Math.sqrt(squared/count)*7.2f);
 }
}
