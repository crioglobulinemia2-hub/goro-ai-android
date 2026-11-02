package ar.goro.ai;
import org.junit.Test;
import static org.junit.Assert.*;
public class ZnpDeviceProfileTest {
 @Test public void detectsN02(){assertEquals(ZnpDeviceProfile.Model.N02,ZnpDeviceProfile.detect("ZNP N02").model);}
 @Test public void detectsZn02Alias(){assertEquals(ZnpDeviceProfile.Model.N02,ZnpDeviceProfile.detect("ZN02").model);}
 @Test public void rejectsZ02UsedBySeparateCabezónProject(){assertEquals(ZnpDeviceProfile.Model.UNKNOWN,ZnpDeviceProfile.detect("ZNP-Z02").model);}
 @Test public void genericRcspDoesNotIdentifyN02(){assertEquals(ZnpDeviceProfile.Model.UNKNOWN,ZnpDeviceProfile.detect("Unknown badge",true).model);}
 @Test public void unknownIsSafe(){assertEquals(ZnpDeviceProfile.Model.UNKNOWN,ZnpDeviceProfile.detect(null).model);}
 @Test public void noUnverifiedCapabilitiesAreInvented(){ZnpDeviceProfile p=ZnpDeviceProfile.detect("ZN02");assertFalse(p.canShowGoro());assertFalse(p.canRouteAudio());assertFalse(p.canUseTouch());}
}
