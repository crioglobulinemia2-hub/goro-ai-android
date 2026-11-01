package ar.goro.ai;
import java.util.Locale;
/** Explicit capabilities: unknown is not equivalent to supported. */
public final class ZnpDeviceProfile {
 public enum Model { Z02, N02, UNKNOWN }
 public final Model model;
 public final boolean screenVerified;
 public final boolean audioVerified;
 public final boolean touchVerified;
 private ZnpDeviceProfile(Model m,boolean s,boolean a,boolean t){model=m;screenVerified=s;audioVerified=a;touchVerified=t;}
 public static ZnpDeviceProfile detect(String advertisedName){
  if(advertisedName==null)return new ZnpDeviceProfile(Model.UNKNOWN,false,false,false);
  String name=advertisedName.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]","");
  if(name.contains("Z02"))return new ZnpDeviceProfile(Model.Z02,false,false,false);
  if(name.contains("N02"))return new ZnpDeviceProfile(Model.N02,false,false,false);
  return new ZnpDeviceProfile(Model.UNKNOWN,false,false,false);
 }
 public boolean canShowGoro(){return screenVerified;}
 public boolean canUseTouch(){return touchVerified;}
 public boolean canRouteAudio(){return audioVerified;}
}
