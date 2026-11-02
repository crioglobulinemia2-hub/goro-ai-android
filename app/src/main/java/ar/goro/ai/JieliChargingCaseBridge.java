package ar.goro.ai;

import android.bluetooth.BluetoothDevice;
import com.jieli.bluetooth.bean.BleScanMessage;
import com.jieli.bluetooth.bean.base.BaseError;
import com.jieli.bluetooth.bean.charging_case.ChargingCaseInfo;
import com.jieli.bluetooth.bean.settings.v0.ScreenInfo;
import com.jieli.bluetooth.bean.settings.v0.SettingFunction;
import com.jieli.bluetooth.constant.BluetoothConstant;
import com.jieli.bluetooth.constant.JL_DeviceType;
import com.jieli.bluetooth.constant.StateCode;
import com.jieli.bluetooth.impl.rcsp.RCSPController;
import com.jieli.bluetooth.impl.rcsp.charging_case.ChargingCaseOpImpl;
import com.jieli.bluetooth.interfaces.rcsp.callback.BTRcspEventCallback;
import com.jieli.bluetooth.interfaces.rcsp.callback.OnRcspActionCallback;
import com.jieli.bluetooth.interfaces.rcsp.charging_case.OnChargingCaseListener;
import java.util.List;

/**
 * Official JieLi RCSP charging-case bridge.
 * It scans only after an explicit user action and never assumes a device feature exists.
 */
