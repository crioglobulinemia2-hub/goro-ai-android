package ar.goro.ai;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.Looper;
import com.jieli.bluetooth.bean.base.BaseError;
import com.jieli.bluetooth.interfaces.rcsp.callback.OnRcspActionCallback;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.util.ReflectionHelpers;
import static org.robolectric.Shadows.shadowOf;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
@LooperMode(LooperMode.Mode.PAUSED)
public class Z02FlashlightSessionTest {
 private GoroZn02Controller controller;
 private BluetoothDevice device;
 private final List<String> statuses=new ArrayList<>();
 @Before public void setup(){
  controller=new GoroZn02Controller(RuntimeEnvironment.getApplication(),statuses::add);
  device=BluetoothAdapter.getDefaultAdapter().getRemoteDevice("12:34:56:78:90:AB");
  ReflectionHelpers.setField(controller,"target",device);
  ReflectionHelpers.setField(controller,"connected",true);
  ReflectionHelpers.setField(controller,"vendorProtocolVerified",true);
  ReflectionHelpers.setField(controller,"connectionEpoch",3L);
 }
 @After public void cleanup(){controller.close();}
 private void idle(){shadowOf(Looper.getMainLooper()).idle();}
 private void reconnectSameDevice(){ReflectionHelpers.setField(controller,"connectionEpoch",4L);}
 @SuppressWarnings("unchecked")
 private OnRcspActionCallback<Boolean> callback(String method)throws Exception{
  Method m=GoroZn02Controller.class.getDeclaredMethod(method,long.class);m.setAccessible(true);
  return (OnRcspActionCallback<Boolean>)m.invoke(controller,3L);
 }
 @Test public void previousConnectionSetResponseCannotQueryTheReconnectedDevice()throws Exception{
  OnRcspActionCallback<Boolean> old=callback("flashlightSetCallback");
  reconnectSameDevice();old.onSuccess(device,true);idle();
  assertTrue(statuses.isEmpty());
 }
 @Test public void previousConnectionReadCannotReportStateOrErrorAsCurrent()throws Exception{
  OnRcspActionCallback<Boolean> old=callback("flashlightReadCallback");
  reconnectSameDevice();old.onSuccess(device,true);old.onError(device,new BaseError(1,"old"));idle();
  assertTrue(statuses.isEmpty());
 }
 @Test public void disconnectBeforeCallbackDispatchCannotTriggerAnotherCommand()throws Exception{
  callback("flashlightSetCallback").onSuccess(device,true);
  ReflectionHelpers.setField(controller,"connected",false);idle();
  assertTrue(statuses.isEmpty());
 }
 @Test public void unknownStateMustNotBeReportedAsLightOff()throws Exception{
  callback("flashlightReadCallback").onSuccess(device,null);idle();
  assertFalse(statuses.stream().anyMatch(s->s.contains("APAGADA")));
  assertTrue(statuses.stream().anyMatch(s->s.contains("no confirmó el estado")));
 }
 @Test public void currentConnectionReadReportsVerifiedState()throws Exception{
  callback("flashlightReadCallback").onSuccess(device,true);idle();
  assertTrue(statuses.stream().anyMatch(s->s.contains("Estado real")&&s.contains("ENCENDIDA")));
 }
 private com.jieli.bluetooth.interfaces.rcsp.charging_case.OnChargingCaseListener observation()throws Exception{
  Method method=GoroZn02Controller.class.getDeclaredMethod("createCaseListener",long.class);method.setAccessible(true);
  return (com.jieli.bluetooth.interfaces.rcsp.charging_case.OnChargingCaseListener)method.invoke(controller,3L);
 }
 @Test public void oldUnsolicitedCaseEventCannotReportTheNewConnectionState()throws Exception{
  com.jieli.bluetooth.interfaces.rcsp.charging_case.OnChargingCaseListener old=observation();
  old.onInit(device,0);old.onChargingCaseEvent(device,null);
  reconnectSameDevice();idle();
  assertTrue(statuses.isEmpty());
 }
 @Test public void currentCaseObservationReportsItsInitialization()throws Exception{
  observation().onInit(device,0);idle();
  assertTrue(statuses.stream().anyMatch(s->s.contains("inicialización Bluetooth completada")));
 }

}
