package ar.goro.ai.voice;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;

/** Explicit and reversible Android communication-audio route for the actual ZN02.
 * Never selects another Bluetooth headset or claims device audio from BLE/RCSP alone.
 */
public final class GoroZn02AudioRouter {
 private final AudioManager audio;
 private boolean active=false;
 private int previousMode=AudioManager.MODE_NORMAL;
 public GoroZn02AudioRouter(Context context){
  audio=(AudioManager)context.getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
 }
 public String activate(){
  if(audio==null)return "AudioManager no disponible.";
  if(Build.VERSION.SDK_INT<31)return "La selección de audio del ZN02 requiere Android 12 o superior.";
  AudioDeviceInfo matching=null;
  for(AudioDeviceInfo device:audio.getAvailableCommunicationDevices()){
   if(!device.isSink())continue;
   int type=device.getType();
   if(type!=AudioDeviceInfo.TYPE_BLUETOOTH_SCO&&type!=AudioDeviceInfo.TYPE_BLE_HEADSET)continue;
   CharSequence label=device.getProductName();
   String name=label==null?"":label.toString().toUpperCase(java.util.Locale.ROOT)
    .replaceAll("[^A-Z0-9]","");
   if(name.endsWith("ZNPZ02")||name.equals("Z02")||name.endsWith("Z02")){matching=device;break;}
  }
  if(matching==null)return "No se encontró ninguna salida de comunicación Bluetooth identificada como ZN02. No se modificó el audio.";
  try{
   if(!active)previousMode=audio.getMode();
   audio.setMode(AudioManager.MODE_IN_COMMUNICATION);
   if(audio.setCommunicationDevice(matching)){
    active=true;
    return "Android confirmó el enrutamiento de comunicación al ZN02.";
   }
   if(!active)audio.setMode(previousMode);
   return "Android no aceptó el enrutamiento de audio al ZN02.";
  }catch(SecurityException e){
   if(!active)audio.setMode(previousMode);
   return "Android denegó la selección de la salida de audio.";
  }catch(RuntimeException e){
   return "No se pudo seleccionar el audio del ZN02.";
  }
 }
 public String deactivate(){
  if(audio==null)return "AudioManager no disponible.";
  if(Build.VERSION.SDK_INT>=31&&active){
   try{audio.clearCommunicationDevice();audio.setMode(previousMode);}catch(RuntimeException ignored){}
  }
  active=false;
  return "Audio restablecido a las opciones del teléfono.";
 }
 public boolean isActive(){return active;}
 public void close(){deactivate();}
}