public final class JieliChargingCaseBridge {
 public interface Listener {void onStatus(String status);}
 private final RCSPController controller;
 private final Listener listener;
 private final BTRcspEventCallback eventCallback;
 private ChargingCaseOpImpl caseOp;
 private BluetoothDevice target;
 private boolean scanning;
 private boolean connected;
 private boolean closed;
 private final OnChargingCaseListener caseListener=new OnChargingCaseListener(){
  @Override public void onInit(BluetoothDevice device,int state){
   if(!isTarget(device))return;
   emit(state==0?"ZNP · inicialización RCSP de estuche completada.":"ZNP · error de inicialización RCSP: "+state);
  }
  @Override public void onChargingCaseInfoChange(BluetoothDevice device,int function,ChargingCaseInfo info){
   if(!isTarget(device)||info==null)return;
   if(function==ChargingCaseInfo.FUNC_FLASHLIGHT)
    emit("ZNP · luz del estuche: "+(info.isFlashlightOn()?"encendida":"apagada"));
   else if(function==ChargingCaseInfo.FUNC_BRIGHTNESS)
    emit("ZNP · brillo de pantalla: "+info.getBrightness()+"%");
  }
  @Override public void onChargingCaseEvent(BluetoothDevice device,SettingFunction function){
   if(isTarget(device))emit("ZNP · evento RCSP de estuche recibido.");
  }
 };
 public JieliChargingCaseBridge(Listener listener){
  this.listener=listener;
  controller=RCSPController.getInstance();
  eventCallback=new BTRcspEventCallback(){
   @Override public void onDiscoveryStatus(boolean ble,boolean start){
    if(!ble)return;
    scanning=start;
    if(!start&&!connected)emit("Búsqueda RCSP finalizada: no se conectó un estuche Z02/N02 compatible.");
   }
   @Override public void onDiscovery(BluetoothDevice device,BleScanMessage message){
    if(closed||!scanning||device==null)return;
    boolean chargingCase=message!=null&&message.getDeviceType()==JL_DeviceType.JL_DEVICE_TYPE_CHARGING_BIN;
    String name=null;
    try{name=device.getName();}catch(SecurityException ignored){}
    String normalized=name==null?"":name.toUpperCase(java.util.Locale.ROOT);
    boolean namedZnp=normalized.contains("Z02")||normalized.contains("N02")||normalized.contains("ZNP");
    if(!chargingCase&&!namedZnp)return;
    target=device;
    controller.stopScan();
    scanning=false;
    emit("ZNP detectado: "+(name==null?"nombre no anunciado":name)+". Iniciando conexión RCSP BLE…");
    try{controller.connectDevice(device,BluetoothConstant.PROTOCOL_TYPE_BLE);}
    catch(RuntimeException error){emit("El SDK JieLi no pudo iniciar la conexión BLE RCSP: "+error.getClass().getSimpleName());}
   }
   @Override public void onConnection(BluetoothDevice device,int status){
    if(!isTarget(device))return;
    if(status==StateCode.CONNECTION_OK){
     connected=true;
     caseOp=ChargingCaseOpImpl.instance(controller.getRcspOp());
     caseOp.addOnChargingCaseListener(caseListener);
     emit("ZNP conectado por RCSP. Consultando pantalla y estado de luz…");
     caseOp.getChargingCaseInfo(device);
     caseOp.readScreenInfo(device,new OnRcspActionCallback<ScreenInfo>(){
      @Override public void onSuccess(BluetoothDevice d,ScreenInfo info){
       if(isTarget(d))emit("Pantalla ZNP verificada por SDK: "+String.valueOf(info));
      }
      @Override public void onError(BluetoothDevice d,BaseError error){
       if(isTarget(d))emit("La conexión existe, pero el SDK no pudo leer la pantalla: "+error.getMessage());
      }
     });
     readFlashlight();
     caseOp.getBrightness(device,new OnRcspActionCallback<Integer>(){
      @Override public void onSuccess(BluetoothDevice d,Integer value){if(isTarget(d))emit("Brillo de pantalla confirmado: "+value+"%.");}
      @Override public void onError(BluetoothDevice d,BaseError error){if(isTarget(d))emit("Brillo no disponible: "+error.getMessage());}
     });
    }else if(status==StateCode.CONNECTION_DISCONNECT||status==StateCode.CONNECTION_FAILED){
     connected=false;
     emit("ZNP desconectado o conexión RCSP fallida (estado "+status+").");
    }else if(status==StateCode.CONNECTION_CONNECTING){
     emit("Conectando ZNP por Bluetooth LE…");
    }
   }
  };
 }
 public synchronized void startScan(){
  if(closed){emit("Puente JieLi cerrado.");return;}
  if(!RCSPController.isInit()){emit("El SDK JieLi no está inicializado.");return;}
  if(scanning){emit("La búsqueda ZNP ya está activa.");return;}
  target=null;connected=false;
  controller.addBTRcspEventCallback(eventCallback);
  emit("Buscando Z02/N02 mediante el escáner RCSP oficial de JieLi…");
  scanning=controller.startBleScan(20000);
  if(!scanning)emit("El SDK JieLi no pudo iniciar el escaneo. Revisá Bluetooth y permisos.");
 }
 public synchronized void setFlashlight(boolean enabled){
  if(closed||!connected||target==null||caseOp==null){
   emit("No hay estuche ZNP conectado por RCSP. No se envió ningún comando.");
   return;
  }
  emit(enabled?"Solicitando encendido de luz al ZNP…":"Solicitando apagado de luz al ZNP…");
  caseOp.setFlashlightState(target,enabled,new OnRcspActionCallback<Boolean>(){
   @Override public void onSuccess(BluetoothDevice device,Boolean result){
    if(!isTarget(device))return;
    emit(Boolean.TRUE.equals(result)?"Comando de luz aceptado por el ZNP. Verificando estado…":"El ZNP no confirmó el cambio de luz.");
    readFlashlight();
   }
   @Override public void onError(BluetoothDevice device,BaseError error){
    if(isTarget(device))emit("El ZNP rechazó el comando de luz: "+error.getMessage()+" (código "+error.getSubCode()+").");
   }
  });
 }
 public synchronized void readFlashlight(){
  if(!connected||target==null||caseOp==null){emit("Luz no consultada: ZNP sin conexión RCSP.");return;}
  caseOp.getFlashlightState(target,new OnRcspActionCallback<Boolean>(){
   @Override public void onSuccess(BluetoothDevice device,Boolean on){
    if(isTarget(device))emit("Estado real de luz del estuche: "+(Boolean.TRUE.equals(on)?"ENCENDIDA":"APAGADA")+".");
   }
   @Override public void onError(BluetoothDevice device,BaseError error){
    if(isTarget(device))emit("La luz no está soportada o no respondió: "+error.getMessage()+" (código "+error.getSubCode()+").");
   }
  });
 }
 private boolean isTarget(BluetoothDevice device){
  return target!=null&&device!=null&&target.getAddress()!=null&&target.getAddress().equals(device.getAddress());
 }
 private void emit(String value){if(listener!=null&&!closed)listener.onStatus(value);}
 public synchronized void close(){
  if(closed)return;closed=true;
  if(scanning)controller.stopScan();
  controller.removeBTRcspEventCallback(eventCallback);
  if(caseOp!=null)caseOp.removeOnChargingCaseListener(caseListener);
  scanning=false;connected=false;target=null;caseOp=null;
 }
}
