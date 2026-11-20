package ar.goro.ai;
import org.junit.Test;
import static org.junit.Assert.*;
public class ZnpDeviceProfileTest {
 @Test public void detectsActualZnpZ02Name(){
  assertEquals(ZnpDeviceProfile.Model.Z02_CANDIDATE,ZnpDeviceProfile.detect("ZNP Z02").model);
  assertEquals(ZnpDeviceProfile.Model.Z02_CANDIDATE,ZnpDeviceProfile.detect("ZNP-Z02").model);
 }
 @Test public void detectsN02AliasAsDifferentCandidate(){
  assertEquals(ZnpDeviceProfile.Model.N02_CANDIDATE,ZnpDeviceProfile.detect("ZNP N02").model);
  assertEquals(ZnpDeviceProfile.Model.N02_CANDIDATE,ZnpDeviceProfile.detect("ZN02").model);
 }
 @Test public void genericRcspDoesNotIdentifyDevice(){
  assertEquals(ZnpDeviceProfile.Model.UNKNOWN,ZnpDeviceProfile.detect("Unknown badge",true).model);
 }
 @Test public void unknownIsSafe(){assertEquals(ZnpDeviceProfile.Model.UNKNOWN,ZnpDeviceProfile.detect(null).model);}
 @Test public void namesNeverInventCapabilities(){
  ZnpDeviceProfile p=ZnpDeviceProfile.detect("ZNP Z02");
  assertTrue(p.isCandidate());assertFalse(p.canShowGoro());assertFalse(p.canRouteAudio());assertFalse(p.canUseTouch());
 }
}
