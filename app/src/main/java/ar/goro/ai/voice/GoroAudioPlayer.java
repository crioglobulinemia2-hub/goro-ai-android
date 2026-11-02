package ar.goro.ai.voice;

import android.content.Context;
import android.media.MediaPlayer;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/** Plays NVIDIA Magpie WAV responses and supports immediate barge-in interruption. */
public final class GoroAudioPlayer {
 public interface Listener {void onStarted();void onCompleted();void onError(String error);}
 private final Context context;
 private MediaPlayer player;
 private File currentFile;
 private Listener listener;
 public GoroAudioPlayer(Context context){this.context=context.getApplicationContext();}
 public synchronized void play(byte[] wav,Listener listener){
  stop();
  if(wav==null||wav.length<44){if(listener!=null)listener.onError("Audio vacío");return;}
  this.listener=listener;
  try{
   currentFile=File.createTempFile("goro-voice-",".wav",context.getCacheDir());
   try(FileOutputStream out=new FileOutputStream(currentFile)){out.write(wav);out.flush();}
   player=new MediaPlayer();
   player.setOnPreparedListener(mp->{synchronized(GoroAudioPlayer.this){if(player!=mp)return;mp.start();if(GoroAudioPlayer.this.listener!=null)GoroAudioPlayer.this.listener.onStarted();}});
   player.setOnCompletionListener(mp->{Listener done; synchronized(GoroAudioPlayer.this){if(player!=mp)return;done=GoroAudioPlayer.this.listener;releaseLocked();}if(done!=null)done.onCompleted();});
   player.setOnErrorListener((mp,what,extra)->{Listener failed;synchronized(GoroAudioPlayer.this){if(player!=mp)return true;failed=GoroAudioPlayer.this.listener;releaseLocked();}if(failed!=null)failed.onError("Error al reproducir audio ("+what+"/"+extra+")");return true;});
   player.setDataSource(currentFile.getAbsolutePath());
   player.prepareAsync();
  }catch(Exception e){Listener failed=this.listener;releaseLocked();if(failed!=null)failed.onError("No se pudo preparar la voz: "+e.getClass().getSimpleName());}
 }
 public synchronized void stop(){releaseLocked();}
 public synchronized boolean isPlaying(){return player!=null&&player.isPlaying();}
 private void releaseLocked(){
  if(player!=null){try{player.setOnPreparedListener(null);player.setOnCompletionListener(null);player.setOnErrorListener(null);player.stop();}catch(Exception ignored){}try{player.release();}catch(Exception ignored){}player=null;}
  if(currentFile!=null){try{currentFile.delete();}catch(Exception ignored){}currentFile=null;}
  listener=null;
 }
}
