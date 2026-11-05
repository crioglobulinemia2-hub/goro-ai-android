package ar.goro.ai;
import android.os.Looper;
import android.widget.TextView;
import ar.goro.ai.core.ParakeetAsrGateway;
import ar.goro.ai.voice.PcmSpeechSignalTest;
import java.lang.reflect.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,shadows=ListeningPauseTest.HeldAsr.class,instrumentedPackages="ar.goro.ai.core")
@LooperMode(LooperMode.Mode.PAUSED)
public class ListeningPauseTest {
 @Implements(value=ParakeetAsrGateway.class,isInAndroidSdk=false)
 public static class HeldAsr {
  static int calls,cancels;static ParakeetAsrGateway.Callback pending;
  @Implementation protected void __constructor__(String key){}
  @Implementation protected void transcribe(byte[] pcm,int rate,ParakeetAsrGateway.Callback callback){calls++;pending=callback;}
  @Implementation protected void cancelPending(){cancels++;}
  @Implementation protected void close(){}
 }
 private MainActivity a;
 private Field field(String name)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f;}
 private Object get(String n)throws Exception{return field(n).get(a);}
 private void set(String n,Object v)throws Exception{field(n).set(a,v);}
 private void call(String name)throws Exception{Method m=MainActivity.class.getDeclaredMethod(name);m.setAccessible(true);m.invoke(a);}
 private void audio(byte[] pcm)throws Exception{Method m=MainActivity.class.getDeclaredMethod("handleZ02Pcm",byte[].class);m.setAccessible(true);m.invoke(a,(Object)pcm);}
 @Before public void setup()throws Exception{
  a=Robolectric.buildActivity(MainActivity.class).get();set("foreground",true);set("voiceActive",true);
  set("output",new TextView(a));set("state",new TextView(a));
  set("zn02Controller",new GoroZn02Controller(a,s->{}));set("asrGateway",new ParakeetAsrGateway("nvapi-test"));
  HeldAsr.calls=0;HeldAsr.cancels=0;HeldAsr.pending=null;
  a.getSharedPreferences("goro_voice_control",0).edit().clear().commit();
 }
 @After public void cleanup(){a.onDestroy();}
 @Test public void pauseCancelsWorkPersistsAndPreventsReconnectFromListening()throws Exception{
  call("scheduleZ02ListenRetry");assertNotNull(get("pendingVoiceRetry"));
  call("toggleListening");assertTrue((Boolean)get("listeningPaused"));assertFalse((Boolean)get("voiceActive"));
  assertTrue(a.getSharedPreferences("goro_voice_control",0).getBoolean("paused",false));assertNull(get("pendingVoiceRetry"));assertTrue(HeldAsr.cancels>0);
  set("zn02ReadyForVoice",true);set("nvidiaChatReady",true);set("magpieDiegoReady",true);set("nvidiaAsrReady",true);
  call("maybeStartHandsFreeConversation");assertFalse((Boolean)get("voiceActive"));
 }
 @Test public void silenceNeverCallsCloudAndRecordingCannotRestartDuringRecognition()throws Exception{
  audio(new byte[32000]);assertEquals(0,HeldAsr.calls);
  audio(PcmSpeechSignalTest.tone(700,1000,0));assertEquals(1,HeldAsr.calls);assertTrue((Boolean)get("recognitionInProgress"));
  call("scheduleZ02ListenRetry");assertNull(get("pendingVoiceRetry"));call("startZ02Listening");assertTrue((Boolean)get("voiceActive"));
 }
 @Test public void pauseInvalidatesRecognitionAlreadyWaitingToReturn()throws Exception{
  audio(PcmSpeechSignalTest.tone(700,1000,0));ParakeetAsrGateway.Callback old=HeldAsr.pending;
  call("toggleListening");TextView output=(TextView)get("output");output.setText("Pausado");
  old.completed("consulta vieja",null);assertEquals("Pausado",output.getText().toString());assertFalse((Boolean)get("recognitionInProgress"));
 }
 @Test public void twoRecognitionFailuresPauseInsteadOfRepeatingForever()throws Exception{
  byte[] pcm=PcmSpeechSignalTest.tone(700,1000,0);
  audio(pcm);HeldAsr.pending.completed(null,"No words");assertFalse((Boolean)get("listeningPaused"));
  audio(pcm);HeldAsr.pending.completed(null,"No words");assertTrue((Boolean)get("listeningPaused"));assertFalse((Boolean)get("voiceActive"));assertEquals(2,HeldAsr.calls);
 }
}
