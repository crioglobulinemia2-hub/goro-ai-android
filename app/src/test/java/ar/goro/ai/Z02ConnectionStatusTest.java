package ar.goro.ai;
import android.os.Looper;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import com.jieli.bluetooth.constant.StateCode;
import com.jieli.bluetooth.interfaces.rcsp.callback.BTRcspEventCallback;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
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
 private Field field(String name)throws Exception{
  Field field=GoroZn02Controller.class.getDeclaredField(name);field.setAccessible(true);return field;
 }
 @Test public void duplicateConnectedEventsCannotInvalidateAnActiveMicrophoneCapture()throws Exception{
  Listener listener=new Listener();GoroZn02Controller controller=new GoroZn02Controller(RuntimeEnvironment.getApplication(),listener);
  try{
   BluetoothDevice device=BluetoothAdapter.getDefaultAdapter().getRemoteDevice("12:34:56:78:90:AB");
   field("target").set(controller,device);
   BTRcspEventCallback events=(BTRcspEventCallback)field("eventCallback").get(controller);
   events.onConnection(device,StateCode.CONNECTION_CONNECTED);
   shadowOf(Looper.getMainLooper()).idle();
   long epoch=field("connectionEpoch").getLong(controller);
   field("recording").setBoolean(controller,true);
   events.onConnection(device,StateCode.CONNECTION_OK);
   shadowOf(Looper.getMainLooper()).idle();
   assertEquals("The existing capture must retain its live connection epoch",epoch,field("connectionEpoch").getLong(controller));
   assertTrue(field("recording").getBoolean(controller));
  }finally{controller.close();}
 }
 @Test public void authenticationTimeoutEndsPollingAndInvalidatesTheExpiredAttempt()throws Exception{
  Listener listener=new Listener();GoroZn02Controller controller=new GoroZn02Controller(RuntimeEnvironment.getApplication(),listener);
  try{
   BluetoothDevice device=BluetoothAdapter.getDefaultAdapter().getRemoteDevice("12:34:56:78:90:AB");
   field("target").set(controller,device);
   BTRcspEventCallback events=(BTRcspEventCallback)field("eventCallback").get(controller);
   events.onConnection(device,StateCode.CONNECTION_CONNECTED);
   shadowOf(Looper.getMainLooper()).idle();
   long epoch=field("connectionEpoch").getLong(controller);
   shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(21));
   assertFalse(field("connected").getBoolean(controller));
   assertTrue(field("connectionEpoch").getLong(controller)>epoch);
   assertNull(field("authPoll").get(controller));
   assertTrue(listener.last.contains("20 segundos"));
   int notifications=listener.notifications;
   shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(1));
   assertEquals(notifications,listener.notifications);
  }finally{controller.close();}
 }
 @Test public void reconnectButtonPreservesAnOpenLinkAndItsCapture()throws Exception{
  Listener listener=new Listener();GoroZn02Controller controller=new GoroZn02Controller(RuntimeEnvironment.getApplication(),listener);
  try{
   BluetoothDevice device=BluetoothAdapter.getDefaultAdapter().getRemoteDevice("12:34:56:78:90:AB");
   field("target").set(controller,device);field("connected").setBoolean(controller,true);
   field("recording").setBoolean(controller,true);
   long epoch=field("connectionEpoch").getLong(controller);
   controller.startScan();shadowOf(Looper.getMainLooper()).idle();
   assertSame(device,field("target").get(controller));
   assertTrue(field("connected").getBoolean(controller));
   assertTrue(controller.isVoiceCaptureActive());
   assertEquals(epoch,field("connectionEpoch").getLong(controller));
   assertEquals("El Z02 ya tiene una sesión Bluetooth abierta.",listener.last);
  }finally{controller.close();}
 }
 @Test public void selectingAnotherModelNeverSilentlyReusesTheCurrentLink()throws Exception{
  Listener listener=new Listener();GoroZn02Controller controller=new GoroZn02Controller(RuntimeEnvironment.getApplication(),listener);
  try{
   BluetoothDevice device=BluetoothAdapter.getDefaultAdapter().getRemoteDevice("12:34:56:78:90:AB");
   field("target").set(controller,device);field("connected").setBoolean(controller,true);
   field("activeModel").set(controller,"Z02");
   controller.startScan("N02");shadowOf(Looper.getMainLooper()).idle();
   assertTrue(field("connected").getBoolean(controller));
   assertSame(device,field("target").get(controller));
   assertEquals("Z02",field("activeModel").get(controller));
   assertTrue(listener.last.contains("ZNP Z02"));
   assertFalse(field("scanning").getBoolean(controller));
  }finally{controller.close();}
 }
 @Test public void explicitDisconnectReleasesCurrentModelAndCapture()throws Exception{
  Listener listener=new Listener();GoroZn02Controller controller=new GoroZn02Controller(RuntimeEnvironment.getApplication(),listener);
  try{
   BluetoothDevice device=BluetoothAdapter.getDefaultAdapter().getRemoteDevice("12:34:56:78:90:AB");
   field("target").set(controller,device);field("connected").setBoolean(controller,true);
   field("recording").setBoolean(controller,true);
   controller.disconnect();shadowOf(Looper.getMainLooper()).idle();
   assertNull(field("target").get(controller));
   assertFalse(field("connected").getBoolean(controller));
   assertFalse(controller.isVoiceCaptureActive());
   assertEquals("Dispositivo ZNP desconectado desde GORO.",listener.last);
  }finally{controller.close();}
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
