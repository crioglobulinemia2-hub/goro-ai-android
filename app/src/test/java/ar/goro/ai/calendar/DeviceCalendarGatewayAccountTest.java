package ar.goro.ai.calendar;
import android.content.Context;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
public class DeviceCalendarGatewayAccountTest {
 @Test public void selectedGmailAccountPersistsAndCanBeUnlinked(){
  Context context=RuntimeEnvironment.getApplication();context.getSharedPreferences("goro_calendar_link",Context.MODE_PRIVATE).edit().clear().commit();
  DeviceCalendarGateway gateway=new DeviceCalendarGateway(context);
  assertNull(gateway.selectedGoogleAccount());
  gateway.setSelectedGoogleAccount("matias@example.com");
  assertEquals("matias@example.com",gateway.selectedGoogleAccount());
  DeviceCalendarGateway reopened=new DeviceCalendarGateway(context);
  assertEquals("matias@example.com",reopened.selectedGoogleAccount());
  reopened.unlinkGoogleAccount();assertNull(gateway.selectedGoogleAccount());
  gateway.close();reopened.close();
 }
 @Test(expected=IllegalArgumentException.class) public void rejectsNonAccountValue(){
  new DeviceCalendarGateway(RuntimeEnvironment.getApplication()).setSelectedGoogleAccount("not-an-email");
 }
}
