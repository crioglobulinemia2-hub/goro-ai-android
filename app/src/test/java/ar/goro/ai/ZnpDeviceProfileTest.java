package ar.goro.ai;
import org.junit.Test;
import static org.junit.Assert.*;
public class ZnpDeviceProfileTest {
 @Test public void detectsZ02(){assertEquals(ZnpDeviceProfile.Model.Z02,ZnpDeviceProfile.detect("ZNP-Z02").model);}
 @Test public void detectsN02(){assertEquals(ZnpDeviceProfile.Model.N02,ZnpDeviceProfile.detect("ZNP N02").model);}
 @Test public void unknownIsSafe(){assertEquals(ZnpDeviceProfile.Model.UNKNOWN,ZnpDeviceProfile.detect(null).model);}
 @Test public void noInventedCapabilities(){assertFalse(ZnpDeviceProfile.detect("N02").canShowGoro());assertFalse(ZnpDeviceProfile.detect("Z02").canRouteAudio());}
}
