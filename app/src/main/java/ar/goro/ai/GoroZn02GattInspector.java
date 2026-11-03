package ar.goro.ai;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
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
import java.util.List;

/**
 * GORO-only passive ZN02 BLE inventory, independent of the JieLi RCSP controller
 * and of the Cabezón vacuum project.
 *
 * It never writes BLE characteristics, starts OTA, attempts authentication, or
 * infers that audio, touchscreen and screen display work from a UUID alone.
 */
public final class GoroZn02GattInspector {
 public interface Listener {void onStatus(String status);}
 private final Context context;
 private final Listener listener;
 private final Handler main=new Handler(Looper.getMainLooper());
 private BluetoothLeScanner scanner;
 private BluetoothGatt gatt;
 private boolean scanning;
 private boolean closed;
 private final Runnable timeout=()->{
  if(scanning){stopScan();emit("ZN02 no observado en 12 segundos. Sin cambios en el dispositivo.");}
 };
 private final ScanCallback scanListener=new ScanCallback(){
  @Override public void onScanResult(int callbackType,ScanResult result){
   if(closed||!scanning||result==null)return;
   String name=result.getScanRecord()==null?null:result.getScanRecord().getDeviceName();
   BluetoothDevice device=result.getDevice();
   if(name==null&&device!=null){
    try{name=device.getName();}catch(SecurityException ignored){}
   }
   // An advertised name is a candidate only, never authenticated model identity.
   if(ZnpDeviceProfile.detect(name).model!=ZnpDeviceProfile.Model.N02)return;
   stopScan();
   emit("Se observó un anuncio N02. Identificación preliminar; leyendo servicios BLE sin escribir.");
   if(device==null)return;
   try{
    gatt=device.connectGatt(context,false,gattListener,BluetoothDevice.TRANSPORT_LE);
   }catch(SecurityException denied){emit("Android denegó leer servicios del N02.");}
    catch(RuntimeException error){emit("No se pudo abrir el canal GATT pasivo del N02.");}
  }
  @Override public void onScanFailed(int errorCode){
   stopScan();
   emit("Escaneo BLE detenido por Android. Código "+errorCode+".");
  }
 };
 private final BluetoothGattCallback gattListener=new BluetoothGattCallback(){
  @Override public void onConnectionStateChange(BluetoothGatt remote,int status,int newState){
   if(closed){closeGatt(remote);return;}
   if(status!=BluetoothGatt.GATT_SUCCESS){
    emit("El candidato N02 no completó GATT. Estado "+status+".");
    closeGatt(remote);
    return;
   }
   if(newState==BluetoothProfile.STATE_CONNECTED){
    emit("GATT conectado. Solicitando inventario de servicios.");
    try{
     if(!remote.discoverServices()){emit("El N02 no aceptó el inventario de servicios.");closeGatt(remote);}
    }catch(SecurityException denied){emit("Android rechazó descubrir servicios BLE.");closeGatt(remote);}
   }else if(newState==BluetoothProfile.STATE_DISCONNECTED){
    emit("GATT desconectado. No se modificó el dispositivo.");
    closeGatt(remote);
   }
  }
  @Override public void onServicesDiscovered(BluetoothGatt remote,int status){
   if(closed){closeGatt(remote);return;}
   if(status!=BluetoothGatt.GATT_SUCCESS){emit("No se pudieron descubrir los servicios BLE: "+status);closeGatt(remote);return;}
   List<BluetoothGattService> services=remote.getServices();
   StringBuilder report=new StringBuilder("ZN02 · inventario BLE: ");
   int count=services==null?0:services.size(), characteristics=0;
   if(services!=null)for(BluetoothGattService service:services){
    if(service.getCharacteristics()!=null)characteristics+=service.getCharacteristics().size();
   }
   report.append(count).append(" servicios, ").append(characteristics).append(" características.");
   if(services!=null){
    int shown=0;
    for(BluetoothGattService service:services){
     if(shown++>=8)break;
     report.append("\n").append(service.getUuid());
     report.append(" (").append(service.getCharacteristics()==null?0:service.getCharacteristics().size()).append(" características)");
    }
   }
   report.append("\nRCSP, visor, audio, micrófono y gestos NO verificados por este inventario.");
   emit(report.toString());
   closeGatt(remote);
  }
 };
 public GoroZn02GattInspector(Context context,Listener listener){
  this.context=context.getApplicationContext();this.listener=listener;
 }
 public String[] neededPermissions(){
  if(Build.VERSION.SDK_INT>=31){
   java.util.ArrayList<String> missing=new java.util.ArrayList<>();
   if(context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)!=PackageManager.PERMISSION_GRANTED)
    missing.add(Manifest.permission.BLUETOOTH_SCAN);
   if(context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)
    missing.add(Manifest.permission.BLUETOOTH_CONNECT);
   return missing.toArray(new String[0]);
  }
  if(context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)
   return new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
  return new String[0];
 }
 public void scan(){
  if(closed){emit("Inspector BLE cerrado.");return;}
  if(neededPermissions().length>0){emit("Faltan permisos Android para el inventario BLE.");return;}
  try{
   BluetoothManager manager=(BluetoothManager)context.getSystemService(Context.BLUETOOTH_SERVICE);
   BluetoothAdapter adapter=manager==null?null:manager.getAdapter();
   if(adapter==null||!adapter.isEnabled()){emit("Activá Bluetooth antes de buscar el N02.");return;}
   scanner=adapter.getBluetoothLeScanner();
   if(scanner==null){emit("Android no ofrece el escáner BLE.");return;}
   if(scanning){emit("Ya se está leyendo el entorno BLE.");return;}
   scanning=true;scanner.startScan(scanListener);
   main.postDelayed(timeout,12000);
   emit("Buscando N02 por Bluetooth LE, solo lectura y sin órdenes al firmware.");
  }catch(SecurityException denied){scanning=false;emit("Android no autorizó el escaneo BLE.");}
   catch(RuntimeException error){scanning=false;emit("No se pudo iniciar el inventario BLE.");}
 }
 private void stopScan(){
  main.removeCallbacks(timeout);
  if(!scanning)return;
  scanning=false;
  BluetoothLeScanner current=scanner;
  if(current!=null){try{current.stopScan(scanListener);}catch(RuntimeException ignored){}}
 }
 private void closeGatt(BluetoothGatt remote){
  if(remote==null)return;
  try{remote.close();}catch(RuntimeException ignored){}
  if(gatt==remote)gatt=null;
 }
 private void emit(String detail){
  if(listener!=null&&!closed)main.post(()->{if(!closed)listener.onStatus(detail);});
 }
 public void close(){
  closed=true;stopScan();main.removeCallbacksAndMessages(null);
  BluetoothGatt old=gatt;gatt=null;
  if(old!=null){try{old.disconnect();}catch(RuntimeException ignored){}closeGatt(old);}
 }
}
