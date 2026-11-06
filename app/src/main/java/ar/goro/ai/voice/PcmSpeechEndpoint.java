package ar.goro.ai.voice;

/** Local end-of-utterance detection, independent of the device's optional VAD. */
public final class PcmSpeechEndpoint {
 private final byte[] frame=new byte[PcmSpeechSignal.FRAME_SAMPLES*2];
 private int used,activeFrames,quietFrames,totalFrames;
 private boolean ended;
 public boolean accept(byte[] pcm){
  if(ended)return true;
  for(byte b:pcm){
   frame[used++]=b;
   if(used!=frame.length)continue;
   used=0;totalFrames++;
   if(PcmSpeechSignal.rms(frame,0,frame.length)>=PcmSpeechSignal.VOICE_RMS){activeFrames++;quietFrames=0;}
   else quietFrames++;
   if((activeFrames>=4&&quietFrames>=40)||(activeFrames<4&&totalFrames>=200)){ended=true;return true;}
  }
  return false;
 }
}
