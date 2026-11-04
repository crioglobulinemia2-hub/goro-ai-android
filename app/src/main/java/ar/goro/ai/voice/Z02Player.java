package ar.goro.ai.voice;

import android.content.Context;
import android.media.*;
import android.os.*;
import android.media.AudioDeviceInfo;
import ar.goro.ai.core.NvidiaWavValidator;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Plays PCM only through the selected and verified Z02 A2DP endpoint. */
public final class Z02Player {
 public interface Listener {void onState(String state);void onError(String error);void onCompleted();}
 private final AudioManager audio;
 private final Handler main=new Handler(Looper.getMainLooper());
 private String targetAddress="",targetName="";
 private long sequence;
 private volatile Playback active;
 private Listener listener;
 private Runnable pendingStart;
 private static final class Playback {
  final long id;final AtomicBoolean running=new AtomicBoolean(true);
  volatile AudioTrack track;
  Playback(long id){this.id=id;}
 }
 private final AudioDeviceCallback deviceCallback=new AudioDeviceCallback(){
  @Override public void onAudioDevicesAdded(AudioDeviceInfo[] devices){ }
  @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] devices){ }
 };
 public Z02Player(Context context,Listener listener){
  audio=(AudioManager)context.getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
  this.listener=listener;
  if(audio!=null)audio.registerAudioDeviceCallback(deviceCallback,main);
 }
 public synchronized void setTarget(String address,String name){
  stop();targetAddress=address==null?"":address;targetName=name==null?"":name.trim();
 }
 public List<AudioDeviceInfo> outputs(){
  if(audio==null||targetAddress.isEmpty())return Collections.emptyList();
  List<AudioDeviceInfo> connected=new ArrayList<>();
  for(AudioDeviceInfo d:audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS))
   if(d.getType()==AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)connected.add(d);
  List<AudioDeviceInfo> exact=new ArrayList<>();
  if(Build.VERSION.SDK_INT>=28)for(AudioDeviceInfo d:connected)
   if(targetAddress.equalsIgnoreCase(d.getAddress()))exact.add(d);
  if(!exact.isEmpty())return exact;
  List<AudioDeviceInfo> named=new ArrayList<>();
  for(AudioDeviceInfo d:connected)
   if(!targetName.isEmpty()&&targetName.equalsIgnoreCase(d.getProductName().toString().trim()))named.add(d);
  return named.size()==1?named:Collections.emptyList();
 }
 public void playWav(byte[] wav){
  try{
   NvidiaWavValidator.Info info=NvidiaWavValidator.inspect(wav);
   byte[] pcm=Arrays.copyOfRange(wav,info.dataOffset,info.dataOffset+info.dataLength);
   stop();
   waitForOutput(pcm,info.sampleRate,info.channels,0);
  }catch(Exception error){emitError("El WAV de Magpie no es reproducible: "+error.getMessage());}
 }
 private void waitForOutput(byte[] pcm,int rate,int channels,int attempt){
  if(attempt==0)emitStateText("Esperando audio multimedia del Z02");
  List<AudioDeviceInfo> devices=outputs();
  if(devices.size()==1){play(pcm,rate,channels,devices.get(0).getId());return;}
  if(attempt>=30){emitError("El Z02 no presentó una salida A2DP única después de 15 segundos. No se reprodujo por la tablet.");return;}
  pendingStart=()->waitForOutput(pcm,rate,channels,attempt+1);
  main.postDelayed(pendingStart,500);
 }
 private void emitStateText(String value){main.post(()->{if(listener!=null)listener.onState(value);});}
 public void play(byte[] pcm,int rate,int channels,int targetId){
  stop();
  Playback session=new Playback(++sequence);active=session;
  AudioDeviceInfo target=null;
  for(AudioDeviceInfo d:outputs())if(d.getId()==targetId)target=d;
  if(target==null){fail(session,"La salida multimedia Z02 no está conectada o no es inequívoca.");return;}
  if(audio.getStreamVolume(AudioManager.STREAM_MUSIC)==0){fail(session,"El volumen multimedia está en cero.");return;}
  if(pcm==null||pcm.length==0||channels<1||channels>2||pcm.length%(channels*2)!=0||rate<8000||rate>96000){
   fail(session,"Formato PCM de voz inválido.");return;
  }
  final AudioDeviceInfo selected=target;
  Thread worker=new Thread(()->{
   AudioTrack track=null;
   AudioManager.OnAudioFocusChangeListener focus=change->{
    if(change!=AudioManager.AUDIOFOCUS_GAIN){session.running.set(false);if(session.track!=null)try{session.track.setVolume(0f);}catch(Exception ignored){}}
   };
   boolean focused=false;
   try{
    @SuppressWarnings("deprecation")
    int focusResult=audio.requestAudioFocus(focus,AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
    focused=focusResult==AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    if(!focused)throw new IllegalStateException("No se obtuvo el foco de audio.");
    int mask=channels==1?AudioFormat.CHANNEL_OUT_MONO:AudioFormat.CHANNEL_OUT_STEREO;
    int minimum=AudioTrack.getMinBufferSize(rate,mask,AudioFormat.ENCODING_PCM_16BIT);
    if(minimum<=0)throw new IllegalStateException("Formato de audio no soportado.");
    track=new AudioTrack(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
      .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build(),
      new AudioFormat.Builder().setSampleRate(rate).setChannelMask(mask)
       .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build(),
      Math.max(minimum,rate*channels/5),AudioTrack.MODE_STREAM,AudioManager.AUDIO_SESSION_ID_GENERATE);
    session.track=track;
    if(track.getState()!=AudioTrack.STATE_INITIALIZED)throw new IllegalStateException("AudioTrack no se inicializó.");
    if(!track.setPreferredDevice(selected))throw new IllegalStateException("Android rechazó la salida Z02.");
    track.setVolume(0f);track.play();
    byte[] silence=new byte[(rate/50)*channels*2];
    long frames=0,deadline=SystemClock.elapsedRealtime()+5000;boolean routed=false;
    while(session.running.get()&&SystemClock.elapsedRealtime()<deadline){
     int n=track.write(silence,0,silence.length,AudioTrack.WRITE_NON_BLOCKING);
     if(n<0)throw new IllegalStateException("Error preparando audio: "+n);
     frames+=n/(channels*2);
     AudioDeviceInfo actual=track.getRoutedDevice();
     if(actual!=null&&actual.getType()==AudioDeviceInfo.TYPE_BLUETOOTH_A2DP&&actual.getId()==selected.getId()){routed=true;break;}
     SystemClock.sleep(15);
    }
    if(!routed||!session.running.get())throw new IllegalStateException("No se verificó la ruta real al Z02.");
    track.setVolume(1f);emitState(session,"Reproduciendo por Z02");
    int offset=0;long writeDeadline=SystemClock.elapsedRealtime()+pcm.length*1000L/(rate*channels*2)+5000;
    long lastProgress=SystemClock.elapsedRealtime();
    while(session.running.get()&&offset<pcm.length){
     long now=SystemClock.elapsedRealtime();
     if(now>=writeDeadline||now-lastProgress>=2000)throw new IllegalStateException("La salida de audio dejó de avanzar.");
     AudioDeviceInfo actual=track.getRoutedDevice();
     if(actual==null||actual.getType()!=AudioDeviceInfo.TYPE_BLUETOOTH_A2DP||actual.getId()!=selected.getId())
      throw new IllegalStateException("La ruta cambió durante la respuesta.");
     int n=track.write(pcm,offset,Math.min(1280,pcm.length-offset),AudioTrack.WRITE_NON_BLOCKING);
     if(n<0)throw new IllegalStateException("Falló la escritura de audio: "+n);
     offset+=n;frames+=n/(channels*2);if(n>0)lastProgress=SystemClock.elapsedRealtime();else SystemClock.sleep(10);
    }
    long drain=SystemClock.elapsedRealtime()+5000;
    while(session.running.get()&&(track.getPlaybackHeadPosition()&0xffffffffL)<frames&&SystemClock.elapsedRealtime()<drain)
     SystemClock.sleep(10);
    if(!session.running.get()||(track.getPlaybackHeadPosition()&0xffffffffL)<frames)
     throw new IllegalStateException("Audio interrumpido antes de completar.");
    main.post(()->{if(active==session){active=null;if(listener!=null)listener.onCompleted();}});
   }catch(Exception error){fail(session,error.getMessage()==null?"Falló la reproducción por Z02.":error.getMessage());}
   finally{
    session.running.set(false);
    if(track!=null){try{track.setVolume(0f);}catch(Exception ignored){}try{track.pause();}catch(Exception ignored){}try{track.flush();}catch(Exception ignored){}try{track.release();}catch(Exception ignored){}}
    session.track=null;
    if(focused){@SuppressWarnings("deprecation") boolean ignored=audio.abandonAudioFocus(focus)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED;}
   }
  },"GORO-Z02-A2DP").start();
 }
 private void fail(Playback session,String message){
  session.running.set(false);
  main.post(()->{if(active==session){active=null;if(listener!=null)listener.onError(message);}});
 }
 private void emitError(String message){main.post(()->{if(listener!=null)listener.onError(message);});}
 private void emitState(Playback session,String state){main.post(()->{if(active==session&&listener!=null)listener.onState(state);});}
 public void stop(){
  if(pendingStart!=null)main.removeCallbacks(pendingStart);
  pendingStart=null;
  Playback old=active;active=null;
  if(old!=null){old.running.set(false);if(old.track!=null)try{old.track.setVolume(0f);}catch(Exception ignored){}}
 }
 public void close(){stop();if(audio!=null)try{audio.unregisterAudioDeviceCallback(deviceCallback);}catch(Exception ignored){}main.removeCallbacksAndMessages(null);listener=null;}
}
