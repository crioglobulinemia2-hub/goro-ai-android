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
 public interface Listener { void onStatus(String status); default void onMicrophonePcm(byte[] pcm16Mono){} default void onAudioTarget(BluetoothDevice audioTarget){} default void onMicrophoneStarted(){} default void onMicrophoneIdle(){} default void onMicrophoneError(String error){} }
 private final Context context;
 private final ExecutorService resourceExecutor=Executors.newSingleThreadExecutor();
 private ScreenUpload screenUpload;
 private static final class ScreenUpload {
  final BluetoothDevice device;final long epoch;final ChargingCaseOpImpl operation;
  File output;FileOpImpl fileOperation;Runnable timeout;
  ScreenUpload(BluetoothDevice device,long epoch,ChargingCaseOpImpl operation){this.device=device;this.epoch=epoch;this.operation=operation;}
 }
 private RCSPController controller;
 private final Listener listener;
 private final Handler mainHandler=new Handler(Looper.getMainLooper());
 private final BTRcspEventCallback eventCallback;
 private ChargingCaseOpImpl caseOp;
 private volatile BluetoothDevice target;
 private volatile String requestedModel="Z02";
 private volatile String activeModel="Z02";
 private boolean scanning;
 private boolean callbackRegistered;
 private volatile boolean connected;
 private volatile boolean vendorProtocolVerified=false;
 private volatile boolean closed;
 private final BluetoothBondSession bondSession;
 private volatile long connectionEpoch;
 private long voiceEpoch;
 private RecordOpImpl recorder;
 private OpusManager decoder;
 private OnRecordStateCallback recordCallback;
 private ByteArrayOutputStream microphonePcm=new ByteArrayOutputStream();
 private boolean recording;
 private boolean draining;
 private Runnable authPoll,authTimeout,voiceTimeout,drainTimeout,drainFinishTimeout,connectionCheck;
 private OnChargingCaseListener caseListener;
 private OnChargingCaseListener createCaseListener(long epoch){
  return new OnChargingCaseListener(){
   @Override public void onInit(BluetoothDevice device,int state){
    mainHandler.post(()->{
     if(!isCurrentConnection(device,epoch))return;
     emit(state==0?"ZN02 · inicialización Bluetooth completada.":"ZN02 · error de inicialización: "+state);
    });
   }
   @Override public void onChargingCaseInfoChange(BluetoothDevice device,int function,ChargingCaseInfo info){
    if(info==null)return;
    // Capture values before dispatch: the vendor may reuse and mutate its info object.
    final boolean light=info.isFlashlightOn();final int brightness=info.getBrightness();
    mainHandler.post(()->{
     if(!isCurrentConnection(device,epoch))return;
     if(function==ChargingCaseInfo.FUNC_FLASHLIGHT)
      emit("ZN02 · luz: "+(light?"encendida":"apagada"));
     else if(function==ChargingCaseInfo.FUNC_BRIGHTNESS)
      emit("ZN02 · brillo de pantalla: "+brightness+"%");
    });
   }
   @Override public void onChargingCaseEvent(BluetoothDevice device,SettingFunction function){
    mainHandler.post(()->{if(isCurrentConnection(device,epoch))emit("ZN02 · evento de dispositivo recibido.");});
   }
  };
 }
 private void releaseCaseObservation(){
  if(caseOp!=null&&caseListener!=null)try{caseOp.removeOnChargingCaseListener(caseListener);}catch(RuntimeException ignored){}
  caseListener=null;
 }
 public GoroZn02Controller(Context context,Listener listener){
  this.context=context.getApplicationContext();
  this.listener=listener;
  this.bondSession=new BluetoothBondSession(context);
  controller=null;
  eventCallback=new BTRcspEventCallback(){
   @Override public void onDiscoveryStatus(boolean ble,boolean start){
    mainHandler.post(()->{
     if(!ble||closed)return;
     scanning=start;
     if(!start&&!connected&&target==null)emit("Búsqueda finalizada: no se encontró "+modelLabel()+".");
    });
   }
   @Override public void onDiscovery(BluetoothDevice device,BleScanMessage message){
    mainHandler.post(()->{
     if(closed||!scanning||device==null||controller==null)return;
     String name=null;
     try{name=device.getName();}catch(SecurityException ignored){}
     ZnpDeviceProfile profile=ZnpAdvertisement.profile(name,message==null?null:message.getRawData());
     if(!profile.isCandidate())return;
     String candidate=profile.model.name().replace("_CANDIDATE","");
     if(!candidate.equals(requestedModel))return;
     // Explicit model selection authorizes a conservative RCSP handshake attempt.
     // No device commands are enabled until authentication and chipset checks pass.
     activeModel=candidate;
     target=device;
     try{controller.stopScan();}catch(RuntimeException ignored){}
     scanning=false;
     emit("Anuncio "+profile.modelLabel()+" detectado. Abriendo enlace RCSP y verificando autenticación…");
     try{controller.connectDevice(device);}
     catch(RuntimeException error){emit("El SDK JieLi no pudo iniciar la conexión BLE RCSP: "+error.getClass().getSimpleName());}
    });
   }
   @Override public void onConnection(BluetoothDevice device,int status){
    mainHandler.post(()->{
     if(closed||!isTarget(device))return;
     if(status==StateCode.CONNECTION_OK||status==StateCode.CONNECTION_CONNECTED){
      if(connectionCheck!=null)mainHandler.removeCallbacks(connectionCheck);
      // JieLi reports both transport-connected and ready for the same link.
      // Restarting authentication here would invalidate an active capture's
      // epoch without releasing it, leaving every later startVoice blocked.
      if(connected)return;
      connected=true;vendorProtocolVerified=false;
      emit("Enlace BLE "+modelLabel()+" abierto. Verificando autenticación RCSP…");
      verifyAuthenticated(device);
     }else if(status==StateCode.CONNECTION_DISCONNECT||status==StateCode.CONNECTION_FAILED){
      // JieLi may report a classic audio profile failure while the BLE/RCSP link remains alive.
      if(connectionCheck!=null)mainHandler.removeCallbacks(connectionCheck);
      connectionCheck=()->{
       if(closed||!isTarget(device)||controller==null)return;
       if(!controller.isDeviceConnected(device)){
        connectionEpoch++;connected=false;vendorProtocolVerified=false;releaseVoice();cancelScreenUpload();releaseCaseObservation();bondSession.close();
        if(authPoll!=null)mainHandler.removeCallbacks(authPoll);
        if(authTimeout!=null)mainHandler.removeCallbacks(authTimeout);
        emit(modelLabel()+" desconectado (estado "+status+").");
       }else{
        emit("El perfil secundario informó "+status+"; el enlace BLE/RCSP principal sigue conectado.");
       }
      };
      mainHandler.postDelayed(connectionCheck,500);
     }else if(status==StateCode.CONNECTION_CONNECTING){
      emit("Conectando "+modelLabel()+" por Bluetooth LE…");
     }
    });
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
 static boolean isScanLocationReady(Context context){
  if(Build.VERSION.SDK_INT>=31)return true;
  android.location.LocationManager location=(android.location.LocationManager)context.getSystemService(Context.LOCATION_SERVICE);
  if(location==null)return false;
  try{
   if(Build.VERSION.SDK_INT>=28)return location.isLocationEnabled();
   return location.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
     ||location.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER);
  }catch(SecurityException error){return false;}
 }
 static BluetoothOption scanOptions(){
  return BluetoothOption.createDefaultOption()
      .setUseMultiDevice(true)
      .setPriority(0)
      .setMandatoryUseBLE(false)
      .setMtu(509)
      .setUseDeviceAuth(true)
      .setBleScanMode(2)
      // SDK defaults filter vendor advertising formats and discard devices
      // whose Android name cache is empty before our callback can inspect them.
      // Discover broadly, then apply our Z02 name filter and RCSP authentication.
      .setBleScanStrategy(BluetoothConstant.NONE_FILTER)
      .setSkipNoNameDev(false)
      .setReconnect(false);
 }
 public synchronized void startScan(){startScan("Z02");}
 public synchronized void startScan(String model){
  String requested=model==null?"Z02":model.trim().toUpperCase(java.util.Locale.ROOT);
  if(!requested.matches("Z01|Z02|N02")){emit("Modelo ZNP no reconocido. Elegí Z01, Z02 o N02.");return;}
  if(closed){emit("Controlador ZNP cerrado.");return;}
  if(connected&&target!=null){
   if(activeModel.equals(requested))emit("El "+modelLabel()+" ya tiene una sesión Bluetooth abierta.");
   else emit("Ya hay una sesión activa con "+modelLabel()+". Desconectalo antes de cambiar de modelo.");
   return;
  }
  if(scanning){
   if(requestedModel.equals(requested)){emit("Buscando "+modelLabel()+"; la búsqueda ya está activa.");return;}
   try{if(controller!=null)controller.stopScan();}catch(RuntimeException ignored){}
   scanning=false;
  }
  requestedModel=requested;
  activeModel=requested;
  if(neededPermissions().length>0){emit("Faltan permisos Bluetooth para conectar "+modelLabel()+".");return;}
  try{
   BluetoothManager manager=(BluetoothManager)context.getSystemService(Context.BLUETOOTH_SERVICE);
   BluetoothAdapter adapter=manager==null?null:manager.getAdapter();
   if(adapter==null){emit("El dispositivo Android no tiene Bluetooth; GORO sigue funcionando sin ZN02.");return;}
   if(!adapter.isEnabled()){emit("Bluetooth está apagado. Activá Bluetooth para conectar el ZN02.");return;}
   if(!isScanLocationReady(context)){emit("En Android 11 o anterior, activá Ubicación para que Android permita buscar el ZNP Z02. No se inició la búsqueda.");return;}
   if(!RCSPController.isInit()){
    BluetoothOption option=scanOptions();
    RCSPController.init(context,option);
   }
   controller=RCSPController.getInstance();
   if(controller==null||!RCSPController.isInit()){emit("No se pudo inicializar el control Bluetooth del ZN02.");return;}
  }catch(SecurityException error){emit("Android no autorizó el acceso Bluetooth del ZN02.");return;}
   catch(RuntimeException error){emit("El SDK del ZN02 no pudo inicializarse. No se envió ningún comando.");return;}
  if(scanning){emit("La búsqueda ZNP ya está activa.");return;}
  target=null;connected=false;vendorProtocolVerified=false;
  if(!callbackRegistered){controller.addBTRcspEventCallback(eventCallback);callbackRegistered=true;}
  emit("Buscando "+modelLabel()+" por Bluetooth LE…");
  scanning=controller.startBleScan(20000);
  if(!scanning)emit("El SDK JieLi no pudo iniciar el escaneo. Revisá Bluetooth y permisos.");
 }

 private void verifyAuthenticated(BluetoothDevice device){
  final long epoch=++connectionEpoch;
  if(authPoll!=null)mainHandler.removeCallbacks(authPoll);
  if(authTimeout!=null)mainHandler.removeCallbacks(authTimeout);
  authPoll=new Runnable(){@Override public void run(){
   if(closed||epoch!=connectionEpoch||!isTarget(device))return;
   try{
    if(controller!=null&&controller.isDeviceConnected(device)
      &&controller.getRcspOp().isRcspInit(device)
      &&controller.getRcspOp().isDeviceAuthPass(device)){
     if(authTimeout!=null)mainHandler.removeCallbacks(authTimeout);
     initializeAuthenticatedDevice(device,epoch);return;
    }
   }catch(RuntimeException ignored){}
   mainHandler.postDelayed(this,250);
  }};
  authTimeout=()->{
   if(epoch!=connectionEpoch||closed)return;
   if(authPoll!=null)mainHandler.removeCallbacks(authPoll);
   authPoll=null;authTimeout=null;
   connectionEpoch++;connected=false;vendorProtocolVerified=false;
   releaseVoice();cancelScreenUpload();releaseCaseObservation();
   emit("El SDK no completó la autenticación del Z02 en 20 segundos. Volvé a conectar el dispositivo.");
  };
  mainHandler.postDelayed(authTimeout,20000);
  mainHandler.post(authPoll);
 }
 private void initializeAuthenticatedDevice(BluetoothDevice device,long epoch){
  if(closed||epoch!=connectionEpoch||!isTarget(device))return;
  emit("Autenticación RCSP de "+modelLabel()+" confirmada.");
  try{
   releaseCaseObservation();
   final ChargingCaseOpImpl operation=ChargingCaseOpImpl.instance(controller.getRcspOp());caseOp=operation;
   caseListener=createCaseListener(epoch);operation.addOnChargingCaseListener(caseListener);
   operation.getChargingCaseInfo(device);
   operation.readSDKMessage(device,new OnRcspActionCallback<SDKMessage>(){
    @Override public void onSuccess(BluetoothDevice d,SDKMessage message){
     if(!isCurrentConnection(d,epoch)||message==null)return;
     int chip=message.getChip();ChargingCaseInfo info=operation.getChargingCaseInfo(d);
     if(info!=null)emit(modelLabel()+" · chip "+chip+" · pantalla "+info.getScreenWidth()+"×"+info.getScreenHeight()+".");
     if(chip==SDKMessage.CHIP_701N||chip==SDKMessage.CHIP_707N){vendorProtocolVerified=true;emit("Perfil de pantalla JieLi confirmado para "+modelLabel()+".");readFlashlight();}
     else{vendorProtocolVerified=false;emit("Micrófono RCSP disponible; controles de pantalla bloqueados para "+modelLabel()+" por chip no compatible.");}
    }
    @Override public void onError(BluetoothDevice d,BaseError error){if(isCurrentConnection(d,epoch))emit("Pantalla Z02 no disponible: "+error.getMessage());}
   });
   operation.readScreenInfo(device,new OnRcspActionCallback<ScreenInfo>(){
    @Override public void onSuccess(BluetoothDevice d,ScreenInfo info){if(isCurrentConnection(d,epoch))emit("Pantalla Z02 leída por SDK: "+String.valueOf(info));}
    @Override public void onError(BluetoothDevice d,BaseError error){if(isCurrentConnection(d,epoch))emit("Pantalla Z02 no disponible: "+error.getMessage());}
   });
   operation.getBrightness(device,new OnRcspActionCallback<Integer>(){
    @Override public void onSuccess(BluetoothDevice d,Integer value){if(isCurrentConnection(d,epoch))emit("Brillo Z02: "+value+"%.");}
    @Override public void onError(BluetoothDevice d,BaseError error){if(isCurrentConnection(d,epoch))emit("Brillo Z02 no disponible: "+error.getMessage());}
   });
  }catch(RuntimeException error){emit("RCSP autenticado; perfil de pantalla no disponible: "+error.getClass().getSimpleName());}
  try{
   String edr=null;
   if(controller.getRcspOp().getDeviceInfo(device)!=null)edr=controller.getRcspOp().getDeviceInfo(device).getEdrAddr();
   BluetoothManager manager=(BluetoothManager)context.getSystemService(Context.BLUETOOTH_SERVICE);
   BluetoothAdapter adapter=manager==null?null:manager.getAdapter();
   BluetoothDevice audioTarget=(edr!=null&&BluetoothAdapter.checkBluetoothAddress(edr)&&!"00:00:00:00:00:00".equals(edr)&&adapter!=null)?adapter.getRemoteDevice(edr):device;
   listener.onAudioTarget(audioTarget);
   final BluetoothDevice selected=audioTarget;
   bondSession.start(selected,()->{
    if(closed||epoch!=connectionEpoch||!isTarget(device))return;
    try{controller.getBluetoothManager().startConnectByBreProfiles(selected);emit("Perfil de audio clásico solicitado. Verificando salida multimedia "+modelLabel()+"…");}
    catch(Exception error){emit("No se pudo iniciar el perfil de audio Z02: "+error.getClass().getSimpleName());}
   },this::emit);
  }catch(Exception error){emit("No se pudo resolver el perfil de audio Z02: "+error.getClass().getSimpleName());}
 }
 public synchronized boolean isVoiceCaptureActive(){return recording||draining;}
 public synchronized boolean startVoice(){
  if(screenUpload!=null){emit("La escucha permanece pausada mientras se instala la animación.");return false;}
  if(closed||!connected||target==null||recording||draining||controller==null){emit("El micrófono de "+modelLabel()+" no está listo o ya hay una captura activa.");return false;}
  if(!controller.getRcspOp().isRcspInit(target)||!controller.getRcspOp().isDeviceAuthPass(target)){emit("La sesión RCSP del Z02 dejó de estar autenticada.");return false;}
  final BluetoothDevice device=target;final long epoch=connectionEpoch;final long capture=++voiceEpoch;
  microphonePcm=new ByteArrayOutputStream();final ByteArrayOutputStream out=microphonePcm;
  final ar.goro.ai.voice.PcmSpeechEndpoint endpointer=new ar.goro.ai.voice.PcmSpeechEndpoint();
  try{
   RecordOpImpl op=RecordOpImpl.getInstance(controller.getRcspOp());recorder=op;
   OpusManager opus=new OpusManager();decoder=opus;recording=true;draining=false;
   OnRecordStateCallback callback=new OnRecordStateCallback(){
    @Override public void onStateChange(BluetoothDevice d,RecordState state){
     if(d==null||!isTarget(d)||state==null)return;
     int code=state.getState();byte[] block=state.getVoiceDataBlock()==null?null:state.getVoiceDataBlock().clone();
     mainHandler.post(()->{
      if(closed||epoch!=connectionEpoch||capture!=voiceEpoch)return;
      if(code==RecordState.RECORD_STATE_WORKING){
       if((recording||draining)&&block!=null&&block.length>0){try{opus.writeAudioStream(block);}catch(Exception error){abortVoice("No se pudo decodificar Opus del Z02: "+error.getClass().getSimpleName());}}
      }else if(code==RecordState.RECORD_STATE_IDLE&&recording)stopVoice();
     });
    }
   };
   recordCallback=callback;op.addOnRecordStateCallback(callback);
   opus.startDecodeStream(new OpusOption(),new OnDecodeStreamCallback(){
    @Override public void onStart(){mainHandler.post(()->{
     if(closed||epoch!=connectionEpoch||capture!=voiceEpoch||!recording)return;
     try{
      op.startRecord(device,new RecordParam(Constants.AUDIO_TYPE_OPUS,RecordParam.SAMPLE_RATE_16K,RecordParam.VAD_WAY_DEVICE),new OnRcspActionCallback<Boolean>(){
       @Override public void onSuccess(BluetoothDevice d,Boolean ok){mainHandler.post(()->{if(epoch==connectionEpoch&&capture==voiceEpoch&&recording){if(Boolean.TRUE.equals(ok)){emit("Micrófono Z02 activo. Esperando voz…");listener.onMicrophoneStarted();}else abortVoice("El Z02 rechazó la captura de micrófono.");}});}
       @Override public void onError(BluetoothDevice d,BaseError error){mainHandler.post(()->{if(epoch==connectionEpoch&&capture==voiceEpoch)abortVoice("El Z02 rechazó la captura: "+error.getMessage());});}
      });
     }catch(Exception error){abortVoice("No se pudo iniciar el micrófono Z02: "+error.getClass().getSimpleName());}
    });}
    @Override public void onDecodeStream(byte[] data){
     if(data==null||data.length==0)return;byte[] copy=data.clone();
     mainHandler.post(()->{
      if(closed||epoch!=connectionEpoch||capture!=voiceEpoch||(!recording&&!draining))return;
      if(out.size()+copy.length>480000){stopVoice();return;}
      boolean first=out.size()==0;out.write(copy,0,copy.length);if(first)emit("PCM16 recibido desde el micrófono Z02.");
      if(recording&&endpointer.accept(copy))stopVoice();
     });
    }
    @Override public void onComplete(String path){mainHandler.post(()->{if(epoch==connectionEpoch&&capture==voiceEpoch&&draining)finishVoice();});}
    @Override public void onError(int code,String message){mainHandler.post(()->{if(epoch==connectionEpoch&&capture==voiceEpoch)abortVoice("Decodificación Opus falló ("+code+"): "+message);});}
   });
   voiceTimeout=()->{if(epoch==connectionEpoch&&capture==voiceEpoch)stopVoice();};
   mainHandler.postDelayed(voiceTimeout,12000);
   emit("Abriendo micrófono del Z02…");return true;
  }catch(Exception error){abortVoice("Audio Z02 no disponible: "+error.getClass().getSimpleName());return false;}
 }
 public synchronized void cancelVoice(){
  BluetoothDevice device=target;RecordOpImpl op=recorder;releaseVoice();
  if(device!=null&&op!=null)try{op.stopRecord(device,RecordState.REASON_STOP,null);}catch(Exception ignored){}
 }
 public synchronized void stopVoice(){
  if(!recording)return;
  recording=false;draining=true;
  if(voiceTimeout!=null)mainHandler.removeCallbacks(voiceTimeout);
  final long epoch=connectionEpoch,capture=voiceEpoch;BluetoothDevice device=target;RecordOpImpl op=recorder;
  if(device!=null&&op!=null)try{op.stopRecord(device,RecordState.REASON_STOP,null);}catch(Exception ignored){}
  drainTimeout=()->{
   if(epoch!=connectionEpoch||capture!=voiceEpoch||!draining)return;
   try{if(decoder!=null)decoder.stopDecodeStream();}catch(Exception ignored){}
   drainFinishTimeout=()->{if(epoch==connectionEpoch&&capture==voiceEpoch&&draining)finishVoice();};
   mainHandler.postDelayed(drainFinishTimeout,250);
  };
  mainHandler.postDelayed(drainTimeout,250);
 }
 private void finishVoice(){
  if(!draining)return;
  if(drainTimeout!=null)mainHandler.removeCallbacks(drainTimeout);
  byte[] pcm=microphonePcm.toByteArray();releaseVoice();
  if(pcm.length==0){emit("Z02 sin palabras en este turno; retomando escucha.");listener.onMicrophoneIdle();}
  else{emit("Captura del micrófono Z02 recibida ("+pcm.length+" bytes PCM).");listener.onMicrophonePcm(pcm);}
 }
 private void abortVoice(String message){
  BluetoothDevice device=target;RecordOpImpl op=recorder;releaseVoice();
  if(device!=null&&op!=null)try{op.stopRecord(device,RecordState.REASON_STOP,null);}catch(Exception ignored){}
  emit(message);listener.onMicrophoneError(message);
 }
 private void releaseVoice(){
  ++voiceEpoch;recording=false;draining=false;
  if(voiceTimeout!=null)mainHandler.removeCallbacks(voiceTimeout);
  if(drainTimeout!=null)mainHandler.removeCallbacks(drainTimeout);
  if(drainFinishTimeout!=null)mainHandler.removeCallbacks(drainFinishTimeout);
  voiceTimeout=null;drainTimeout=null;drainFinishTimeout=null;
  if(recordCallback!=null&&recorder!=null)try{recorder.removeOnRecordStateCallback(recordCallback);}catch(Exception ignored){}
  recordCallback=null;
  OpusManager old=decoder;decoder=null;
  if(old!=null){try{old.stopDecodeStream();}catch(Exception ignored){}try{old.release();}catch(Exception ignored){}}
  recorder=null;microphonePcm.reset();
 }
 public synchronized void setFlashlight(boolean enabled){
  if(closed||!connected||!vendorProtocolVerified||target==null||caseOp==null){
   emit("ZN02 sin protocolo compatible verificado. No se envió ningún comando.");
   return;
  }
  emit(enabled?"Solicitando encendido de luz al ZN02…":"Solicitando apagado de luz al ZN02…");
  try{caseOp.setFlashlightState(target,enabled,flashlightSetCallback(connectionEpoch));}
  catch(RuntimeException error){emit("No se pudo enviar el comando de luz al ZN02: "+error.getClass().getSimpleName());}
 }
 private OnRcspActionCallback<Boolean> flashlightSetCallback(long epoch){
  return new OnRcspActionCallback<Boolean>(){
   @Override public void onSuccess(BluetoothDevice device,Boolean result){
    mainHandler.post(()->{
     if(!isCurrentConnection(device,epoch))return;
     emit(Boolean.TRUE.equals(result)?"El ZN02 aceptó el cambio de luz. Verificando estado…":"El ZN02 no confirmó el cambio de luz.");
     if(Boolean.TRUE.equals(result))readFlashlight();
    });
   }
   @Override public void onError(BluetoothDevice device,BaseError error){
    mainHandler.post(()->{
     if(isCurrentConnection(device,epoch))emit("El ZN02 rechazó el comando de luz: "+error.getMessage()+" (código "+error.getSubCode()+").");
    });
   }
  };
 }
 public synchronized void readFlashlight(){
  if(closed||!connected||!vendorProtocolVerified||target==null||caseOp==null){emit("Luz no consultada: protocolo del ZN02 no verificado.");return;}
  try{caseOp.getFlashlightState(target,flashlightReadCallback(connectionEpoch));}
  catch(RuntimeException error){emit("No se pudo consultar la luz del ZN02: "+error.getClass().getSimpleName());}
 }
 private OnRcspActionCallback<Boolean> flashlightReadCallback(long epoch){
  return new OnRcspActionCallback<Boolean>(){
   @Override public void onSuccess(BluetoothDevice device,Boolean on){
    mainHandler.post(()->{
     if(!isCurrentConnection(device,epoch))return;
     emit(on==null?"El ZN02 no confirmó el estado de la luz.":"Estado real de luz del ZN02: "+(on?"ENCENDIDA":"APAGADA")+".");
    });
   }
   @Override public void onError(BluetoothDevice device,BaseError error){
    mainHandler.post(()->{
     if(isCurrentConnection(device,epoch))emit("La luz del ZN02 no está soportada o no respondió: "+error.getMessage()+" (código "+error.getSubCode()+").");
    });
   }
  };
 }
 public synchronized void uploadGoroScreenSaver(File sourceGif){
  if(closed||!connected||!vendorProtocolVerified||target==null||caseOp==null){
   emit(modelLabel()+" sin protocolo de pantalla verificado. No se inició la transferencia.");return;
  }
  if(screenUpload!=null){emit("Ya hay una transferencia de animación en curso.");return;}
  cancelVoice();
  if(sourceGif==null||!sourceGif.isFile()||!sourceGif.canRead()){
   emit("No se encuentra la animación GORO para "+modelLabel()+".");return;
  }
  ChargingCaseInfo info=caseOp.getChargingCaseInfo(target);
  if(info==null||info.getScreenWidth()<=0||info.getScreenHeight()<=0){
   emit("La resolución real del visor "+modelLabel()+" todavía no está disponible. No se envió ningún archivo.");return;
  }
  try(java.io.FileInputStream input=new java.io.FileInputStream(sourceGif)){
   byte[] header=new byte[10];
   if(input.read(header)!=10||header[0]!='G'||header[1]!='I'||header[2]!='F'){
    emit("El archivo de GORO no es un GIF válido.");return;
   }
   int gifWidth=(header[6]&255)|((header[7]&255)<<8);
   int gifHeight=(header[8]&255)|((header[9]&255)<<8);
   if(gifWidth!=info.getScreenWidth()||gifHeight!=info.getScreenHeight()){
    sourceGif=ZnpScreenAsset.forDisplay(context,info.getScreenWidth(),info.getScreenHeight());
   }
  }catch(Exception error){emit("No se pudo verificar la resolución del GIF: "+error.getClass().getSimpleName());return;}
  final File matchedGif=sourceGif;
  final ScreenUpload upload=new ScreenUpload(target,connectionEpoch,caseOp);screenUpload=upload;
  upload.timeout=()->{
   if(!currentScreenUpload(upload))return;
   emit("La transferencia del visor agotó el tiempo. La animación no quedó confirmada.");cancelScreenUpload();
  };
  mainHandler.postDelayed(upload.timeout,120000);
  emit("Preparando animación GORO para el visor "+modelLabel()+" "+info.getScreenWidth()+"×"+info.getScreenHeight()+"…");
  try{caseOp.readSDKMessage(upload.device,new OnRcspActionCallback<SDKMessage>(){
   @Override public void onSuccess(BluetoothDevice device,SDKMessage sdk){
    if(!currentScreenUpload(upload)||!upload.device.equals(device))return;
    if(sdk==null||(sdk.getChip()!=SDKMessage.CHIP_701N&&sdk.getChip()!=SDKMessage.CHIP_707N)){
     emit("El chip del estuche no está soportado por el conversor GIF 701N/707N. Transferencia cancelada.");
     finishScreenUpload(upload);return;
    }
    if(!sdk.isSupportGif()){
     emit("El firmware del Z02 informa que no admite animaciones GIF. No se envió un archivo incompatible.");
     finishScreenUpload(upload);return;
    }
    try{resourceExecutor.execute(()->convertAndTransfer(matchedGif,sdk.getChip(),upload));}
    catch(java.util.concurrent.RejectedExecutionException ignored){finishScreenUpload(upload);}
   }
   @Override public void onError(BluetoothDevice device,BaseError error){
    if(currentScreenUpload(upload)&&upload.device.equals(device)){emit("No se pudo leer el chip para convertir el GIF: "+error.getMessage());finishScreenUpload(upload);}
   }
  });}catch(Exception error){emit("No se pudo iniciar la transferencia del visor: "+error.getClass().getSimpleName());finishScreenUpload(upload);}
 }
 private void convertAndTransfer(File sourceGif,int chip,ScreenUpload upload){
  File output=null;
  try{
   if(!currentScreenUpload(upload))return;
   int converterChip=chip==SDKMessage.CHIP_707N?GifConverter.CHIP_707N:GifConverter.CHIP_701N;
   GifConvertResult result=GifConverter.getInstance().gif2Bin(sourceGif.getAbsolutePath(),
      GifConverter.MODE_LOW_COMPRESSION_RATE,converterChip);
   if(result==null||!result.isSuccess()||result.getGifBin()==null||result.getGifBin().getData()==null){
    if(currentScreenUpload(upload))emit("El conversor JieLi rechazó la animación GIF. No se modificó el estuche.");finishScreenUpload(upload);return;
   }
   byte[] bytes=result.getGifBin().getData();
   short crc=CryptoUtil.CRC16(bytes,(short)0);
   String suffix=chip==SDKMessage.CHIP_701N?".RES":"";
   File directory=new File(context.getCacheDir(),"goro-screen-"+java.util.UUID.randomUUID());
   if(!directory.mkdir())throw new java.io.IOException("No se pudo crear el archivo temporal del visor");
   output=new File(directory,"GORO-"+Integer.toHexString(crc&0xffff)+suffix);
   try(FileOutputStream stream=new FileOutputStream(output)){stream.write(bytes);stream.flush();}
   final File prepared=output;
   mainHandler.post(()->beginScreenTransfer(upload,prepared,bytes.length));
  }catch(Exception error){
   deletePreparedScreen(output);
   if(currentScreenUpload(upload))emit("No se pudo preparar la animación GORO: "+error.getClass().getSimpleName());finishScreenUpload(upload);
  }
 }
 private void beginScreenTransfer(ScreenUpload upload,File output,int byteCount){
  if(!currentScreenUpload(upload)){deletePreparedScreen(output);return;}
  upload.output=output;
  try{
   List<SDCardBean> storages=upload.operation.getOnlineStorages(upload.device);
   SDCardBean flash=null;
   if(storages!=null)for(SDCardBean storage:storages)if(storage.isFlash()&&storage.isOnline()){flash=storage;break;}
   if(flash==null){emit("El ZN02 no informa memoria flash disponible. No se transfirió la animación.");finishScreenUpload(upload);return;}
   final SDCardBean selectedFlash=flash;
   FileOpImpl fileOp=upload.operation.getFileOp();upload.fileOperation=fileOp;
   emit("Animación GORO convertida. Transfiriendo "+byteCount+" bytes al ZN02…");
   fileOp.createBigFile(new CreateFileParam(upload.device,output,selectedFlash),new OnTaskStateListener(){
    @Override public void onStart(){if(currentScreenUpload(upload))emit("Transferencia de animación GORO al ZN02 iniciada.");}
    @Override public void onProgress(int progress){if(currentScreenUpload(upload))emit("ZN02 · transferencia GORO "+progress+"%.");}
    @Override public void onStop(){
     mainHandler.post(()->{
     if(!currentScreenUpload(upload))return;
     String devicePath="/"+output.getName();
     try{upload.operation.setCurrentScreenSaver(upload.device,selectedFlash.getDevHandler(),devicePath,new OnRcspActionCallback<Boolean>(){
      @Override public void onSuccess(BluetoothDevice device,Boolean ok){
       if(!currentScreenUpload(upload)||!upload.device.equals(device))return;
       if(Boolean.TRUE.equals(ok)){
        emit("El ZN02 aceptó la animación GORO. Verificando recurso activo…");
        try{upload.operation.getCurrentScreenSaver(device,new OnRcspActionCallback<com.jieli.bluetooth.bean.settings.v0.ResourceInfo>(){
         @Override public void onSuccess(BluetoothDevice d,com.jieli.bluetooth.bean.settings.v0.ResourceInfo resource){
          if(!currentScreenUpload(upload)||!upload.device.equals(d))return;
          emit(matchesScreenResource(resource,selectedFlash.getDevHandler(),devicePath)
           ?"GORO instalado y seleccionado como salvapantallas. Escucha pausada; el visor puede mantener su pantalla de micrófono hasta volver al inicio."
           :"El visor ZN02 informa otro recurso activo. La animación GORO no quedó confirmada.");
          finishScreenUpload(upload);
         }
         @Override public void onError(BluetoothDevice d,BaseError error){
          if(currentScreenUpload(upload)&&upload.device.equals(d)){emit("El ZN02 aceptó el cambio, pero no se pudo leer el recurso activo: "+error.getMessage());finishScreenUpload(upload);}
         }
        });}catch(Exception error){emit("No se pudo verificar el recurso del visor: "+error.getClass().getSimpleName());finishScreenUpload(upload);}
       }else{emit("El ZN02 no confirmó la activación de la animación GORO.");finishScreenUpload(upload);}
      }
      @Override public void onError(BluetoothDevice device,BaseError error){
       if(currentScreenUpload(upload)&&upload.device.equals(device)){emit("El GIF se transfirió, pero no se pudo activar: "+error.getMessage());finishScreenUpload(upload);}
      }
     });}catch(Exception error){emit("No se pudo activar la animación del visor: "+error.getClass().getSimpleName());finishScreenUpload(upload);}
     });
    }
    @Override public void onCancel(int reason){if(currentScreenUpload(upload))emit("Transferencia GORO cancelada por el dispositivo.");finishScreenUpload(upload);}
    @Override public void onError(int code,String message){if(currentScreenUpload(upload))emit("Falló la transferencia GORO: "+message+" (código "+code+").");finishScreenUpload(upload);}
   });
  }catch(Exception error){if(currentScreenUpload(upload))emit("No se pudo transferir la animación GORO: "+error.getClass().getSimpleName());finishScreenUpload(upload);}
 }
 static boolean matchesScreenResource(com.jieli.bluetooth.bean.settings.v0.ResourceInfo resource,int storage,String path){
  if(resource==null||resource.isInvalid()||resource.getDevHandle()!=storage||path==null)return false;
  String actual=resource.getFilePath();
  if(actual==null)return false;
  return (actual.startsWith("/")?actual:"/"+actual).equals(path.startsWith("/")?path:"/"+path);
 }
 private boolean isCurrentConnection(BluetoothDevice device,long epoch){return !closed&&connected&&epoch==connectionEpoch&&isTarget(device);}
 public synchronized boolean isConnectedTo(String model){
  return connected&&target!=null&&activeModel.equals(model==null?"":model.trim().toUpperCase(java.util.Locale.ROOT));
 }
 public String connectedModelLabel(){return modelLabel();}
 private String modelLabel(){return "ZNP "+activeModel;}
 public synchronized boolean isScreenUploadActive(){return screenUpload!=null;}
 private synchronized boolean currentScreenUpload(ScreenUpload upload){return screenUpload==upload&&isCurrentConnection(upload.device,upload.epoch);}
 private static void deletePreparedScreen(File output){
  if(output==null)return;
  output.delete();File directory=output.getParentFile();
  if(directory!=null&&directory.getName().startsWith("goro-screen-"))directory.delete();
 }
 private synchronized void finishScreenUpload(ScreenUpload upload){
  if(upload.timeout!=null)mainHandler.removeCallbacks(upload.timeout);
  deletePreparedScreen(upload.output);
  if(screenUpload==upload)screenUpload=null;
 }
 private synchronized void cancelScreenUpload(){
  ScreenUpload upload=screenUpload;if(upload==null)return;
  screenUpload=null;
  if(upload.fileOperation!=null)try{upload.fileOperation.cancelBigFileTransfer(upload.device,Constants.REASON_BY_SDK,null);}catch(Exception ignored){}
  finishScreenUpload(upload);
 }
 private boolean isTarget(BluetoothDevice device){
  return target!=null&&device!=null&&target.getAddress()!=null&&target.getAddress().equals(device.getAddress());
 }
 private void emit(String value){
  final long epoch=connectionEpoch;
  if(listener!=null&&!closed)mainHandler.post(()->{if(listener!=null&&!closed&&epoch==connectionEpoch)listener.onStatus(value);});
 }
 public synchronized void close(){
  if(closed)return;closed=true;connectionEpoch++;releaseVoice();cancelScreenUpload();releaseCaseObservation();bondSession.close();
  if(authPoll!=null)mainHandler.removeCallbacks(authPoll);if(authTimeout!=null)mainHandler.removeCallbacks(authTimeout);if(connectionCheck!=null)mainHandler.removeCallbacks(connectionCheck);
  if(controller!=null){
   if(scanning)controller.stopScan();
   if(callbackRegistered){controller.removeBTRcspEventCallback(eventCallback);callbackRegistered=false;}
   if(target!=null)try{controller.disconnectDevice(target);}catch(RuntimeException ignored){}
  }
  releaseCaseObservation();
  resourceExecutor.shutdownNow();
  scanning=false;connected=false;vendorProtocolVerified=false;target=null;caseOp=null;
 }
}
