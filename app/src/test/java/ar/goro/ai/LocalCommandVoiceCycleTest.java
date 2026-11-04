package ar.goro.ai;

import android.Manifest;
import android.content.Context;
import android.location.LocationManager;
import android.widget.TextView;
import ar.goro.ai.calendar.CalendarEvent;
import ar.goro.ai.calendar.DeviceCalendarGateway;
import ar.goro.ai.core.NemotronSpeechGateway;
import ar.goro.ai.memory.MemoryStore;
import ar.goro.ai.weather.DeviceLocationProvider;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,shadows=LocalCommandVoiceCycleTest.HeldSpeech.class,
 instrumentedPackages="ar.goro.ai.core")
@LooperMode(LooperMode.Mode.PAUSED)
public class LocalCommandVoiceCycleTest {
 /** Hold only the external TTS boundary. Never return invented successful audio. */
 @Implements(value=NemotronSpeechGateway.class,isInAndroidSdk=false)
 public static class HeldSpeech {
  static NemotronSpeechGateway.Callback pending;
  @Implementation protected void synthesize(String text,NemotronSpeechGateway.Callback callback){pending=callback;}
 }
 private MainActivity activity;
 private TextView output;
 private DeviceCalendarGateway calendar;
 private MemoryStore memory;
 private static Field field(String name)throws Exception{
  Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f;
 }
 private Object get(String name)throws Exception{return field(name).get(activity);}
 private void set(String name,Object value)throws Exception{field(name).set(activity,value);}
 private void command(String text)throws Exception{
  set("voiceActive",true);HeldSpeech.pending=null;
  Method m=MainActivity.class.getDeclaredMethod("runCommand",String.class,boolean.class);
  m.setAccessible(true);m.invoke(activity,text,true);
 }
 @Before public void setup()throws Exception{
  // No onCreate: no renderer, hardware startup, account access or credential read.
  activity=Robolectric.buildActivity(MainActivity.class).get();
  output=new TextView(activity);set("output",output);set("state",new TextView(activity));
  set("foreground",true);set("voiceActive",true);
  set("zn02Controller",new GoroZn02Controller(RuntimeEnvironment.getApplication(),status->{}));
  set("speechGateway",new NemotronSpeechGateway("nvapi-test-only-not-a-credential"));
  Context context=RuntimeEnvironment.getApplication();
  context.getSharedPreferences("goro_calendar_link",Context.MODE_PRIVATE).edit().clear().commit();
  calendar=new DeviceCalendarGateway(context);set("calendarGateway",calendar);
  memory=new MemoryStore(context);memory.clearAll();set("memoryStore",memory);
  set("locationProvider",new DeviceLocationProvider(context));
 }
 @After public void cleanup(){activity.onDestroy();memory.close();HeldSpeech.pending=null;}
 private void assertSpokenAndRecoversOnSynthesisFailure()throws Exception{
  assertNotNull("Command must enter the speech cycle",HeldSpeech.pending);
  assertTrue((Boolean)get("speechInProgress"));assertTrue((Boolean)get("voiceActive"));
  assertNull(get("pendingVoiceRetry"));
  HeldSpeech.pending.completed(null,"Síntesis detenida por la prueba local");
  assertFalse((Boolean)get("speechInProgress"));
  // The real disconnected controller rejects reopening; reaching that error
  // proves the cycle attempted capture again, without claiming hardware success.
  assertFalse((Boolean)get("voiceActive"));
  assertTrue(((TextView)get("state")).getText().toString().contains("Micrófono Z02 no disponible"));
 }
 @Test public void unaccentedAgendaCreationReachesConfirmationInsteadOfListing()throws Exception{
  command("agenda reunión mañana a las 15:30 por 45 minutos");
  CalendarEvent event=(CalendarEvent)get("pendingCalendarEvent");
  assertNotNull(event);assertEquals("reunión",event.title);
  assertTrue(output.getText().toString().contains("Decí confirmar"));
  assertSpokenAndRecoversOnSynthesisFailure();
 }
 @Test public void agendaListingStillRespondsAndRecovers()throws Exception{
  command("agenda mañana");
  assertNull(get("pendingCalendarEvent"));
  assertTrue(output.getText().toString().contains("vinculá la cuenta"));
  assertSpokenAndRecoversOnSynthesisFailure();
  calendar.setSelectedGoogleAccount("owner@example.test");
  shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.READ_CALENDAR);
  command("mi agenda hoy");
  assertTrue(output.getText().toString().contains("permiso para leer"));
  assertSpokenAndRecoversOnSynthesisFailure();
 }
 @Test public void confirmationAndCancellationBothReturnToTheSpeechCycle()throws Exception{
  command("confirmar");assertTrue(output.getText().toString().contains("ninguna operación"));
  assertSpokenAndRecoversOnSynthesisFailure();
  command("agendá reunión mañana a las 15:30");assertNotNull(get("pendingCalendarEvent"));
  assertSpokenAndRecoversOnSynthesisFailure();
  shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.WRITE_CALENDAR);
  command("confirmar");assertNull(get("pendingCalendarEvent"));
  assertTrue(output.getText().toString().contains("permisos de lectura y escritura"));
  assertSpokenAndRecoversOnSynthesisFailure();
  command("agendá reunión mañana a las 15:30");assertNotNull(get("pendingCalendarEvent"));
  assertSpokenAndRecoversOnSynthesisFailure();
  command("cancelar");assertNull(get("pendingCalendarEvent"));assertNull(get("pendingCalendarDeleteId"));
  assertTrue(output.getText().toString().contains("cancelada"));
  assertSpokenAndRecoversOnSynthesisFailure();
 }
 @Test public void memoryCommandsKeepTheSpeechCycle()throws Exception{
  command("acordate que la reunión es el jueves");assertEquals(1,memory.recent(8).size());
  assertSpokenAndRecoversOnSynthesisFailure();
  command("qué recordás");assertTrue(output.getText().toString().contains("jueves"));
  assertSpokenAndRecoversOnSynthesisFailure();
  command("buscá en recuerdos reunión");assertTrue(output.getText().toString().contains("jueves"));
  assertSpokenAndRecoversOnSynthesisFailure();
  long id=memory.recent(8).get(0).id;
  command("editá recuerdo "+id+" la reunión es el viernes");assertTrue(memory.recent(8).get(0).content.contains("viernes"));
  assertSpokenAndRecoversOnSynthesisFailure();
  command("borrá recuerdo "+id);assertTrue(memory.recent(8).isEmpty());
  assertSpokenAndRecoversOnSynthesisFailure();
 }
 @Test public void weatherPermissionAndLocationFailuresRemainSpoken()throws Exception{
  shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION);
  command("clima");assertNull(HeldSpeech.pending);
  activity.onRequestPermissionsResult(64,new String[]{Manifest.permission.ACCESS_FINE_LOCATION},new int[]{-1});
  assertTrue(output.getText().toString().contains("No se concedió permiso"));
  assertSpokenAndRecoversOnSynthesisFailure();
  shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION);
  LocationManager manager=(LocationManager)RuntimeEnvironment.getApplication().getSystemService(Context.LOCATION_SERVICE);
  shadowOf(manager).setProviderEnabled(LocationManager.GPS_PROVIDER,false);
  shadowOf(manager).setProviderEnabled(LocationManager.NETWORK_PROVIDER,false);
  command("pronóstico");assertTrue(output.getText().toString().contains("Activá la ubicación"));
  assertSpokenAndRecoversOnSynthesisFailure();
  command("clima en Mendoza");assertTrue(output.getText().toString().contains("no está disponible"));
  assertSpokenAndRecoversOnSynthesisFailure();
 }
}
