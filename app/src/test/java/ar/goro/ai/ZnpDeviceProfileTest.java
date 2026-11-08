package ar.goro.ai;
import org.junit.Test;
import static org.junit.Assert.*;
public class ZnpDeviceProfileTest {
 @Test public void detectsZ02(){assertEquals(ZnpDeviceProfile.Model.Z02,ZnpDeviceProfile.detect("ZNP-Z02").model);}
 @Test public void detectsN02(){assertEquals(ZnpDeviceProfile.Model.N02,ZnpDeviceProfile.detect("ZNP N02").model);}
 @Test public void detectsMoreNamedModels(){
  assertEquals(ZnpDeviceProfile.Model.AN02,ZnpDeviceProfile.detect("AN02").model);
  assertEquals(ZnpDeviceProfile.Model.AZ02,ZnpDeviceProfile.detect("AZ02").model);
  assertEquals(ZnpDeviceProfile.Model.AZ01,ZnpDeviceProfile.detect("AZ01").model);
  assertFalse(ZnpDeviceProfile.detect("AZ01").canRouteAudio());
 }
 @Test public void unknownIsSafe(){assertEquals(ZnpDeviceProfile.Model.UNKNOWN,ZnpDeviceProfile.detect(null).model);}
 @Test public void detectsJieliRcspWithoutInventingDeviceCapabilities(){ZnpDeviceProfile p=ZnpDeviceProfile.detect("Unknown badge",true);assertEquals(ZnpDeviceProfile.Model.JIELI_RCSP_UNVERIFIED,p.model);assertFalse(p.canShowGoro());assertFalse(p.canUseTouch());assertFalse(p.canRouteAudio());}
 @Test public void noInventedCapabilities(){assertFalse(ZnpDeviceProfile.detect("N02").canShowGoro());assertFalse(ZnpDeviceProfile.detect("Z02").canRouteAudio());}
}
