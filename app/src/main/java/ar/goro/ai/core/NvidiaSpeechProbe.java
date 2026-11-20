package ar.goro.ai.core;

import android.os.Handler;
import android.os.Looper;
import java.text.Normalizer;
import java.util.Locale;

/**
 * A bounded cloud speech check with cached results. Interrupted attempts may
 * restart when the lifecycle permits. It only sends a fixed synthetic phrase,
 * never reads the microphone, plays sound or persists audio.
 */
public final class NvidiaSpeechProbe {
 public static final String PHRASE="Esta es una prueba de voz en español.";
 public static final long TIMEOUT_MS=90000L;
 public interface Callback {void completed(boolean ready,String message);}
 interface AudioCallback {void completed(byte[] wav,String error);}
 interface TextCallback {void completed(String text,String error);}
 interface Session {
  void synthesize(String text,AudioCallback callback);
  void prepare(byte[] wav,AudioCallback callback);
  void transcribe(byte[] pcm16Mono,TextCallback callback);
  void close();
 }
 interface Factory {Session create();}
 interface Scheduler {void postDelayed(Runnable task,long delayMs);void remove(Runnable task);}
 private final Factory factory;
 private final Scheduler scheduler;
 private Session active;
 private Callback callback;
 private Runnable timeout;
 private long generation;
 private boolean attempted,running,ready,closed;
 private String status="Parakeet todavía no se comprobó.";
 public NvidiaSpeechProbe(String apiKey){
  this(()->new Session(){
   final NemotronSpeechGateway tts=new NemotronSpeechGateway(apiKey);
   final ParakeetAsrGateway asr=new ParakeetAsrGateway(apiKey);
   final Handler main=new Handler(Looper.getMainLooper());
   Thread conversion;
   public void synthesize(String text,AudioCallback callback){tts.synthesize(text,callback::completed);}
   public void prepare(byte[] wav,AudioCallback callback){
    conversion=new Thread(()->{
     try{byte[] pcm=WavToPcm16k.convert(wav);main.post(()->callback.completed(pcm,null));}
     catch(Exception error){main.post(()->callback.completed(null,error.getMessage()));}
    },"GORO-Speech-Diagnostic");
    conversion.start();
   }
   public void transcribe(byte[] pcm,TextCallback callback){asr.transcribe(pcm,16000,callback::completed);}
   public void close(){tts.close();asr.close();if(conversion!=null)conversion.interrupt();main.removeCallbacksAndMessages(null);}
  },new Scheduler(){
   final Handler handler=new Handler(Looper.getMainLooper());
   public void postDelayed(Runnable task,long delayMs){handler.postDelayed(task,delayMs);}
   public void remove(Runnable task){handler.removeCallbacks(task);}
  });
 }
 NvidiaSpeechProbe(Factory factory,Scheduler scheduler){this.factory=factory;this.scheduler=scheduler;}
 public boolean isReady(){return ready;}
 public boolean wasAttempted(){return attempted;}
 public boolean isRunning(){return running;}
 public String status(){return status;}
 public void start(Callback result){
  if(result==null)throw new IllegalArgumentException("callback required");
  if(closed){result.completed(false,"La comprobación NVIDIA está cerrada.");return;}
  if(running){callback=result;return;}
  if(attempted){result.completed(ready,status);return;}
  attempted=true;running=true;ready=false;callback=result;
  status="Comprobando Diego y Parakeet con una frase de diagnóstico…";
  final long ticket=++generation;
  timeout=()->finish(ticket,false,"La comprobación de voz agotó el tiempo. Podés volver a probar NVIDIA.");
  scheduler.postDelayed(timeout,TIMEOUT_MS);
  try{
   active=factory.create();
   active.synthesize(PHRASE,(wav,error)->{
    if(!current(ticket))return;
    if(error!=null||wav==null){finish(ticket,false,"Diego no completó la comprobación: "+safe(error));return;}
    try{
     active.prepare(wav,(pcm,preparationError)->{
      if(!current(ticket))return;
      if(preparationError!=null||pcm==null){finish(ticket,false,"No se pudo preparar la comprobación de voz: "+safe(preparationError));return;}
      try{active.transcribe(pcm,(text,problem)->{
      if(!current(ticket))return;
      if(problem!=null){finish(ticket,false,"Parakeet no completó la comprobación: "+safe(problem));return;}
      boolean recognized=normalize(PHRASE).equals(normalize(text));
      finish(ticket,recognized,recognized
        ?"Diego y Parakeet verificados con una frase de diagnóstico. El micrófono Z02 aún requiere conexión."
        :"Parakeet respondió, pero no reconoció la frase de comprobación. Podés volver a probar NVIDIA.");
     });
      }catch(Exception e){finish(ticket,false,"No se pudo iniciar Parakeet: "+safe(e.getMessage()));}
     });
    }catch(Exception e){finish(ticket,false,"No se pudo preparar la comprobación de voz: "+safe(e.getMessage()));}
   });
  }catch(Exception e){finish(ticket,false,"No se pudo iniciar la comprobación de voz: "+safe(e.getMessage()));}
 }
 private boolean current(long ticket){return !closed&&running&&ticket==generation;}
 private void finish(long ticket,boolean success,String message){
  if(!current(ticket))return;
  running=false;ready=success;status=message;
  Callback result=callback;callback=null;
  release();
  if(result!=null)result.completed(success,message);
 }
 private void release(){
  if(timeout!=null)scheduler.remove(timeout);timeout=null;
  Session session=active;active=null;
  if(session!=null)session.close();
 }
 /** Interrupts pending work without caching a service failure. The caller controls restart. */
 public void cancel(){
  if(!running)return;
  generation++;running=false;ready=false;attempted=false;callback=null;
  status="Comprobación de voz interrumpida.";
  release();
 }
 public void reset(){
  if(closed)return;
  cancel();generation++;attempted=false;ready=false;
  status="Parakeet todavía no se comprobó.";
 }
 public void close(){cancel();closed=true;ready=false;generation++;callback=null;release();}
 static String normalize(String text){
  if(text==null)return "";
  return Normalizer.normalize(text,Normalizer.Form.NFD)
    .replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT)
    .replaceAll("[^a-z0-9 ]"," ").trim().replaceAll("\\s+"," ");
 }
 private static String safe(String error){return error==null?"respuesta vacía":error;}
}
