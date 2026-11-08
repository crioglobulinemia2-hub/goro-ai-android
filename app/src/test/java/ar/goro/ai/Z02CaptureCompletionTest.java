package ar.goro.ai;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class Z02CaptureCompletionTest {
 private static final class Listener implements GoroZn02Controller.Listener {
  int idle,pcm;byte[] received;
  public void onStatus(String message){}
  public void onMicrophoneIdle(){idle++;}
  public void onMicrophonePcm(byte[] bytes){pcm++;received=bytes;}
 }
 private void complete(GoroZn02Controller controller,byte[] bytes)throws Exception{
  Field draining=GoroZn02Controller.class.getDeclaredField("draining");draining.setAccessible(true);draining.setBoolean(controller,true);
  Field buffer=GoroZn02Controller.class.getDeclaredField("microphonePcm");buffer.setAccessible(true);
  ((ByteArrayOutputStream)buffer.get(controller)).write(bytes);
  Method finish=GoroZn02Controller.class.getDeclaredMethod("finishVoice");finish.setAccessible(true);finish.invoke(controller);finish.invoke(controller);
 }
 @Test public void silentCaptureNotifiesSessionOnceSoListeningCanResume()throws Exception{
  Listener listener=new Listener();GoroZn02Controller controller=new GoroZn02Controller(RuntimeEnvironment.getApplication(),listener);
  try{complete(controller,new byte[0]);assertEquals(1,listener.idle);assertEquals(0,listener.pcm);}
  finally{controller.close();}
 }
 @Test public void capturedAudioDoesNotAlsoTriggerIdleRestart()throws Exception{
  Listener listener=new Listener();GoroZn02Controller controller=new GoroZn02Controller(RuntimeEnvironment.getApplication(),listener);
  byte[] pcm=new byte[]{1,0,2,0};
  try{complete(controller,pcm);assertEquals(0,listener.idle);assertEquals(1,listener.pcm);assertArrayEquals(pcm,listener.received);}
  finally{controller.close();}
 }
}
