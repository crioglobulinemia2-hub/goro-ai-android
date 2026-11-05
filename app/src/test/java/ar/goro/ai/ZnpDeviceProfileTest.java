package ar.goro.ai;
import org.junit.Test;
import static org.junit.Assert.*;
public class ZnpDeviceProfileTest {
 @Test public void detectsAllThreeRequestedModels(){
  assertEquals(ZnpDeviceProfile.Model.Z01_CANDIDATE,ZnpDeviceProfile.detect("ZNP Z01").model);
  assertEquals(ZnpDeviceProfile.Model.Z01_CANDIDATE,ZnpDeviceProfile.detect("ZNP-Z01").model);
  assertEquals(ZnpDeviceProfile.Model.Z02_CANDIDATE,ZnpDeviceProfile.detect("ZNP Z02").model);
  assertEquals(ZnpDeviceProfile.Model.Z02_CANDIDATE,ZnpDeviceProfile.detect("ZNP-Z02").model);
  assertEquals(ZnpDeviceProfile.Model.N02_CANDIDATE,ZnpDeviceProfile.detect("ZNP N02").model);
  assertEquals(ZnpDeviceProfile.Model.N02_CANDIDATE,ZnpDeviceProfile.detect("ZN02").model);
 }
 @Test public void onlyZ02UsesTheExistingVerifiedRcspController(){
  assertTrue(ZnpDeviceProfile.detect("ZNP Z02").isRcspCandidate());
  assertFalse(ZnpDeviceProfile.detect("ZNP Z01").isRcspCandidate());
  assertFalse(ZnpDeviceProfile.detect("ZNP N02").isRcspCandidate());
 }
 @Test public void eachCandidateHasAnExplicitAnimationProfile(){
  assertEquals("z01",ZnpDeviceProfile.detect("ZNP Z01").animationProfile());
  assertEquals("z02",ZnpDeviceProfile.detect("ZNP Z02").animationProfile());
  assertEquals("n02",ZnpDeviceProfile.detect("ZNP N02").animationProfile());
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
