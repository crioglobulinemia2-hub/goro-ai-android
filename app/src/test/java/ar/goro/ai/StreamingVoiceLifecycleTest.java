package ar.goro.ai;
import android.widget.TextView;
import java.lang.reflect.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class StreamingVoiceLifecycleTest {
 private static Field field(String name)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f;}
 private static void done(MainActivity a)throws Exception{Method m=MainActivity.class.getDeclaredMethod("onSpeechPlaybackCompleted");m.setAccessible(true);m.invoke(a);}
 @Test public void microphoneStaysPausedBetweenSentencesUntilModelFinishes()throws Exception{
  MainActivity a=Robolectric.buildActivity(MainActivity.class).get();
  field("foreground").set(a,true);field("state").set(a,new TextView(a));
  field("speechInProgress").set(a,true);field("speechChunkActive").set(a,true);field("modelReplyStreaming").set(a,true);
  done(a);
  assertTrue((Boolean)field("speechInProgress").get(a));assertFalse((Boolean)field("speechChunkActive").get(a));
  field("modelReplyStreaming").set(a,false);done(a);
  assertFalse((Boolean)field("speechInProgress").get(a));
 }
 @Test public void cancelledStreamingChangesGenerationBeforeLateCallbacksCanSpeak()throws Exception{
  MainActivity a=Robolectric.buildActivity(MainActivity.class).get();
  field("modelReplyStreaming").set(a,true);field("commandGeneration").set(a,4L);
  Method m=MainActivity.class.getDeclaredMethod("cancelStreamedReply");m.setAccessible(true);m.invoke(a);
  assertFalse((Boolean)field("modelReplyStreaming").get(a));assertEquals(5L,field("commandGeneration").get(a));
 }
}
