package ar.goro.ai.weather;
import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import androidx.core.content.ContextCompat;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
public class DeviceLocationProviderTest {
 @Test public void doesNotInventLocationWithoutPermission(){
  Context c=RuntimeEnvironment.getApplication();
  DeviceLocationProvider provider=new DeviceLocationProvider(c);
  assertFalse(provider.hasPermission());
  final String[] error={null};
  provider.current((location,message)->error[0]=message);
  assertNotNull(error[0]);
  assertNull(error[0].isEmpty()?null:error[0]);
 }
}
