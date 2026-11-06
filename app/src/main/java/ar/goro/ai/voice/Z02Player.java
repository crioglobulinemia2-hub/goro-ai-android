package ar.goro.ai.voice;

import android.content.Context;
import android.media.*;
import android.os.*;
import android.media.AudioDeviceInfo;
import ar.goro.ai.core.NvidiaWavValidator;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/** Plays PCM only through the selected and verified ZNP A2DP endpoint. */
public final class Z02Player {
 public interface Listener {void onState(String state);void onError(String error);void onCompleted();}
 private final AudioManager audio;
 private final Handler main=new Handler(Looper.getMainLooper());
 private String targetAddress="",targetName="";
 private long sequence;
 private volatile Playback active;
 private volatile boolean closed;
 private final ExecutorService playbackExecutor=Executors.newSingleThreadExecutor(r->new Thread(r,"GORO-Z02-A2DP"));
 private Listener listener;
 private Runnable pendingStart;
 private static final class Playback {
  final long id;final int targetId;final AtomicBoolean running=new AtomicBoolean(true);
  volatile boolean routeVerified;
  volatile AudioTrack track;
  Playback(long id,int targetId){this.id=id;this.targetId=targetId;}
 }
 private final AudioDeviceCallback deviceCallback=new AudioDeviceCallback(){
  @Override public void onAudioDevicesAdded(AudioDeviceInfo[] devices){ }
  @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] devices){
   Playback session=active;
   if(session==null)return;
   for(AudioDeviceInfo device:devices)if(device.getId()==session.targetId){silence(session);return;}
  }
 };
 public Z02Player(Context context,Listener listener){
  audio=(AudioManager)context.getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
  this.listener=listener;
  if(audio!=null)audio.registerAudioDeviceCallback(deviceCallback,main);
 }
 public synchronized void setTarget(String address,String name){
  if(closed)return;
  stop();targetAddress=address==null?"":address;targetName=name==null?"":name.trim();
 }
 public List<AudioDeviceInfo> outputs(){
  if(closed||audio==null||targetAddress.isEmpty())return Collections.emptyList();
  List<AudioDeviceInfo> connected=new ArrayList<>();
  for(AudioDeviceInfo d:audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS))
   if(d.getType()==AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)connected.add(d);
  List<AudioDeviceInfo> exact=new ArrayList<>();
  if(Build.VERSION.SDK_INT>=28)for(AudioDeviceInfo d:connected)
   if(targetAddress.equalsIgnoreCase(d.getAddress()))exact.add(d);
  if(!exact.isEmpty())return exact;
  List<AudioDeviceInfo> named=new ArrayList<>();
  for(AudioDeviceInfo d:connected){
   // A reported different address cannot be overridden by an identical product
   // name. Only old Android versions / devices with no address use the fallback.
   String address=Build.VERSION.SDK_INT>=28?d.getAddress():"";
   if(address!=null&&!address.isEmpty())continue;
   if(!targetName.isEmpty()&&sameZnpModelName(targetName,d.getProductName()==null?"":d.getProductName().toString()))named.add(d);
  }
  return named.size()==1?named:Collections.emptyList();
 }
 private static boolean sameZnpModelName(String a,String b){
  String left=a.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]","");
  String right=b.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]","");
  if(left.equals(right))return true;
  String[] aliases={"ZNPZ01","Z01","ZNPZ02","Z02","ZNPN02","ZN02","N02"};
  for(String alias:aliases)if((left.equals(alias)||right.equals(alias))&&
    (left.contains("Z01")&&right.contains("Z01")||left.contains("Z02")&&right.contains("Z02")||
     (left.contains("N02")||left.contains("ZN02"))&&(right.contains("N02")||right.contains("ZN02"))))return true;
  return false;
 }
 public void playWav(byte[] wav){
  if(closed)return;
  stop();
  try{
   NvidiaWavValidator.Info info=NvidiaWavValidator.inspect(wav);
   byte[] pcm=Arrays.copyOfRange(wav,info.dataOffset,info.dataOffset+info.dataLength);
   waitForOutput(pcm,info.sampleRate,info.channels,0);
  }catch(Exception error){emitError("El WAV de Magpie no es reproducible: "+error.getMessage());}
 }
 private void waitForOutput(byte[] pcm,int rate,int channels,int attempt){
  if(closed)return;
  if(attempt==0)emitStateText("Esperando audio multimedia del dispositivo ZNP");
  List<AudioDeviceInfo> devices=outputs();
  if(devices.size()==1){play(pcm,rate,channels,devices.get(0).getId());return;}
  if(attempt>=30){emitError("El dispositivo ZNP seleccionado no presentó una salida A2DP única después de 15 segundos. No se reprodujo por la tablet.");return;}
  final long request=sequence;
  pendingStart=()->{if(sequence==request)waitForOutput(pcm,rate,channels,attempt+1);};
  main.postDelayed(pendingStart,500);
 }
 private void emitStateText(String value){final long request=sequence;main.post(()->{if(sequence==request&&listener!=null)listener.onState(value);});}
 public void play(byte[] pcm,int rate,int channels,int targetId){
  if(closed)return;
  stop();
  Playback session=new Playback(++sequence,targetId);active=session;
  AudioDeviceInfo target=null;
  for(AudioDeviceInfo d:outputs())if(d.getId()==targetId)target=d;
  if(target==null){fail(session,"La salida multimedia ZNP no está conectada o no es inequívoca.");return;}
  if(audio.getStreamVolume(AudioManager.STREAM_MUSIC)==0){fail(session,"El volumen multimedia está en cero.");return;}
  if(pcm==null||pcm.length==0||channels<1||channels>2||pcm.length%(channels*2)!=0||rate<8000||rate>96000){
   fail(session,"Formato PCM de voz inválido.");return;
  }
  final AudioDeviceInfo selected=target;
  // Serialize ownership of audio focus. A stopped worker must release its focus
  // before its replacement can request a new one.
  try{playbackExecutor.execute(()->{
   if(closed||active!=session||!session.running.get())return;
   AudioTrack track=null;
   AudioRouting.OnRoutingChangedListener routing=router->{
    if(session.routeVerified&&!isSelectedRoute(router.getRoutedDevice(),session.targetId))silence(session);
   };
   AudioManager.OnAudioFocusChangeListener focus=change->{
    if(change!=AudioManager.AUDIOFOCUS_GAIN)silence(session);
   };
   boolean focused=false, completed=false;
   String playbackError=null;
   try{
    @SuppressWarnings("deprecation")
    int focusResult=audio.requestAudioFocus(focus,AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
    focused=focusResult==AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    if(!focused)throw new IllegalStateException("No se obtuvo el foco de audio.");
    if(closed||active!=session||!session.running.get())throw new IllegalStateException("Reproducción cancelada.");
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
    if(!track.setPreferredDevice(selected))throw new IllegalStateException("Android rechazó la salida multimedia ZNP.");
    track.addOnRoutingChangedListener(routing,main);
    track.setVolume(0f);track.play();
    byte[] silence=new byte[(rate/50)*channels*2];
    long frames=0,deadline=SystemClock.elapsedRealtime()+5000;boolean routed=false;
    while(session.running.get()&&SystemClock.elapsedRealtime()<deadline){
     int n=track.write(silence,0,silence.length,AudioTrack.WRITE_NON_BLOCKING);
     if(n<0)throw new IllegalStateException("Error preparando audio: "+n);
     frames+=n/(channels*2);
     AudioDeviceInfo actual=track.getRoutedDevice();
     if(isSelectedRoute(actual,selected.getId())){routed=true;break;}
     SystemClock.sleep(15);
    }
    if(!routed||!session.running.get())throw new IllegalStateException("No se verificó la ruta real al Z02.");
    session.routeVerified=true;
    track.setVolume(1f);emitState(session,"Reproduciendo por Z02");
    int offset=0;long writeDeadline=SystemClock.elapsedRealtime()+pcm.length*1000L/(rate*channels*2)+5000;
    long lastProgress=SystemClock.elapsedRealtime();
    while(session.running.get()&&offset<pcm.length){
     long now=SystemClock.elapsedRealtime();
     if(now>=writeDeadline||now-lastProgress>=2000)throw new IllegalStateException("La salida de audio dejó de avanzar.");
     AudioDeviceInfo actual=track.getRoutedDevice();
     if(!isSelectedRoute(actual,selected.getId()))
      throw new IllegalStateException("La ruta cambió durante la respuesta.");
     int n=track.write(pcm,offset,Math.min(1280,pcm.length-offset),AudioTrack.WRITE_NON_BLOCKING);
     if(n<0)throw new IllegalStateException("Falló la escritura de audio: "+n);
     offset+=n;frames+=n/(channels*2);if(n>0)lastProgress=SystemClock.elapsedRealtime();else SystemClock.sleep(10);
    }
    long drain=SystemClock.elapsedRealtime()+5000;
    while(session.running.get()&&(track.getPlaybackHeadPosition()&0xffffffffL)<frames&&SystemClock.elapsedRealtime()<drain){
     if(!isSelectedRoute(track.getRoutedDevice(),selected.getId()))
      throw new IllegalStateException("La ruta cambió antes de completar la respuesta.");
     SystemClock.sleep(10);
    }
    if(!session.running.get()||!isSelectedRoute(track.getRoutedDevice(),selected.getId())||(track.getPlaybackHeadPosition()&0xffffffffL)<frames)
     throw new IllegalStateException("Audio interrumpido antes de completar.");
    completed=true;
   }catch(Exception error){playbackError=error.getMessage()==null?"Falló la reproducción por Z02.":error.getMessage();}
   finally{
    session.running.set(false);
    if(track!=null){try{track.setVolume(0f);}catch(Exception ignored){}try{track.removeOnRoutingChangedListener(routing);}catch(Exception ignored){}try{track.pause();}catch(Exception ignored){}try{track.flush();}catch(Exception ignored){}try{track.release();}catch(Exception ignored){}}
    session.track=null;
    if(focused){@SuppressWarnings("deprecation") boolean ignored=audio.abandonAudioFocus(focus)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED;}
   }
   // Only advance the queue after both the track and its audio focus are released.
   final boolean finished=completed;final String problem=playbackError;
   main.post(()->{if(active!=session)return;active=null;if(listener!=null){if(finished)listener.onCompleted();else listener.onError(problem==null?"Audio interrumpido.":problem);}});
  });}catch(RejectedExecutionException ignored){
   if(!closed)fail(session,"La salida de audio no está disponible.");
  }
 }
 private void fail(Playback session,String message){
  session.running.set(false);
  main.post(()->{if(active==session){active=null;if(listener!=null)listener.onError(message);}});
 }
 private void emitError(String message){final long request=sequence;main.post(()->{if(sequence==request&&listener!=null)listener.onError(message);});}
 private void emitState(Playback session,String state){main.post(()->{if(active==session&&listener!=null)listener.onState(state);});}
 private static boolean isSelectedRoute(AudioDeviceInfo device,int targetId){
  return device!=null&&device.getType()==AudioDeviceInfo.TYPE_BLUETOOTH_A2DP&&device.getId()==targetId;
 }
 private static void silence(Playback session){
  session.running.set(false);
  if(session.track!=null)try{session.track.setVolume(0f);}catch(Exception ignored){}
 }
 public void stop(){
  sequence++;
  if(pendingStart!=null)main.removeCallbacks(pendingStart);
  pendingStart=null;
  Playback old=active;active=null;
  if(old!=null)silence(old);
 }
 public void close(){closed=true;stop();playbackExecutor.shutdownNow();if(audio!=null)try{audio.unregisterAudioDeviceCallback(deviceCallback);}catch(Exception ignored){}main.removeCallbacksAndMessages(null);listener=null;}
}
