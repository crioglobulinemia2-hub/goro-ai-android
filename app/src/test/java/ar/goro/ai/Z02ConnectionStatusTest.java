package ar.goro.ai;
import android.os.Looper;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.LooperMode;
import static org.robolectric.Shadows.shadowOf;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class Z02ConnectionStatusTest {
 private static final class Listener implements GoroZn02Controller.Listener {
  int notifications;String last;
  public void onStatus(String status){notifications++;last=status;}
 }
 private void emit(GoroZn02Controller controller,String value)throws Exception{
  Method method=GoroZn02Controller.class.getDeclaredMethod("emit",String.class);
  method.setAccessible(true);method.invoke(controller,value);
 }
 @Test public void oldDisconnectCannotStopANewerConnection()throws Exception{
  Listener listener=new Listener();GoroZn02Controller controller=new GoroZn02Controller(RuntimeEnvironment.getApplication(),listener);
  try{
   emit(controller,"ZNP Z02 desconectado");
   Field epoch=GoroZn02Controller.class.getDeclaredField("connectionEpoch");epoch.setAccessible(true);
   epoch.setLong(controller,epoch.getLong(controller)+1);
   shadowOf(Looper.getMainLooper()).idle();
   assertEquals(0,listener.notifications);
  }finally{controller.close();}
 }
 @Test public void currentConnectionStatusIsDelivered()throws Exception{
  Listener listener=new Listener();GoroZn02Controller controller=new GoroZn02Controller(RuntimeEnvironment.getApplication(),listener);
  try{
   emit(controller,"Z02 conectado");shadowOf(Looper.getMainLooper()).idle();
   assertEquals(1,listener.notifications);assertEquals("Z02 conectado",listener.last);
  }finally{controller.close();}
 }
}
