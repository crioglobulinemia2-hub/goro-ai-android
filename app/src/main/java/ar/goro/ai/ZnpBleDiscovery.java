package ar.goro.ai;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * User-initiated read-only BLE scan and GATT service inventory for ZNP Z02/N02.
 * Only standard CCCD subscription is written; no RCSP commands, authentication, firmware operations or MAC logging.
 * Discovery does NOT establish screen, touch, microphone or audio support.
 */
public final class ZnpBleDiscovery {
 public interface Listener {void onMessage(String message); }
 private final Activity activity;
 private final Listener listener;
 private final Handler main=new Handler(Looper.getMainLooper());
 private BluetoothLeScanner scanner;
 private ScanCallback scanCallback;
 private BluetoothGatt gatt;
 private boolean active;
 private boolean found;
 private String advertisedModel="desconocido";
 private static final UUID RCSP_SERVICE=UUID.fromString("0000ae00-0000-1000-8000-00805f9b34fb");
 private static final UUID RCSP_NOTIFY=UUID.fromString("0000ae02-0000-1000-8000-00805f9b34fb");
 private static final UUID CCCD=UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
 private final Runnable timeout=()->{
   if(active){
     stopScan();
     if(!found) emit("Escaneo finalizado: ningún Z02/N02 anunciado por Bluetooth LE.");
   }
 };

