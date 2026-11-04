package ar.goro.ai.core;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;
public class NvidiaSpeechProbeTest {
 private static final class Timer implements NvidiaSpeechProbe.Scheduler {
  Runnable task;
  public void postDelayed(Runnable task,long delay){assertEquals(90000,delay);this.task=task;}
  public void remove(Runnable task){if(this.task==task)this.task=null;}
  void fire(){Runnable current=task;if(current!=null)current.run();}
 }
 private static final class Session implements NvidiaSpeechProbe.Session {
  int syntheses,recognitions,preparations;boolean closed,deferPreparation;String phrase;byte[] pcm;
  NvidiaSpeechProbe.AudioCallback prepared;
  NvidiaSpeechProbe.AudioCallback audio;NvidiaSpeechProbe.TextCallback text;
  public void synthesize(String phrase,NvidiaSpeechProbe.AudioCallback callback){syntheses++;this.phrase=phrase;audio=callback;}
  public void prepare(byte[] wav,NvidiaSpeechProbe.AudioCallback callback){
   preparations++;prepared=callback;if(deferPreparation)return;
   try{callback.completed(WavToPcm16k.convert(wav),null);}
   catch(Exception error){callback.completed(null,error.getMessage());}
  }
  public void transcribe(byte[] pcm,NvidiaSpeechProbe.TextCallback callback){recognitions++;this.pcm=pcm;text=callback;}
  public void close(){closed=true;}
 }
 private static final class Result implements NvidiaSpeechProbe.Callback {
  int count;boolean ready;String message;
  public void completed(boolean ready,String message){count++;this.ready=ready;this.message=message;}
 }
 private static final class Harness {
  final Timer timer=new Timer();final List<Session> sessions=new ArrayList<>();
  final NvidiaSpeechProbe probe=new NvidiaSpeechProbe(()->{Session s=new Session();sessions.add(s);return s;},timer);
  Session session(){return sessions.get(sessions.size()-1);}
 }
 @Test public void onlySuccessfulRecognitionMarksReadyAndNeverRepeatsAutomatically(){
  Harness h=new Harness();Result r=new Result();h.probe.start(r);
  assertFalse(h.probe.isReady());assertEquals(NvidiaSpeechProbe.PHRASE,h.session().phrase);
  h.session().audio.completed(DiagnosticWavFixture.shortSpeech(),null);
  assertEquals(6400,h.session().pcm.length);assertFalse(h.probe.isReady());assertEquals(0,r.count);
  h.session().text.completed("ESTA ES UNA PRUEBA DE VOZ EN ESPAÑOL!",null);
  assertTrue(r.ready);assertTrue(h.probe.isReady());assertTrue(h.session().closed);assertNull(h.timer.task);
  h.probe.start(new Result());assertEquals(1,h.sessions.size());assertEquals(1,h.session().syntheses);
 }
 @Test public void unexpectedTranscriptFailsUntilExplicitRetry(){
  Harness h=new Harness();Result r=new Result();h.probe.start(r);
  h.session().audio.completed(DiagnosticWavFixture.shortSpeech(),null);
  h.session().text.completed("otro contenido",null);assertFalse(r.ready);
  h.probe.start(new Result());assertEquals(1,h.sessions.size());
  h.probe.reset();h.probe.start(new Result());assertEquals(2,h.sessions.size());
 }
 @Test public void cancelRejectsLateSynthesisAndDoesNotEnableAudio(){
  Harness h=new Harness();Result r=new Result();h.probe.start(r);Session old=h.session();
  h.probe.cancel();old.audio.completed(DiagnosticWavFixture.shortSpeech(),null);
  assertTrue(old.closed);assertEquals(0,old.recognitions);assertEquals(0,r.count);assertFalse(h.probe.isReady());
 }
 @Test public void timeoutRejectsLateRecognitionAndClosesNetwork(){
  Harness h=new Harness();Result r=new Result();h.probe.start(r);
  h.session().audio.completed(DiagnosticWavFixture.shortSpeech(),null);
  h.timer.fire();h.session().text.completed(NvidiaSpeechProbe.PHRASE,null);
  assertEquals(1,r.count);assertFalse(r.ready);assertFalse(h.probe.isReady());assertTrue(h.session().closed);
 }
 @Test public void replacingCredentialInstanceCannotPublishAnOldSuccess(){
  Harness old=new Harness();Result r=new Result();old.probe.start(r);
  old.session().audio.completed(DiagnosticWavFixture.shortSpeech(),null);old.probe.close();
  Harness replacement=new Harness();replacement.probe.start(new Result());
  old.session().text.completed(NvidiaSpeechProbe.PHRASE,null);
  assertEquals(0,r.count);assertFalse(old.probe.isReady());assertFalse(replacement.probe.isReady());
 }
 @Test public void duplicateStartSharesTheCurrentAttempt(){
  Harness h=new Harness();Result first=new Result(),last=new Result();h.probe.start(first);h.probe.start(last);
  assertEquals(1,h.sessions.size());assertEquals(1,h.session().syntheses);
  h.session().audio.completed(DiagnosticWavFixture.shortSpeech(),null);
  h.session().text.completed(NvidiaSpeechProbe.PHRASE,null);
  assertEquals(0,first.count);assertEquals(1,last.count);assertTrue(last.ready);
 }
 @Test public void cancellationDuringConversionDropsItsAlreadyQueuedResult(){
  Harness h=new Harness();Result r=new Result();h.probe.start(r);Session old=h.session();
  old.deferPreparation=true;old.audio.completed(DiagnosticWavFixture.shortSpeech(),null);
  h.probe.cancel();old.prepared.completed(new byte[6400],null);
  assertTrue(old.closed);assertEquals(0,old.recognitions);assertEquals(0,r.count);assertFalse(h.probe.isReady());
 }
 @Test public void recognitionErrorCannotMarkReadyAndIsNotAutomaticallyRetried(){
  Harness h=new Harness();Result r=new Result();h.probe.start(r);
  h.session().audio.completed(DiagnosticWavFixture.shortSpeech(),null);
  h.session().text.completed(null,"UNAUTHENTICATED");
  h.session().text.completed(NvidiaSpeechProbe.PHRASE,null);
  assertEquals(1,r.count);assertFalse(r.ready);assertTrue(h.session().closed);
  h.probe.start(new Result());assertEquals(1,h.sessions.size());
 }
 @Test public void malformedSynthesisNeverCallsRecognizer(){
  Harness h=new Harness();Result r=new Result();h.probe.start(r);
  h.session().audio.completed(new byte[44],null);
  assertEquals(1,r.count);assertFalse(r.ready);assertEquals(0,h.session().recognitions);assertTrue(h.session().closed);
 }
}
