package ar.goro.ai;

import java.nio.charset.StandardCharsets;

/** BLE local-name fallback; an advertisement never proves RCSP authentication. */
final class ZnpAdvertisement {
 static ZnpDeviceProfile profile(String cachedName,byte[] advertisement){
  // The current advertisement is newer than Android's cached remote name.
  // Prefer its complete/shortened local-name AD structure to avoid identifying
  // a replacement N02 as a stale Z02 (or vice versa).
  ZnpDeviceProfile advertised=ZnpDeviceProfile.detect(localName(advertisement));
  if(advertised.isCandidate())return advertised;
  ZnpDeviceProfile cached=ZnpDeviceProfile.detect(cachedName);
  return cached.isCandidate()?cached:advertised;
 }
 static String localName(byte[] data){
  if(data==null)return null;
  String shortened=null;
  for(int offset=0;offset<data.length;){
   int length=data[offset]&255;
   if(length==0)break;
   if(length>data.length-offset-1)break;
   int type=data[offset+1]&255;
   if((type==8||type==9)&&length>1){
    String name=new String(data,offset+2,length-1,StandardCharsets.UTF_8);
    if(type==9)return name;
    shortened=name;
   }
   offset+=length+1;
  }
  return shortened;
 }
 private ZnpAdvertisement(){}
}
