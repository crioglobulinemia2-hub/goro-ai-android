package ar.goro.ai;

import android.content.Context;
import android.location.LocationManager;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
public class Z02ScanLocationTest {
 @Test @Config(sdk=30) public void android11RequiresLocationServiceEvenWhenBluetoothPermissionExists(){
  Context context=RuntimeEnvironment.getApplication();
  LocationManager location=(LocationManager)context.getSystemService(Context.LOCATION_SERVICE);
  shadowOf(location).setLocationEnabled(false);
  assertFalse(GoroZn02Controller.isScanLocationReady(context));
  shadowOf(location).setLocationEnabled(true);
  assertTrue(GoroZn02Controller.isScanLocationReady(context));
 }
 @Test @Config(sdk=31) public void android12NeverForLocationScanDoesNotRequireLocationService(){
  Context context=RuntimeEnvironment.getApplication();
  LocationManager location=(LocationManager)context.getSystemService(Context.LOCATION_SERVICE);
  shadowOf(location).setLocationEnabled(false);
  assertTrue(GoroZn02Controller.isScanLocationReady(context));
 }
}
