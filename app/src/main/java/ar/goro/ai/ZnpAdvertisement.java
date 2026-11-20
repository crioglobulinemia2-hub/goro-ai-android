package ar.goro.ai;

import java.nio.charset.StandardCharsets;

/** BLE local-name fallback; an advertisement never proves RCSP authentication. */
final class ZnpAdvertisement {
 static ZnpDeviceProfile profile(String cachedName,byte[] advertisement){
  ZnpDeviceProfile cached=ZnpDeviceProfile.detect(cachedName);
  if(cached.isCandidate())return cached;
  return ZnpDeviceProfile.detect(localName(advertisement));
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