 public ZnpBleDiscovery(Activity activity,Listener listener){this.activity=activity;this.listener=listener;}
 public String[] neededPermissions(){
  if(Build.VERSION.SDK_INT>=31){
   List<String> requested=new ArrayList<>();
   if(activity.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)!=PackageManager.PERMISSION_GRANTED)
    requested.add(Manifest.permission.BLUETOOTH_SCAN);
   if(activity.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)
    requested.add(Manifest.permission.BLUETOOTH_CONNECT);
   return requested.toArray(new String[0]);
  }
  if(activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)
   return new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
  return new String[0];
 }
 @SuppressLint("MissingPermission")
 public void begin(){
  if(active){emit("El escaneo ya está activo.");return;}
  if(neededPermissions().length>0){emit("Faltan permisos Bluetooth para inspeccionar el estuche.");return;}
  BluetoothManager manager=(BluetoothManager)activity.getSystemService(Context.BLUETOOTH_SERVICE);
  BluetoothAdapter adapter=manager==null?null:manager.getAdapter();
  if(adapter==null){emit("Bluetooth LE no está disponible en este dispositivo.");return;}
  if(!adapter.isEnabled()){emit("Activá Bluetooth en Android para identificar el ZNP.");return;}
  scanner=adapter.getBluetoothLeScanner();
  if(scanner==null){emit("El escáner Bluetooth no está disponible.");return;}
  found=false;active=true;advertisedModel="desconocido";
  emit("Buscando Z02/N02. Solo se leerán servicios GATT públicos.");
  scanCallback=new ScanCallback(){
   @Override public void onScanResult(int callbackType,ScanResult result){
    if(!active||found||result==null)return;
    String name=result.getScanRecord()==null?null:result.getScanRecord().getDeviceName();
    ZnpDeviceProfile profile=ZnpDeviceProfile.detect(name);
    if(profile.model==ZnpDeviceProfile.Model.UNKNOWN)return;
    found=true;
    advertisedModel=profile.model.name();
    stopScan();
    emit("Anuncio BLE compatible con el nombre "+advertisedModel+". Identidad y protocolo sin verificar.");
    inspect(result.getDevice());
   }
   @Override public void onScanFailed(int errorCode){stopScan();emit("No pudo iniciarse el escaneo BLE (código "+errorCode+").");}
  };
  try{scanner.startScan(scanCallback);main.postDelayed(timeout,12000);}
  catch(SecurityException e){stopScan();emit("Android denegó permisos Bluetooth.");}
  catch(RuntimeException e){stopScan();emit("No fue posible iniciar el escaneo BLE.");}
 }
 @SuppressLint("MissingPermission")
 private void inspect(BluetoothDevice device){
  if(device==null){emit("Anuncio BLE sin dispositivo accesible.");return;}
  try{
   gatt=device.connectGatt(activity,false,new BluetoothGattCallback(){
    @Override public void onConnectionStateChange(BluetoothGatt session,int status,int state){
     if(status!=BluetoothGatt.GATT_SUCCESS){emit("No se pudo inspeccionar GATT. Estado "+status+".");closeGatt();return;}
     if(state==BluetoothProfile.STATE_CONNECTED){
      emit("Conexión GATT de inspección establecida. Leyendo servicios...");
      try{if(!session.discoverServices()){emit("No se pudo descubrir servicios.");closeGatt();}}
      catch(SecurityException e){emit("Falta permiso para descubrir servicios GATT.");closeGatt();}
     }else if(state==BluetoothProfile.STATE_DISCONNECTED){closeGatt();}
    }
    @Override public void onServicesDiscovered(BluetoothGatt session,int status){
     if(status!=BluetoothGatt.GATT_SUCCESS){emit("Descubrimiento GATT falló: "+status+".");closeGatt();return;}
     List<BluetoothGattService> services=session.getServices();
     List<UUID> ids=new ArrayList<>();
     for(BluetoothGattService service:services)ids.add(service.getUuid());
     List<String> labels=ZnpServiceInventory.summarize(ids);
     StringBuilder info=new StringBuilder("Modelo anunciado: ").append(advertisedModel);
     info.append("\nServicios GATT: ").append(ids.size());
     for(int i=0;i<ids.size();i++)info.append("\n").append(labels.get(i)).append(": ").append(ids.get(i));
     info.append("\nVisor, audio y touch: NO VERIFICADOS.");
     BluetoothGattService rcsp=session.getService(RCSP_SERVICE);
     BluetoothGattCharacteristic notify=rcsp==null?null:rcsp.getCharacteristic(RCSP_NOTIFY);
     if(notify==null){info.append("\nRCSP AE00/AE02: no encontrado.");emit(info.toString());closeGatt();return;}
     info.append("\nRCSP AE00/AE02: encontrado.");
     emit(info.toString());
     try{
      if(!session.setCharacteristicNotification(notify,true)){emit("No se pudo activar la suscripción local RCSP.");closeGatt();return;}
      BluetoothGattDescriptor descriptor=notify.getDescriptor(CCCD);
      if(descriptor==null){emit("AE02 no expone CCCD; no se puede suscribir.");closeGatt();return;}
      if(Build.VERSION.SDK_INT>=33){
       int result=session.writeDescriptor(descriptor,BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
       if(result!=BluetoothGatt.GATT_SUCCESS){emit("No se pudo escribir CCCD: "+result);closeGatt();}
      }else{
       descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
       if(!session.writeDescriptor(descriptor)){emit("No se pudo solicitar CCCD.");closeGatt();}
      }
     }catch(SecurityException e){emit("Android denegó la suscripción de notificaciones.");closeGatt();}
    }
    @Override public void onDescriptorWrite(BluetoothGatt session,BluetoothGattDescriptor descriptor,int status){
     if(CCCD.equals(descriptor.getUuid()))emit(status==BluetoothGatt.GATT_SUCCESS?"Z02 conectado · notificaciones RCSP AE02 activas. Autenticación pendiente; no se enviaron comandos.":"Falló la suscripción RCSP AE02: "+status);
    }
    @Override public void onCharacteristicChanged(BluetoothGatt session,BluetoothGattCharacteristic characteristic,byte[] value){
     if(RCSP_NOTIFY.equals(characteristic.getUuid()))emit("Z02 · notificación RCSP recibida ("+(value==null?0:value.length)+" bytes). Sin interpretar ni ejecutar acciones.");
    }
   },BluetoothDevice.TRANSPORT_LE);
  }catch(SecurityException e){emit("Android impidió la inspección GATT.");closeGatt();}
   catch(RuntimeException e){emit("No fue posible abrir una sesión GATT.");closeGatt();}
 }
 @SuppressLint("MissingPermission")
 public void stopScan(){
  active=false;main.removeCallbacks(timeout);
  BluetoothLeScanner s=scanner;ScanCallback callback=scanCallback;
  scanner=null;scanCallback=null;
  if(s!=null&&callback!=null)try{s.stopScan(callback);}catch(Exception ignored){}
 }
 @SuppressLint("MissingPermission")
 private void closeGatt(){
  BluetoothGatt current=gatt;gatt=null;
  if(current!=null)try{current.close();}catch(Exception ignored){}
 }
 public void close(){stopScan();closeGatt();}
 private void emit(String msg){main.post(()->listener.onMessage(msg));}
}