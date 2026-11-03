package ar.goro.ai;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import com.jieli.bluetooth.bean.charging_case.ChargingCaseInfo;
import com.jieli.bluetooth.bean.file.op.CreateFileParam;
import com.jieli.bluetooth.bean.settings.v0.SDKMessage;
import com.jieli.bluetooth.impl.rcsp.file.FileOpImpl;
import com.jieli.bluetooth.interfaces.rcsp.callback.OnTaskStateListener;
import com.jieli.bluetooth.utils.CryptoUtil;
import com.jieli.filebrowse.bean.SDCardBean;
import com.jieli.lib.gif.GifConverter;
import com.jieli.lib.gif.model.GifConvertResult;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.jieli.bluetooth.bean.BleScanMessage;
import com.jieli.bluetooth.bean.BluetoothOption;
import com.jieli.bluetooth.bean.record.RecordParam;
import com.jieli.bluetooth.bean.record.RecordState;
import com.jieli.bluetooth.constant.Constants;
import com.jieli.bluetooth.impl.rcsp.record.RecordOpImpl;
import com.jieli.bluetooth.interfaces.rcsp.record.OnRecordStateCallback;
import com.jieli.jl_audio_decode.opus.OpusManager;
import com.jieli.jl_audio_decode.opus.model.OpusOption;
import com.jieli.jl_audio_decode.callback.OnDecodeStreamCallback;
import java.io.ByteArrayOutputStream;
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
 * GORO-owned ZN02 controller. Uses the vendor RCSP transport only; no Cabezón,
 * Tuya, vacuum-control, or cross-app state is imported into this application.
 * A device must identify itself as N02 before the controller will connect.
 */
