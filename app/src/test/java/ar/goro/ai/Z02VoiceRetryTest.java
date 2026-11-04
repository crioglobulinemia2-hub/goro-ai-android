package ar.goro.ai;
import android.os.Looper;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.LooperMode;
import static org.robolectric.Shadows.shadowOf;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class Z02VoiceRetryTest {
 private MainActivity activity;
 @Before public void setup()throws Exception{
  // Leave onCreate uncalled: these lifecycle tests must not open real services or a renderer.
  activity=Robolectric.buildActivity(MainActivity.class).get();
  set("zn02Controller",new GoroZn02Controller(RuntimeEnvironment.getApplication(),status->{}));
  set("foreground",true);set("voiceActive",true);
 }
 @After public void cleanup(){activity.onDestroy();}
 private Object get(String name)throws Exception{
  Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);return field.get(activity);
 }
 private void set(String name,Object value)throws Exception{
  Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);field.set(activity,value);
 }
 private void call(String name)throws Exception{
  Method method=MainActivity.class.getDeclaredMethod(name);method.setAccessible(true);method.invoke(activity);
 }
 private void advance(){shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600));}
 @Test public void repeatedIdleEventsScheduleOnlyOneRetryAndSpeechBlocksIt()throws Exception{
  call("scheduleZ02ListenRetry");Object retry=get("pendingVoiceRetry");assertNotNull(retry);
  call("scheduleZ02ListenRetry");assertSame(retry,get("pendingVoiceRetry"));
  set("speechInProgress",true);advance();
  assertTrue((Boolean)get("voiceActive"));assertNull(get("pendingVoiceRetry"));
 }
 @Test public void pauseCannotReopenTheMicrophoneInANewSession()throws Exception{
  call("scheduleZ02ListenRetry");activity.onStop();
  assertNull(get("pendingVoiceRetry"));
  set("foreground",true);set("voiceActive",true);advance();
  // The disconnected controller would end this new session if the stale retry ran.
  assertTrue((Boolean)get("voiceActive"));
 }
 @Test public void disconnectInvalidatesSpeechAndQueuedIdleRecovery()throws Exception{
  call("scheduleZ02ListenRetry");long before=(Long)get("speechGeneration");
  set("speechInProgress",true);call("invalidateVoiceConversation");
  assertFalse((Boolean)get("voiceActive"));assertFalse((Boolean)get("speechInProgress"));
  assertTrue((Long)get("speechGeneration")>before);assertNull(get("pendingVoiceRetry"));
  set("voiceActive",true);advance();assertTrue((Boolean)get("voiceActive"));
 }
 @Test public void speechNeverOpensMicrophoneAndFailedOpenEndsSession()throws Exception{
  set("speechInProgress",true);call("startZ02Listening");assertTrue((Boolean)get("voiceActive"));
  set("speechInProgress",false);call("startZ02Listening");assertFalse((Boolean)get("voiceActive"));
 }
 @Test public void pendingRetryCannotEndAnAlreadyRunningOrDrainingCapture()throws Exception{
  GoroZn02Controller controller=(GoroZn02Controller)get("zn02Controller");
  Field recording=GoroZn02Controller.class.getDeclaredField("recording");recording.setAccessible(true);
  Field draining=GoroZn02Controller.class.getDeclaredField("draining");draining.setAccessible(true);
  recording.setBoolean(controller,true);call("scheduleZ02ListenRetry");advance();
  assertTrue((Boolean)get("voiceActive"));assertTrue(recording.getBoolean(controller));
  recording.setBoolean(controller,false);draining.setBoolean(controller,true);
  call("scheduleZ02ListenRetry");advance();
  assertTrue((Boolean)get("voiceActive"));assertTrue(draining.getBoolean(controller));
 }
}
