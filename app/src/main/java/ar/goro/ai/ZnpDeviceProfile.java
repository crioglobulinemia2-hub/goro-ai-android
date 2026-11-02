package ar.goro.ai;
import java.util.Locale;
/** GORO's dedicated device identity gate. A Z02/Cabezón profile is never accepted as N02. */
public final class ZnpDeviceProfile {
 public enum Model { N02, UNKNOWN }
 public final Model model;
 public final boolean screenVerified;
 public final boolean audioVerified;
 public final boolean touchVerified;
 private ZnpDeviceProfile(Model model){this.model=model;screenVerified=false;audioVerified=false;touchVerified=false;}
 public static ZnpDeviceProfile detect(String advertisedName){return detect(advertisedName,false);}
 public static ZnpDeviceProfile detect(String advertisedName,boolean rcspServiceAdvertised){
  if(advertisedName==null)return new ZnpDeviceProfile(Model.UNKNOWN);
  String normalized=advertisedName.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]","");
  if(normalized.endsWith("N02"))return new ZnpDeviceProfile(Model.N02);
  // A generic RCSP UUID is not enough to identify N02 or prove its capabilities.
  return new ZnpDeviceProfile(Model.UNKNOWN);
 }
 public boolean canShowGoro(){return screenVerified;}
 public boolean canUseTouch(){return touchVerified;}
 public boolean canRouteAudio(){return audioVerified;}
}
