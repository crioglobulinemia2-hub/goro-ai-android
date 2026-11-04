package ar.goro.ai.voice;
import android.os.Looper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.LooperMode;
import static org.robolectric.Shadows.shadowOf;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class Z02PlayerCallbackTest {
 private static final class Listener implements Z02Player.Listener {
  int errors,completed;
  public void onState(String state){}
  public void onError(String error){errors++;}
  public void onCompleted(){completed++;}
 }
 @Test public void stoppedPlaybackDropsAnAlreadyQueuedValidationError(){
  Listener listener=new Listener();Z02Player player=new Z02Player(RuntimeEnvironment.getApplication(),listener);
  try{
   player.playWav(new byte[0]);player.stop();shadowOf(Looper.getMainLooper()).idle();
   assertEquals(0,listener.errors);assertEquals(0,listener.completed);
  }finally{player.close();}
 }
 @Test public void replacementReportsOnlyItsOwnError(){
  Listener listener=new Listener();Z02Player player=new Z02Player(RuntimeEnvironment.getApplication(),listener);
  try{
   player.playWav(new byte[0]);player.playWav(new byte[0]);shadowOf(Looper.getMainLooper()).idle();
   assertEquals(1,listener.errors);assertEquals(0,listener.completed);
  }finally{player.close();}
 }
}