public final class GoroZn02Controller {
 public interface Listener { void onStatus(String status); default void onMicrophonePcm(byte[] pcm16Mono){} default void onAudioTarget(BluetoothDevice audioTarget){} }
 private final Context context;
 private final ExecutorService resourceExecutor=Executors.newSingleThreadExecutor();
 private boolean uploadingResource;
 private RCSPController controller;
 private final Listener listener;
 private final Handler mainHandler=new Handler(Looper.getMainLooper());
 private final BTRcspEventCallback eventCallback;
 private ChargingCaseOpImpl caseOp;
 private BluetoothDevice target;
 private boolean scanning;
 private boolean connected;
 private volatile boolean vendorProtocolVerified=false;
 private boolean closed;
 private final BluetoothBondSession bondSession;
 private long connectionEpoch;
 private long voiceEpoch;
 private RecordOpImpl recorder;
 private OpusManager decoder;
 private OnRecordStateCallback recordCallback;
 private ByteArrayOutputStream microphonePcm=new ByteArrayOutputStream();
 private boolean recording;
 private boolean draining;
 private Runnable authPoll,authTimeout,voiceTimeout,drainTimeout,drainFinishTimeout;
 private final OnChargingCaseListener caseListener=new OnChargingCaseListener(){
  @Override public void onInit(BluetoothDevice device,int state){
   if(!isTarget(device))return;
   emit(state==0?"ZN02 · inicialización Bluetooth completada.":"ZN02 · error de inicialización: "+state);
  }
  @Override public void onChargingCaseInfoChange(BluetoothDevice device,int function,ChargingCaseInfo info){
   if(!isTarget(device)||info==null)return;
   if(function==ChargingCaseInfo.FUNC_FLASHLIGHT)
    emit("ZN02 · luz: "+(info.isFlashlightOn()?"encendida":"apagada"));
   else if(function==ChargingCaseInfo.FUNC_BRIGHTNESS)
    emit("ZN02 · brillo de pantalla: "+info.getBrightness()+"%");
  }
  @Override public void onChargingCaseEvent(BluetoothDevice device,SettingFunction function){
   if(isTarget(device))emit("ZN02 · evento de dispositivo recibido.");
  }
 };
 public GoroZn02Controller(Context context,Listener listener){
  this.context=context.getApplicationContext();
  this.listener=listener;
  this.bondSession=new BluetoothBondSession(context);
  controller=null;
  eventCallback=new BTRcspEventCallback(){
   @Override public void onDiscoveryStatus(boolean ble,boolean start){
    if(!ble)return;
    scanning=start;
    if(!start&&!connected&&target==null)emit("Búsqueda finalizada: no se encontró el estuche ZN02.");
   }
   @Override public void onDiscovery(BluetoothDevice device,BleScanMessage message){
    if(closed||!scanning||device==null)return;
    String name=null;
    try{name=device.getName();}catch(SecurityException ignored){}
    ZnpDeviceProfile profile=ZnpDeviceProfile.detect(name);
    if(!profile.isCandidate())return;
    target=device;
    controller.stopScan();
    scanning=false;
    emit("El anuncio Bluetooth coincide con ZN02. Verificando el protocolo antes de habilitar controles…");
    try{controller.connectDevice(device);
    catch(RuntimeException error){emit("El SDK JieLi no pudo iniciar la conexión BLE RCSP: "+error.getClass().getSimpleName());}
   }
   @Override public void onConnection(BluetoothDevice device,int status){
    if(!isTarget(device))return;
    if(status==StateCode.CONNECTION_OK){
     connected=true;
     vendorProtocolVerified=false;
     caseOp=ChargingCaseOpImpl.instance(controller.getRcspOp());
     caseOp.addOnChargingCaseListener(caseListener);
     emit("ZN02 conectado. Consultando pantalla y estado de iluminación…");
     caseOp.getChargingCaseInfo(device);
     caseOp.readSDKMessage(device,new OnRcspActionCallback<SDKMessage>(){
      @Override public void onSuccess(BluetoothDevice d,SDKMessage message){
       if(!isTarget(d)||message==null)return;
       int chip=message.getChip();
       ChargingCaseInfo info=caseOp.getChargingCaseInfo(d);
       if(info!=null)emit("ZN02 · chip "+chip+" · pantalla "+info.getScreenWidth()+"×"+info.getScreenHeight()+".");
       if(chip==SDKMessage.CHIP_701N||chip==SDKMessage.CHIP_707N){
        vendorProtocolVerified=true;
        emit("Protocolo de estuche JieLi y chip "+chip+" confirmados. Controles habilitados.");
        readFlashlight();
       }else{
        vendorProtocolVerified=false;
        emit("Chip desconocido: los comandos de luz y pantalla permanecen bloqueados.");
       }
      }
      @Override public void onError(BluetoothDevice d,BaseError error){
       if(isTarget(d))emit("No se pudo identificar el chip del estuche: "+error.getMessage());
      }
     });
     caseOp.readScreenInfo(device,new OnRcspActionCallback<ScreenInfo>(){
      @Override public void onSuccess(BluetoothDevice d,ScreenInfo info){
       if(isTarget(d))emit("Pantalla del ZN02 verificada por SDK: "+String.valueOf(info));
      }
      @Override public void onError(BluetoothDevice d,BaseError error){
       if(isTarget(d))emit("La conexión existe, pero el SDK no pudo leer la pantalla: "+error.getMessage());
      }
     });
     caseOp.getBrightness(device,new OnRcspActionCallback<Integer>(){
      @Override public void onSuccess(BluetoothDevice d,Integer value){if(isTarget(d))emit("Brillo de pantalla confirmado: "+value+"%.");}
      @Override public void onError(BluetoothDevice d,BaseError error){if(isTarget(d))emit("Brillo no disponible: "+error.getMessage());}
     });
    }else if(status==StateCode.CONNECTION_DISCONNECT||status==StateCode.CONNECTION_FAILED){
     connected=false;vendorProtocolVerified=false;
     emit("ZN02 desconectado o conexión fallida (estado "+status+").");
    }else if(status==StateCode.CONNECTION_CONNECTING){
     emit("Conectando ZN02 por Bluetooth LE…");
    }
   }
  };
 }
 public String[] neededPermissions(){
  if(Build.VERSION.SDK_INT>=31){
   java.util.ArrayList<String> missing=new java.util.ArrayList<>();
   if(context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)!=PackageManager.PERMISSION_GRANTED)missing.add(Manifest.permission.BLUETOOTH_SCAN);
   if(context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)missing.add(Manifest.permission.BLUETOOTH_CONNECT);
   return missing.toArray(new String[0]);
  }
  if(context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)
   return new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
  return new String[0];
 }
 public synchronized void startScan(){
  if(closed){emit("Controlador ZN02 cerrado.");return;}
  if(neededPermissions().length>0){emit("Faltan permisos Bluetooth para conectar el ZN02.");return;}
  try{
   BluetoothManager manager=(BluetoothManager)context.getSystemService(Context.BLUETOOTH_SERVICE);
   BluetoothAdapter adapter=manager==null?null:manager.getAdapter();
   if(adapter==null){emit("El dispositivo Android no tiene Bluetooth; GORO sigue funcionando sin ZN02.");return;}
   if(!adapter.isEnabled()){emit("Bluetooth está apagado. Activá Bluetooth para conectar el ZN02.");return;}
   if(!RCSPController.isInit()){
    BluetoothOption option=BluetoothOption.createDefaultOption()
      .setUseMultiDevice(true)
      .setPriority(0)
      .setMandatoryUseBLE(false)
      .setMtu(509)
      .setUseDeviceAuth(true)
      .setBleScanMode(2);
    RCSPController.init(context,option);
   }
   controller=RCSPController.getInstance();
   if(controller==null||!RCSPController.isInit()){emit("No se pudo inicializar el control Bluetooth del ZN02.");return;}
  }catch(SecurityException error){emit("Android no autorizó el acceso Bluetooth del ZN02.");return;}
   catch(RuntimeException error){emit("El SDK del ZN02 no pudo inicializarse. No se envió ningún comando.");return;}
  if(scanning){emit("La búsqueda ZNP ya está activa.");return;}
  target=null;connected=false;vendorProtocolVerified=false;
  controller.addBTRcspEventCallback(eventCallback);
  emit("Buscando exclusivamente ZN02 / ZNP N02 con el protocolo Bluetooth del dispositivo…");
  scanning=controller.startBleScan(20000);
  if(!scanning)emit("El SDK JieLi no pudo iniciar el escaneo. Revisá Bluetooth y permisos.");
 }
 public synchronized void setFlashlight(boolean enabled){
  if(closed||!connected||!vendorProtocolVerified||target==null||caseOp==null){
   emit("ZN02 sin protocolo compatible verificado. No se envió ningún comando.");
   return;
  }
  emit(enabled?"Solicitando encendido de luz al ZN02…":"Solicitando apagado de luz al ZN02…");
  caseOp.setFlashlightState(target,enabled,new OnRcspActionCallback<Boolean>(){
   @Override public void onSuccess(BluetoothDevice device,Boolean result){
    if(!isTarget(device))return;
    emit(Boolean.TRUE.equals(result)?"El ZN02 aceptó el cambio de luz. Verificando estado…":"El ZN02 no confirmó el cambio de luz.");
    readFlashlight();
   }
   @Override public void onError(BluetoothDevice device,BaseError error){
    if(isTarget(device))emit("El ZN02 rechazó el comando de luz: "+error.getMessage()+" (código "+error.getSubCode()+").");
   }
  });
 }
 public synchronized void readFlashlight(){
  if(!connected||!vendorProtocolVerified||target==null||caseOp==null){emit("Luz no consultada: protocolo del ZN02 no verificado.");return;}
  caseOp.getFlashlightState(target,new OnRcspActionCallback<Boolean>(){
   @Override public void onSuccess(BluetoothDevice device,Boolean on){
    if(isTarget(device))emit("Estado real de luz del ZN02: "+(Boolean.TRUE.equals(on)?"ENCENDIDA":"APAGADA")+".");
   }
   @Override public void onError(BluetoothDevice device,BaseError error){
    if(isTarget(device))emit("La luz del ZN02 no está soportada o no respondió: "+error.getMessage()+" (código "+error.getSubCode()+").");
   }
  });
 }
 public synchronized void uploadGoroScreenSaver(File sourceGif){
  if(closed||!connected||!vendorProtocolVerified||target==null||caseOp==null){
   emit("ZN02 sin protocolo de pantalla verificado. No se inició la transferencia.");return;
  }
  if(uploadingResource){emit("Ya hay una transferencia de animación en curso.");return;}
  if(sourceGif==null||!sourceGif.isFile()||!sourceGif.canRead()){
   emit("No se encuentra la animación GORO para el ZN02.");return;
  }
  ChargingCaseInfo info=caseOp.getChargingCaseInfo(target);
  if(info==null||info.getScreenWidth()<=0||info.getScreenHeight()<=0){
   emit("La resolución real del visor ZN02 todavía no está disponible. No se envió ningún archivo.");return;
  }
  try(java.io.FileInputStream input=new java.io.FileInputStream(sourceGif)){
   byte[] header=new byte[10];
   if(input.read(header)!=10||header[0]!='G'||header[1]!='I'||header[2]!='F'){
    emit("El archivo de GORO no es un GIF válido.");return;
   }
   int gifWidth=(header[6]&255)|((header[7]&255)<<8);
   int gifHeight=(header[8]&255)|((header[9]&255)<<8);
   if(gifWidth!=info.getScreenWidth()||gifHeight!=info.getScreenHeight()){
    emit("El GIF es "+gifWidth+"×"+gifHeight+" y la pantalla real es "+info.getScreenWidth()+"×"+info.getScreenHeight()+". No se transfirió para evitar deformarlo.");
    return;
   }
  }catch(Exception error){emit("No se pudo verificar la resolución del GIF: "+error.getClass().getSimpleName());return;}
  uploadingResource=true;
  emit("Preparando animación GORO para el visor ZN02 "+info.getScreenWidth()+"×"+info.getScreenHeight()+"…");
  caseOp.readSDKMessage(target,new OnRcspActionCallback<SDKMessage>(){
   @Override public void onSuccess(BluetoothDevice device,SDKMessage sdk){
    if(!isTarget(device)){finishUpload();return;}
    if(sdk==null||(sdk.getChip()!=SDKMessage.CHIP_701N&&sdk.getChip()!=SDKMessage.CHIP_707N)){
     emit("El chip del estuche no está soportado por el conversor GIF 701N/707N. Transferencia cancelada.");
     finishUpload();return;
    }
    resourceExecutor.execute(()->convertAndTransfer(sourceGif,info,sdk.getChip()));
   }
   @Override public void onError(BluetoothDevice device,BaseError error){
    if(isTarget(device)){emit("No se pudo leer el chip para convertir el GIF: "+error.getMessage());finishUpload();}
   }
  });
 }
 private void convertAndTransfer(File sourceGif,ChargingCaseInfo info,int chip){
  try{
   int converterChip=chip==SDKMessage.CHIP_707N?GifConverter.CHIP_707N:GifConverter.CHIP_701N;
   GifConvertResult result=GifConverter.getInstance().gif2Bin(sourceGif.getAbsolutePath(),
      GifConverter.MODE_LOW_COMPRESSION_RATE,converterChip);
   if(result==null||!result.isSuccess()||result.getGifBin()==null||result.getGifBin().getData()==null){
    emit("El conversor JieLi rechazó la animación GIF. No se modificó el estuche.");finishUpload();return;
   }
   byte[] bytes=result.getGifBin().getData();
   short crc=CryptoUtil.CRC16(bytes,(short)0);
   String suffix=chip==SDKMessage.CHIP_701N?".RES":"";
   File output=new File(context.getCacheDir(),"GORO-"+Integer.toHexString(crc&0xffff)+suffix);
   try(FileOutputStream stream=new FileOutputStream(output)){stream.write(bytes);stream.flush();}
   List<SDCardBean> storages=caseOp.getOnlineStorages(target);
   SDCardBean flash=null;
   if(storages!=null)for(SDCardBean storage:storages)if(storage.isFlash()&&storage.isOnline()){flash=storage;break;}
   if(flash==null){emit("El ZN02 no informa memoria flash disponible. No se transfirió la animación.");output.delete();finishUpload();return;}
   final SDCardBean selectedFlash=flash;
   FileOpImpl fileOp=caseOp.getFileOp();
   emit("Animación GORO convertida. Transfiriendo "+bytes.length+" bytes al ZN02…");
   fileOp.createBigFile(new CreateFileParam(target,output,selectedFlash),new OnTaskStateListener(){
    @Override public void onStart(){emit("Transferencia de animación GORO al ZN02 iniciada.");}
    @Override public void onProgress(int progress){emit("ZN02 · transferencia GORO "+progress+"%.");}
    @Override public void onStop(){
     String devicePath="/"+output.getName();
     caseOp.setCurrentScreenSaver(target,selectedFlash.getDevHandler(),devicePath,new OnRcspActionCallback<Boolean>(){
      @Override public void onSuccess(BluetoothDevice device,Boolean ok){
       output.delete();
       if(isTarget(device)&&Boolean.TRUE.equals(ok)){
        emit("Visor del ZN02 actualizado con la animación GORO. Verificando recurso activo…");
        caseOp.getCurrentScreenSaver(device,new OnRcspActionCallback<com.jieli.bluetooth.bean.settings.v0.ResourceInfo>(){
         @Override public void onSuccess(BluetoothDevice d,com.jieli.bluetooth.bean.settings.v0.ResourceInfo resource){if(isTarget(d))emit("Animación GORO confirmada en el visor ZN02: "+resource);}
         @Override public void onError(BluetoothDevice d,BaseError error){if(isTarget(d))emit("El ZN02 aceptó el cambio, pero no se pudo leer el recurso activo: "+error.getMessage());}
        });
       }else emit("El ZN02 no confirmó la activación de la animación GORO.");
       finishUpload();
      }
      @Override public void onError(BluetoothDevice device,BaseError error){
       output.delete();if(isTarget(device))emit("El GIF se transfirió, pero no se pudo activar: "+error.getMessage());finishUpload();
      }
     });
    }
    @Override public void onCancel(int reason){output.delete();emit("Transferencia GORO cancelada por el dispositivo.");finishUpload();}
    @Override public void onError(int code,String message){output.delete();emit("Falló la transferencia GORO: "+message+" (código "+code+").");finishUpload();}
   });
  }catch(Exception error){emit("No se pudo preparar la animación GORO: "+error.getClass().getSimpleName());finishUpload();}
 }
 private synchronized void finishUpload(){uploadingResource=false;}
 private boolean isTarget(BluetoothDevice device){
  return target!=null&&device!=null&&target.getAddress()!=null&&target.getAddress().equals(device.getAddress());
 }
 private void emit(String value){if(listener!=null&&!closed)mainHandler.post(()->{if(listener!=null&&!closed)listener.onStatus(value);});}
 public synchronized void close(){
  if(closed)return;closed=true;
  if(controller!=null){
   if(scanning)controller.stopScan();
   controller.removeBTRcspEventCallback(eventCallback);
  }
  if(caseOp!=null)caseOp.removeOnChargingCaseListener(caseListener);
  resourceExecutor.shutdownNow();
  scanning=false;connected=false;vendorProtocolVerified=false;target=null;caseOp=null;
 }
}
