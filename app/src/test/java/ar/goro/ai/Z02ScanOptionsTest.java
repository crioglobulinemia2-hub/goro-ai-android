package ar.goro.ai;
import com.jieli.bluetooth.bean.BluetoothOption;
import com.jieli.bluetooth.constant.BluetoothConstant;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class Z02ScanOptionsTest {
 @Test public void vendorAdvertisementMarkersAndNameCacheCannotHideZ02BeforeAppChecks(){
  BluetoothOption option=GoroZn02Controller.scanOptions();
  assertEquals(BluetoothConstant.NONE_FILTER,option.getBleScanStrategy());
  assertFalse(option.isSkipNoNameDev());
  assertTrue(option.isUseDeviceAuth());
  assertTrue("RCSP control of ZNP charging cases must use BLE",option.isMandatoryUseBLE());
 }
}
