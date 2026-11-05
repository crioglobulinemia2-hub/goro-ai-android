package ar.goro.ai;

import java.util.Locale;

/**
 * Name-based BLE candidate classification only. It is never proof of model,
 * vendor protocol, microphone, screen or audio capabilities.
 */
public final class ZnpDeviceProfile {
 public enum Model { Z02_CANDIDATE, N02_CANDIDATE, UNKNOWN }
 public final Model model;
 public final boolean screenVerified=false;
 public final boolean audioVerified=false;
 public final boolean touchVerified=false;
 private ZnpDeviceProfile(Model model){this.model=model;}
 public static ZnpDeviceProfile detect(String advertisedName){return detect(advertisedName,false);}
 public static ZnpDeviceProfile detect(String advertisedName,boolean ignoredRcspHint){
  if(advertisedName==null)return new ZnpDeviceProfile(Model.UNKNOWN);
  String normalized=advertisedName.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]","");
  if(normalized.endsWith("ZNPZ02")||normalized.equals("Z02")||normalized.endsWith("Z02"))
   return new ZnpDeviceProfile(Model.Z02_CANDIDATE);
  if(normalized.endsWith("N02")||normalized.equals("ZN02"))
   return new ZnpDeviceProfile(Model.N02_CANDIDATE);
  return new ZnpDeviceProfile(Model.UNKNOWN);
 }
 public boolean isCandidate(){return model==Model.Z02_CANDIDATE||model==Model.N02_CANDIDATE;}
 public boolean canShowGoro(){return screenVerified;}
 public boolean canUseTouch(){return touchVerified;}
 public boolean canRouteAudio(){return audioVerified;}
}
