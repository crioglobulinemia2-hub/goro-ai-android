package ar.goro.ai;

import android.accounts.AccountManager;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import ar.goro.ai.calendar.DeviceCalendarGateway;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** Gmail is an independent user-visible authorization flow, not Calendar sync. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class MainActivityGmailLinkTest {
 private static Field field(String name)throws Exception{
  Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f;
 }
 private MainActivity activity()throws Exception{
  Context context=RuntimeEnvironment.getApplication();
  context.getSharedPreferences("goro_calendar_link",Context.MODE_PRIVATE).edit().clear().commit();
  MainActivity activity=Robolectric.buildActivity(MainActivity.class).get();
  field("calendarGateway").set(activity,new DeviceCalendarGateway(context));
  return activity;
 }
 private void readGmail(MainActivity activity)throws Exception{
  Method m=MainActivity.class.getDeclaredMethod("readGmail",boolean.class);
  m.setAccessible(true);m.invoke(activity,false);
 }
 @Test public void gmailWithoutAccountOpensGooglePickerRatherThanCalendarPermission()throws Exception{
  MainActivity a=activity();
  readGmail(a);
  assertTrue(field("googlePickerForGmail").getBoolean(a));
  assertFalse(field("gmailPickerFromVoice").getBoolean(a));
  // Cancellation must reset the picker state and must not link a fake account.
  a.onActivityResult(63,Activity.RESULT_CANCELED,null);
  assertFalse(field("googlePickerForGmail").getBoolean(a));
  DeviceCalendarGateway gateway=(DeviceCalendarGateway)field("calendarGateway").get(a);
  assertNull(gateway.selectedGoogleAccount());
  gateway.close();
 }
 @Test public void accountSelectionForGmailDoesNotNeedCalendarPermissions()throws Exception{
  MainActivity a=activity();
  readGmail(a);
  assertTrue(field("googlePickerForGmail").getBoolean(a));
  Intent chosen=new Intent().putExtra(AccountManager.KEY_ACCOUNT_NAME,"owner@example.test")
      .putExtra(AccountManager.KEY_ACCOUNT_TYPE,"com.google");
  a.onActivityResult(63,Activity.RESULT_OK,chosen);
  DeviceCalendarGateway gateway=(DeviceCalendarGateway)field("calendarGateway").get(a);
  assertEquals("owner@example.test",gateway.selectedGoogleAccount());
  assertFalse(field("googlePickerForGmail").getBoolean(a));
  gateway.close();
 }
 @Test public void gmailSearchKeepsQueryAcrossAccountSelection()throws Exception{
  MainActivity a=activity();
  Method m=MainActivity.class.getDeclaredMethod("searchGmail",String.class,boolean.class);
  m.setAccessible(true);m.invoke(a,"turnos de noviembre",false);
  assertTrue(field("googlePickerForGmail").getBoolean(a));
  assertEquals("turnos de noviembre",field("pendingGmailSearchAfterAccount").get(a));
  a.onActivityResult(63,Activity.RESULT_CANCELED,null);
  assertNull(field("pendingGmailSearchAfterAccount").get(a));
  ((DeviceCalendarGateway)field("calendarGateway").get(a)).close();
 }

}