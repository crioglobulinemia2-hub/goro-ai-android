package ar.goro.ai;
import java.util.Locale;
/** Explicit capabilities: unknown is not equivalent to supported. */
public final class ZnpDeviceProfile {
 public enum Model { Z02, N02, AN02, AZ02, AZ01, JIELI_RCSP_UNVERIFIED, UNKNOWN }
 public final Model model;
 public final boolean screenVerified;
 public final boolean audioVerified;
 public final boolean touchVerified;
 private ZnpDeviceProfile(Model m,boolean s,boolean a,boolean t){model=m;screenVerified=s;audioVerified=a;touchVerified=t;}
 public static ZnpDeviceProfile detect(String advertisedName){return detect(advertisedName,false);}
 public static ZnpDeviceProfile detect(String advertisedName,boolean rcspServiceAdvertised){
  if(advertisedName!=null){
   String name=advertisedName.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]","");
   if(name.contains("AN02"))return new ZnpDeviceProfile(Model.AN02,false,false,false);
   if(name.contains("AZ02"))return new ZnpDeviceProfile(Model.AZ02,false,false,false);
   if(name.contains("AZ01"))return new ZnpDeviceProfile(Model.AZ01,false,false,false);
   if(name.contains("Z02"))return new ZnpDeviceProfile(Model.Z02,false,false,false);
   if(name.contains("N02"))return new ZnpDeviceProfile(Model.N02,false,false,false);
   if(name.contains("JIELI")||name.contains("BR35")||name.contains("BAGIBAGI"))
    return new ZnpDeviceProfile(Model.JIELI_RCSP_UNVERIFIED,false,false,false);
  }
  if(rcspServiceAdvertised)return new ZnpDeviceProfile(Model.JIELI_RCSP_UNVERIFIED,false,false,false);
  return new ZnpDeviceProfile(Model.UNKNOWN,false,false,false);
 }
 public boolean canShowGoro(){return screenVerified;}
 public boolean canUseTouch(){return touchVerified;}
 public boolean canRouteAudio(){return audioVerified;}
}
