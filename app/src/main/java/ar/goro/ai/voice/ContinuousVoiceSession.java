package ar.goro.ai.voice;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Foreground-activity voice session. One explicit session start, continuous turns,
 * interruption of TTS when the user begins speaking. No per-utterance start/stop buttons.
 * For background listening Android requires a separately permissioned microphone FGS.
 */
public final class ContinuousVoiceSession implements RecognitionListener {
 public interface Listener {
  void onState(ConversationState state);
  void onFinalText(String text);
  void onPartialText(String text);
  void onError(String safeMessage);
  void onUserSpeechStarted();
 }
 private final Context context;
 private final Listener listener;
 private final Handler handler=new Handler(Looper.getMainLooper());
 private final ConversationTurnMachine machine=new ConversationTurnMachine();
 private SpeechRecognizer recognizer;
 private boolean active=false;
 private boolean destroyed=false;
 private int retry=0;
 public ContinuousVoiceSession(Context context,Listener listener){this.context=context.getApplicationContext();this.listener=listener;}
 public boolean available(){return SpeechRecognizer.isRecognitionAvailable(context);}
 public void start(){
  if(destroyed||active||!available())return;
  active=true;retry=0;
  if(recognizer==null){recognizer=SpeechRecognizer.createSpeechRecognizer(context);recognizer.setRecognitionListener(this);}
  begin();
 }
 private void begin(){
  if(!active||destroyed||recognizer==null)return;
  Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
  i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
  i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"es-AR");
  i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true);
  i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1);
  i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE,context.getPackageName());
  try{machine.listening();listener.onState(machine.state());recognizer.startListening(i);}
  catch(RuntimeException e){scheduleRetry();}
 }
 public void pauseForResponse(){\n  if(!active)return;\n  handler.removeCallbacksAndMessages(null);\n  if(recognizer!=null){try{recognizer.cancel();}catch(RuntimeException ignored){}}\n  machine.processing();listener.onState(machine.state());\n }\n public void resumeAfterResponse(){if(active&&!destroyed)scheduleRetry();}\n public void stop(){
  active=false;handler.removeCallbacksAndMessages(null);
  if(recognizer!=null){try{recognizer.cancel();}catch(RuntimeException ignored){}}
  machine.idle();listener.onState(machine.state());
 }
 public void destroy(){stop();destroyed=true;if(recognizer!=null){recognizer.destroy();recognizer=null;}}
 public void interruptForUser(){
  if(!active)return;
  machine.interrupt();listener.onState(machine.state());listener.onUserSpeechStarted();
 }
 private void scheduleRetry(){
  if(!active||destroyed)return;
  long delay=Math.min(5000,350L*(1L<<Math.min(retry++,4)));
  handler.postDelayed(this::begin,delay);
 }
 @Override public void onReadyForSpeech(Bundle params){}
 @Override public void onBeginningOfSpeech(){interruptForUser();}
 @Override public void onRmsChanged(float rmsdB){}
 @Override public void onBufferReceived(byte[] buffer){}
 @Override public void onEndOfSpeech(){if(active){machine.processing();listener.onState(machine.state());}}
 @Override public void onError(int error){if(active)scheduleRetry();}
 @Override public void onResults(Bundle results){
  if(!active)return;
  ArrayList<String> values=results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
  if(values!=null&&!values.isEmpty())listener.onFinalText(values.get(0));
  scheduleRetry();
 }
 @Override public void onPartialResults(Bundle partial){
  ArrayList<String> values=partial.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
  if(values!=null&&!values.isEmpty())listener.onPartialText(values.get(0));
 }
 @Override public void onEvent(int eventType,Bundle params){}
}
