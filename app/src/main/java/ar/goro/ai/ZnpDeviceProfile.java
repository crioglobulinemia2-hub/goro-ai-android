package ar.goro.ai;

import java.util.Locale;

/**
 * BLE advertisement classification is candidate discovery only. It never proves
 * model identity, RCSP support, microphone, screen, touch or audio capabilities.
 */
public final class ZnpDeviceProfile {
 public enum Model { Z01_CANDIDATE, Z02_CANDIDATE, N02_CANDIDATE, UNKNOWN }
 public final Model model;
 public final boolean screenVerified=false;
 public final boolean audioVerified=false;
 public final boolean touchVerified=false;
 private ZnpDeviceProfile(Model model){this.model=model;}
 public static ZnpDeviceProfile detect(String advertisedName){return detect(advertisedName,false);}
 public static ZnpDeviceProfile detect(String advertisedName,boolean ignoredRcspHint){
  if(advertisedName==null)return new ZnpDeviceProfile(Model.UNKNOWN);
  String normalized=advertisedName.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]","");
  if(normalized.endsWith("ZNPZ01")||normalized.equals("Z01")||normalized.endsWith("Z01"))
   return new ZnpDeviceProfile(Model.Z01_CANDIDATE);
  if(normalized.endsWith("ZNPZ02")||normalized.equals("Z02")||normalized.endsWith("Z02"))
   return new ZnpDeviceProfile(Model.Z02_CANDIDATE);
  if(normalized.endsWith("N02")||normalized.equals("ZN02"))
   return new ZnpDeviceProfile(Model.N02_CANDIDATE);
  return new ZnpDeviceProfile(Model.UNKNOWN);
 }
 public boolean isCandidate(){return model!=Model.UNKNOWN;}
 /** Converts advertised aliases such as AN02/AZ02/AZ01 to canonical model codes. */
 public static String canonicalModel(String value){
  ZnpDeviceProfile profile=detect(value);
  switch(profile.model){
   case Z01_CANDIDATE:return "Z01";
   case Z02_CANDIDATE:return "Z02";
   case N02_CANDIDATE:return "N02";
   default:return null;
  }
 }
 /** The current RCSP charging-case implementation is validated only for Z02. */
 public boolean isRcspCandidate(){return model==Model.Z02_CANDIDATE;}
 public String modelLabel(){
  switch(model){
   case Z01_CANDIDATE:return "ZNP Z01";
   case Z02_CANDIDATE:return "ZNP Z02";
   case N02_CANDIDATE:return "ZNP N02";
   default:return "ZNP desconocido";
  }
 }
 public String animationProfile(){
  switch(model){
   case Z01_CANDIDATE:return "z01";
   case Z02_CANDIDATE:return "z02";
   case N02_CANDIDATE:return "n02";
   default:return "unknown";
  }
 }
 public boolean canShowGoro(){return screenVerified;}
 public boolean canUseTouch(){return touchVerified;}
 public boolean canRouteAudio(){return audioVerified;}
}
