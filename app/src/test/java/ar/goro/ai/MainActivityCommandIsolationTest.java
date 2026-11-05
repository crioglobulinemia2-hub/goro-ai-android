package ar.goro.ai;

import android.content.Context;
import android.widget.TextView;
import ar.goro.ai.calendar.CalendarEvent;
import ar.goro.ai.calendar.DeviceCalendarGateway;
import ar.goro.ai.core.ModelGateway;
import ar.goro.ai.core.NemotronGateway;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.LooperMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,shadows=MainActivityCommandIsolationTest.HeldModel.class,
 instrumentedPackages="ar.goro.ai.core")
@LooperMode(LooperMode.Mode.PAUSED)
public class MainActivityCommandIsolationTest {
 @Implements(value=NemotronGateway.class,isInAndroidSdk=false)
 public static class HeldModel {
  static ModelGateway.Callback pending;
  @Implementation protected void request(String id,String text,ModelGateway.Callback callback){pending=callback;}
 }
 private MainActivity activity;
 private TextView output;
 private static Field field(String name)throws Exception{
  Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f;
 }
 private Object get(String name)throws Exception{return field(name).get(activity);}
 private void set(String name,Object value)throws Exception{field(name).set(activity,value);}
 private void command(String text)throws Exception{
  Method m=MainActivity.class.getDeclaredMethod("runCommand",String.class,boolean.class);
  m.setAccessible(true);m.invoke(activity,text,false);
 }
 @Before public void setup()throws Exception{
  // No onCreate: no renderer, credentials, Bluetooth startup, or remote calls.
  activity=Robolectric.buildActivity(MainActivity.class).get();
  output=new TextView(activity);set("output",output);set("state",new TextView(activity));
  set("foreground",true);
  Context context=RuntimeEnvironment.getApplication();
  context.getSharedPreferences("goro_calendar_link",Context.MODE_PRIVATE).edit().clear().commit();
  set("calendarGateway",new DeviceCalendarGateway(context));
  set("nemotronGateway",new NemotronGateway("nvapi-test"));
  HeldModel.pending=null;
 }
 @After public void cleanup(){activity.onDestroy();HeldModel.pending=null;}
 private void assertNoPending()throws Exception{
  assertNull(get("pendingCalendarEvent"));assertNull(get("pendingCalendarDeleteId"));
  command("confirmar");
  assertTrue(output.getText().toString().contains("No hay ninguna operación"));
 }
 private void prepareCreation()throws Exception{
  command("agendá reunión mañana a las 15:30");assertNotNull(get("pendingCalendarEvent"));
 }
 @Test public void malformedNewEventCannotConfirmThePreviousCreation()throws Exception{
  prepareCreation();command("agendá otro encuentro sin fecha");assertNoPending();
 }
 @Test public void invalidUpdateCannotConfirmThePreviousCreation()throws Exception{
  prepareCreation();command("reprogramá evento 9 mañana a las 15:30");assertNoPending();
 }
 @Test public void unknownDeletionCannotConfirmThePreviousDeletion()throws Exception{
  set("pendingCalendarDeleteId","previous-id");
  command("eliminá evento 7");assertNoPending();
 }
 @Test public void uppercaseDeletionResolvesTheSelectedEvent()throws Exception{
  CalendarEvent event=new CalendarEvent("chosen-id","Consulta","2026-11-05T10:00:00Z","2026-11-05T11:00:00Z","UTC","");
  ((List<CalendarEvent>)get("lastCalendarEvents")).add(event);
  command("ELIMINÁ EVENTO 1");assertEquals("chosen-id",get("pendingCalendarDeleteId"));
  assertTrue(output.getText().toString().contains("Voy a eliminar Consulta"));
 }
 @Test public void modelAnswerCannotReplaceNewerLocalResult()throws Exception{
  command("Contame algo sobre Saturno");assertNotNull(HeldModel.pending);
  ModelGateway.Callback old=HeldModel.pending;
  command("2+3");String expected=output.getText().toString();
  old.completed("Respuesta vieja",null);
  assertEquals(expected,output.getText().toString());
 }
 @Test public void modelAnswerCannotReplaceNewerScreenOnlyCommand()throws Exception{
  command("Contame algo sobre Saturno");ModelGateway.Callback old=HeldModel.pending;
  command("mis correos");output.setText("Nueva consulta de correo");
  old.completed("Respuesta vieja",null);
  assertEquals("Nueva consulta de correo",output.getText().toString());
 }
 @Test public void modelAnswerFromBeforeBackgroundCannotSpeakAfterReturn()throws Exception{
  command("Contame algo sobre Saturno");ModelGateway.Callback old=HeldModel.pending;
  activity.onStop();set("foreground",true);output.setText("De vuelta");
  old.completed("Respuesta vieja",null);
  assertEquals("De vuelta",output.getText().toString());
 }
 @Test public void currentModelAnswerStillReachesTheUser()throws Exception{
  command("Contame algo sobre Saturno");assertNotNull(HeldModel.pending);
  HeldModel.pending.completed("Respuesta actual",null);
  assertTrue(output.getText().toString().startsWith("Respuesta actual"));
 }
}
