package ar.goro.ai;

import ar.goro.ai.calendar.CalendarEvent;
import ar.goro.ai.calendar.DeviceCalendarGateway;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import android.content.Context;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class MainActivityCalendarAccountTest {
 private MainActivity activity;private DeviceCalendarGateway gateway;
 private static Field field(String name)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f;}
 @Before public void setup()throws Exception{
  Context context=RuntimeEnvironment.getApplication();
  context.getSharedPreferences("goro_calendar_link",Context.MODE_PRIVATE).edit().clear().commit();
  activity=Robolectric.buildActivity(MainActivity.class).get();
  gateway=new DeviceCalendarGateway(context);gateway.setSelectedGoogleAccount("one@example.test");
  field("calendarGateway").set(activity,gateway);
  CalendarEvent event=new CalendarEvent("11","Reunión","2026-11-04T10:00:00Z","2026-11-04T11:00:00Z","UTC","");
  ((List<CalendarEvent>)field("lastCalendarEvents").get(activity)).add(event);
  field("pendingCalendarEvent").set(activity,event);field("pendingCalendarDeleteId").set(activity,"11");
 }
 @After public void teardown(){gateway.close();}
 private void select(String account)throws Exception{
  Method method=MainActivity.class.getDeclaredMethod("setGoogleAccount",String.class);method.setAccessible(true);method.invoke(activity,account);
 }
 private void assertCleared()throws Exception{
  assertTrue(((List<?>)field("lastCalendarEvents").get(activity)).isEmpty());
  assertNull(field("pendingCalendarEvent").get(activity));assertNull(field("pendingCalendarDeleteId").get(activity));
 }
 @Test public void changingGoogleAccountClearsPriorCalendarSelectionAndConfirmation()throws Exception{
  select("two@example.test");assertEquals("two@example.test",gateway.selectedGoogleAccount());assertCleared();
 }
 @Test public void unlinkClearsPriorCalendarSelectionAndConfirmation()throws Exception{
  select(null);assertNull(gateway.selectedGoogleAccount());assertCleared();
 }
 @Test public void selectingSameAccountPreservesPendingConfirmation()throws Exception{
  select("one@example.test");assertEquals(1,((List<?>)field("lastCalendarEvents").get(activity)).size());
  assertNotNull(field("pendingCalendarEvent").get(activity));
 }
}
