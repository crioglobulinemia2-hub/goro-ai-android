package ar.goro.ai;

import ar.goro.ai.core.NvidiaSpeechProbe;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class Z02SpeechReconnectTest {
 private MainActivity activity;
 private NvidiaSpeechProbe probe;
 private int started;
 @Before public void setup()throws Exception{
  activity=Robolectric.buildActivity(MainActivity.class).get();
  Class<?> factory=Class.forName("ar.goro.ai.core.NvidiaSpeechProbe$Factory");
  Class<?> scheduler=Class.forName("ar.goro.ai.core.NvidiaSpeechProbe$Scheduler");
  Class<?> session=Class.forName("ar.goro.ai.core.NvidiaSpeechProbe$Session");
  Object fakeSession=Proxy.newProxyInstance(session.getClassLoader(),new Class[]{session},(object,method,args)->null);
  Object fakeFactory=Proxy.newProxyInstance(factory.getClassLoader(),new Class[]{factory},(object,method,args)->{started++;return fakeSession;});
  Object fakeScheduler=Proxy.newProxyInstance(scheduler.getClassLoader(),new Class[]{scheduler},(object,method,args)->null);
  Constructor<?> constructor=NvidiaSpeechProbe.class.getDeclaredConstructor(factory,scheduler);constructor.setAccessible(true);
  probe=(NvidiaSpeechProbe)constructor.newInstance(fakeFactory,fakeScheduler);
  ReflectionHelpers.setField(activity,"speechProbe",probe);
  ReflectionHelpers.setField(activity,"foreground",true);
  ReflectionHelpers.setField(activity,"nvidiaChatReady",true);
  ReflectionHelpers.setField(activity,"magpieDiegoReady",true);
  // The transport itself is not started in this test; no Bluetooth/network calls.
  ReflectionHelpers.setField(activity,"zn02ReadyForVoice",false);
 }
 @After public void cleanup(){activity.onDestroy();}
 private void reconnect()throws Exception{
  Method method=MainActivity.class.getDeclaredMethod("resumeVoiceAfterZ02Connected");method.setAccessible(true);method.invoke(activity);
 }
 @Test public void reconnectionRetriesACachedAsrFailureWhenChatAndDiegoAreAlreadyReady()throws Exception{
  ReflectionHelpers.setField(probe,"attempted",true);
  ReflectionHelpers.setField(probe,"status","Parakeet no completó la comprobación");
  reconnect();
  assertEquals(1,started);assertTrue(probe.isRunning());assertTrue(probe.wasAttempted());
 }
 @Test public void repeatedConnectionNoticeDoesNotRestartAnInFlightProbe()throws Exception{
  probe.start((ready,message)->{});
  reconnect();reconnect();
  assertEquals(1,started);assertTrue(probe.isRunning());
 }
 @Test public void alreadyVerifiedAsrDoesNotConsumeAnotherDiagnosticRequest()throws Exception{
  ReflectionHelpers.setField(activity,"nvidiaAsrReady",true);
  ReflectionHelpers.setField(probe,"attempted",true);ReflectionHelpers.setField(probe,"ready",true);
  reconnect();
  assertEquals(0,started);assertTrue(probe.isReady());assertFalse(probe.isRunning());
 }
}
