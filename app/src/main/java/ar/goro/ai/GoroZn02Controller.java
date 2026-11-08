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
import com.jieli.bluetooth.tool.DeviceAddrManager;
import com.jieli.bluetooth.impl.rcsp.RCSPController;
import com.jieli.bluetooth.impl.rcsp.charging_case.ChargingCaseOpImpl;
import com.jieli.bluetooth.interfaces.rcsp.callback.BTRcspEventCallback;
import com.jieli.bluetooth.interfaces.rcsp.callback.OnRcspActionCallback;
import com.jieli.bluetooth.interfaces.rcsp.charging_case.OnChargingCaseListener;
import java.util.List;

/**
 * GORO-owned ZNP charging-case controller. RCSP control is forced over BLE;
 * charging-case EDR addresses are not treated as audio endpoints. Advertised
 * model names are candidates only; RCSP operations require authentication and
 * device-reported chipset and screen capabilities.
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
 private volatile int targetDeviceType=-1;
 private boolean scanning;
 private boolean screenInfoReadInProgress;
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
 private Runnable authPoll,authTimeout,voiceTimeout,drainTimeout,drainFinishTimeout,connectionCheck,connectTimeout;
 private OnChargingCaseListener caseListener;
 private OnChargingCaseListener createCaseListener(long epoch){
  return new OnChargingCaseListener(){
   @Override public void onInit(BluetoothDevice device,int state){
    mainHandler.post(()->{
     if(!isCurrentConnection(device,epoch))return;
     if(state==0){
      ChargingCaseInfo info=caseOp==null?null:caseOp.getChargingCaseInfo(device);
      if(info!=null&&info.getScreenWidth()>0&&info.getScreenHeight()>0)
       emit(modelLabel()+" · inicialización completa; visor "+info.getScreenWidth()+"×"+info.getScreenHeight()+".");
      else emit(modelLabel()+" · inicialización Bluetooth completada; esperando resolución del visor.");
     }else emit(modelLabel()+" · error de inicialización: "+state);
    });
   }
   @Override public void onChargingCaseInfoChange(BluetoothDevice device,int function,ChargingCaseInfo info){
    if(info==null)return;
    // Capture values before dispatch: the vendor may reuse and mutate its info object.
    final boolean light=info.isFlashlightOn();final int brightness=info.getBrightness();
    mainHandler.post(()->{
     if(!isCurrentConnection(device,epoch))return;
     if(function==ChargingCaseInfo.FUNC_FLASHLIGHT)
      emit(modelLabel()+" · luz: "+(light?"encendida":"apagada"));
     else if(function==ChargingCaseInfo.FUNC_BRIGHTNESS)
      emit(modelLabel()+" · brillo de pantalla: "+brightness+"%");
    });
   }
   @Override public void onChargingCaseEvent(BluetoothDevice device,SettingFunction function){
    mainHandler.post(()->{if(isCurrentConnection(device,epoch))emit(modelLabel()+" · evento de dispositivo recibido.");});
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
     targetDeviceType=message==null?-1:message.getDeviceType();
     try{controller.stopScan();}catch(RuntimeException ignored){}
     scanning=false;
     emit("Anuncio "+profile.modelLabel()+" detectado. Abriendo enlace RCSP por BLE y verificando autenticación…");
     try{
      // ZNP charging cases are BLE RCSP endpoints. Persist the same transport
      // choice used by JieLi's charging-bin reference implementation.
      DeviceAddrManager.getInstance().saveDeviceConnectWay(device,BluetoothConstant.PROTOCOL_TYPE_BLE);
      if(connectTimeout!=null)mainHandler.removeCallbacks(connectTimeout);
      connectTimeout=()->{
       if(closed||connected||!isTarget(device))return;
       connectionEpoch++;
       if(authPoll!=null)mainHandler.removeCallbacks(authPoll);
       if(authTimeout!=null)mainHandler.removeCallbacks(authTimeout);
       if(connectionCheck!=null)mainHandler.removeCallbacks(connectionCheck);
       releaseVoice();cancelScreenUpload();releaseCaseObservation();bondSession.close();
       connected=false;vendorProtocolVerified=false;target=null;targetDeviceType=-1;caseOp=null;
       try{controller.disconnectDevice(device);}catch(RuntimeException ignored){}
       connectTimeout=null;
       emit("El dispositivo fue detectado, pero no completó el enlace BLE/RCSP en 20 segundos. Se liberó el intento; volvé a buscarlo.");
      };
      mainHandler.postDelayed(connectTimeout,20000);
      controller.connectDevice(device,BluetoothConstant.PROTOCOL_TYPE_BLE);
     }catch(RuntimeException error){emit("El SDK JieLi no pudo iniciar la conexión BLE RCSP: "+error.getClass().getSimpleName());}
    });
   }
   @Override public void onConnection(BluetoothDevice device,int status){
    mainHandler.post(()->{
     if(closed||!isTarget(device))return;
     if(status==StateCode.CONNECTION_OK||status==StateCode.CONNECTION_CONNECTED){
      if(connectionCheck!=null)mainHandler.removeCallbacks(connectionCheck);
      if(connectTimeout!=null)mainHandler.removeCallbacks(connectTimeout);
      connectTimeout=null;
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
        if(connectTimeout!=null)mainHandler.removeCallbacks(connectTimeout);
        connectTimeout=null;
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
      .setPriority(BluetoothOption.PREFER_BLE)
      .setMandatoryUseBLE(true)
      .setMtu(BluetoothConstant.BLE_MTU_MAX)
      .setUseDeviceAuth(true)
      .setBleScanMode(2)
      // SDK defaults filter vendor advertising formats and discard devices
      // whose Android name cache is empty before our callback can inspect them.
      // Discover broadly, then apply the explicitly selected ZNP model filter and RCSP authentication.
      .setBleScanStrategy(BluetoothConstant.NONE_FILTER)
      .setSkipNoNameDev(false)
      .setReconnect(false);
 }
 public synchronized void startScan(){startScan("Z02");}
 public synchronized void startScan(String model){
  String requested=ZnpDeviceProfile.canonicalModel(model==null?"Z02":model);
  if(requested==null){emit("Modelo ZNP no reconocido. Elegí AZ01, AZ02 o AN02; también se aceptan Z01, Z02 y N02.");return;}
  if(closed){emit("Controlador ZNP cerrado.");return;}
  if(connected&&target!=null){
   if(activeModel.equals(requested))emit("El "+activeModel+" ya tiene una sesión Bluetooth abierta.");
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
   if(adapter==null){emit("El dispositivo Android no tiene Bluetooth; GORO sigue funcionando sin ZNP.");return;}
   if(!adapter.isEnabled()){emit("Bluetooth está apagado. Activá Bluetooth para conectar "+modelLabel()+".");return;}
   if(!isScanLocationReady(context)){emit("En Android 11 o anterior, activá Ubicación para que Android permita buscar "+modelLabel()+". No se inició la búsqueda.");return;}
   if(!RCSPController.isInit()){
    BluetoothOption option=scanOptions();
    RCSPController.init(context,option);
   }
   controller=RCSPController.getInstance();
   if(controller==null||!RCSPController.isInit()){emit("No se pudo inicializar el control Bluetooth de "+modelLabel()+".");return;}
  }catch(SecurityException error){emit("Android no autorizó el acceso Bluetooth de "+modelLabel()+".");return;}
   catch(RuntimeException error){emit("El SDK no pudo inicializarse para "+modelLabel()+". No se envió ningún comando.");return;}
  if(scanning){emit("La búsqueda ZNP ya está activa.");return;}
  if(connectTimeout!=null)mainHandler.removeCallbacks(connectTimeout);
  connectTimeout=null;
  target=null;targetDeviceType=-1;connected=false;vendorProtocolVerified=false;
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
   emit("El SDK no completó la autenticación de "+modelLabel()+" en 20 segundos. Volvé a conectar el dispositivo.");
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
    @Override public void onError(BluetoothDevice d,BaseError error){if(isCurrentConnection(d,epoch))emit("Pantalla "+modelLabel()+" no disponible: "+error.getMessage());}
   });
   operation.readScreenInfo(device,new OnRcspActionCallback<ScreenInfo>(){
    @Override public void onSuccess(BluetoothDevice d,ScreenInfo info){
     mainHandler.post(()->{
      if(!isCurrentConnection(d,epoch))return;
      ChargingCaseInfo current=operation.getChargingCaseInfo(d);
      int width=current==null?-1:current.getScreenWidth();
      int height=current==null?-1:current.getScreenHeight();
      if(width<=0||height<=0){width=readScreenDimension(info,true);height=readScreenDimension(info,false);}
      emit(width>0&&height>0?"Pantalla "+modelLabel()+" confirmada por SDK: "+width+"×"+height+".":"SDK respondió la lectura del visor "+modelLabel()+" sin dimensiones utilizables: "+String.valueOf(info));
     });
    }
    @Override public void onError(BluetoothDevice d,BaseError error){if(isCurrentConnection(d,epoch))emit("Pantalla "+modelLabel()+" no disponible: "+error.getMessage());}
   });
   operation.getBrightness(device,new OnRcspActionCallback<Integer>(){
    @Override public void onSuccess(BluetoothDevice d,Integer value){if(isCurrentConnection(d,epoch))emit("Brillo "+modelLabel()+": "+value+"%.");}
    @Override public void onError(BluetoothDevice d,BaseError error){if(isCurrentConnection(d,epoch))emit("Brillo "+modelLabel()+" no disponible: "+error.getMessage());}
   });
  }catch(RuntimeException error){emit("RCSP autenticado; perfil de pantalla no disponible: "+error.getClass().getSimpleName());}
  if(targetDeviceType!=JL_DeviceType.JL_DEVICE_TYPE_TWS_HEADSET_V1){
   // A charging case is not an A2DP headset. Keep its RCSP link independent
   // and let Android route Magpie output to the phone or an already-connected headset.
   bondSession.close();
   if(listener!=null)listener.onAudioTarget(null);
   emit("Control RCSP de "+modelLabel()+" confirmado por BLE. El estuche no se empareja como auricular; audio por la ruta Android predeterminada.");
   return;
  }
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
    catch(Exception error){emit("No se pudo iniciar el perfil de audio de "+modelLabel()+": "+error.getClass().getSimpleName());}
   },this::emit);
  }catch(Exception error){emit("No se pudo resolver el perfil de audio de "+modelLabel()+": "+error.getClass().getSimpleName());}
 }
 public synchronized boolean isVoiceCaptureActive(){return recording||draining;}
 public synchronized boolean startVoice(){
  if(screenUpload!=null){emit("La escucha permanece pausada mientras se instala la animación.");return false;}
  if(closed||!connected||target==null||recording||draining||controller==null){emit("El micrófono de "+modelLabel()+" no está listo o ya hay una captura activa.");return false;}
  if(!controller.getRcspOp().isRcspInit(target)||!controller.getRcspOp().isDeviceAuthPass(target)){emit("La sesión RCSP de "+modelLabel()+" dejó de estar autenticada.");return false;}
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
       if((recording||draining)&&block!=null&&block.length>0){try{opus.writeAudioStream(block);}catch(Exception error){abortVoice("No se pudo decodificar Opus de "+modelLabel()+": "+error.getClass().getSimpleName());}}
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
       @Override public void onSuccess(BluetoothDevice d,Boolean ok){mainHandler.post(()->{if(epoch==connectionEpoch&&capture==voiceEpoch&&recording){if(Boolean.TRUE.equals(ok)){emit("Micrófono de "+modelLabel()+" activo. Esperando voz…");listener.onMicrophoneStarted();}else abortVoice(modelLabel()+" rechazó la captura de micrófono.");}});}
       @Override public void onError(BluetoothDevice d,BaseError error){mainHandler.post(()->{if(epoch==connectionEpoch&&capture==voiceEpoch)abortVoice(modelLabel()+" rechazó la captura: "+error.getMessage());});}
      });
     }catch(Exception error){abortVoice("No se pudo iniciar el micrófono de "+modelLabel()+": "+error.getClass().getSimpleName());}
    });}
    @Override public void onDecodeStream(byte[] data){
     if(data==null||data.length==0)return;byte[] copy=data.clone();
     mainHandler.post(()->{
      if(closed||epoch!=connectionEpoch||capture!=voiceEpoch||(!recording&&!draining))return;
      if(out.size()+copy.length>480000){stopVoice();return;}
      boolean first=out.size()==0;out.write(copy,0,copy.length);if(first)emit("PCM16 recibido desde el micrófono de "+modelLabel()+".");
      if(recording&&endpointer.accept(copy))stopVoice();
     });
    }
    @Override public void onComplete(String path){mainHandler.post(()->{if(epoch==connectionEpoch&&capture==voiceEpoch&&draining)finishVoice();});}
    @Override public void onError(int code,String message){mainHandler.post(()->{if(epoch==connectionEpoch&&capture==voiceEpoch)abortVoice("Decodificación Opus falló ("+code+"): "+message);});}
   });
   voiceTimeout=()->{if(epoch==connectionEpoch&&capture==voiceEpoch)stopVoice();};
   mainHandler.postDelayed(voiceTimeout,12000);
   emit("Abriendo micrófono de "+modelLabel()+"…");return true;
  }catch(Exception error){abortVoice("Audio de "+modelLabel()+" no disponible: "+error.getClass().getSimpleName());return false;}
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
  if(pcm.length==0){emit(modelLabel()+" sin palabras en este turno; retomando escucha.");listener.onMicrophoneIdle();}
  else{emit("Captura del micrófono de "+modelLabel()+" recibida ("+pcm.length+" bytes PCM).");listener.onMicrophonePcm(pcm);}
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
   emit(modelLabel()+" sin protocolo compatible verificado. No se envió ningún comando.");
   return;
  }
  emit(enabled?"Solicitando encendido de luz a "+modelLabel()+"…":"Solicitando apagado de luz a "+modelLabel()+"…");
  try{caseOp.setFlashlightState(target,enabled,flashlightSetCallback(connectionEpoch));}
  catch(RuntimeException error){emit("No se pudo enviar el comando de luz a "+modelLabel()+": "+error.getClass().getSimpleName());}
 }
 private OnRcspActionCallback<Boolean> flashlightSetCallback(long epoch){
  return new OnRcspActionCallback<Boolean>(){
   @Override public void onSuccess(BluetoothDevice device,Boolean result){
    mainHandler.post(()->{
     if(!isCurrentConnection(device,epoch))return;
     emit(Boolean.TRUE.equals(result)?modelLabel()+" aceptó el cambio de luz. Verificando estado…":modelLabel()+" no confirmó el cambio de luz.");
     if(Boolean.TRUE.equals(result))readFlashlight();
    });
   }
   @Override public void onError(BluetoothDevice device,BaseError error){
    mainHandler.post(()->{
     if(isCurrentConnection(device,epoch))emit(modelLabel()+" rechazó el comando de luz: "+error.getMessage()+" (código "+error.getSubCode()+").");
    });
   }
  };
 }
 public synchronized void readFlashlight(){
  if(closed||!connected||!vendorProtocolVerified||target==null||caseOp==null){emit("Luz no consultada: protocolo de "+modelLabel()+" no verificado.");return;}
  try{caseOp.getFlashlightState(target,flashlightReadCallback(connectionEpoch));}
  catch(RuntimeException error){emit("No se pudo consultar la luz de "+modelLabel()+": "+error.getClass().getSimpleName());}
 }
 private OnRcspActionCallback<Boolean> flashlightReadCallback(long epoch){
  return new OnRcspActionCallback<Boolean>(){
   @Override public void onSuccess(BluetoothDevice device,Boolean on){
    mainHandler.post(()->{
     if(!isCurrentConnection(device,epoch))return;
     emit(on==null?modelLabel()+" no confirmó el estado de la luz.":"Estado real de luz de "+modelLabel()+": "+(on?"ENCENDIDA":"APAGADA")+".");
    });
   }
   @Override public void onError(BluetoothDevice device,BaseError error){
    mainHandler.post(()->{
     if(isCurrentConnection(device,epoch))emit("La luz de "+modelLabel()+" no está soportada o no respondió: "+error.getMessage()+" (código "+error.getSubCode()+").");
    });
   }
  };
 }
 public synchronized void uploadGoroScreenSaverForModel(String model){
  String selected=ZnpDeviceProfile.canonicalModel(model);
  if(selected==null){emit("Modelo ZNP no reconocido para instalar la animación.");return;}
  if(!isConnectedTo(selected)){
   emit("Primero conectá el ZNP "+selected+". No se envió ninguna animación.");
   return;
  }
  if(!connected||!vendorProtocolVerified||target==null||caseOp==null){
   emit(modelLabel()+" no tiene una pantalla RCSP verificada. No se envió ninguna animación.");
   return;
  }
  if(screenInfoReadInProgress){emit("Ya se está leyendo la resolución física del visor "+modelLabel()+".");return;}
  final long epoch=connectionEpoch;
  final BluetoothDevice device=target;
  final ChargingCaseOpImpl operation=caseOp;
  screenInfoReadInProgress=true;
  emit("Consultando la resolución física del visor "+modelLabel()+" antes de seleccionar el GIF…");
  try{
   operation.readScreenInfo(device,new OnRcspActionCallback<ScreenInfo>(){
    @Override public void onSuccess(BluetoothDevice d,ScreenInfo screenInfo){
     mainHandler.post(()->{
      if(epoch!=connectionEpoch)return;
      screenInfoReadInProgress=false;
      if(!isCurrentConnection(d,epoch))return;
      ChargingCaseInfo cached=operation.getChargingCaseInfo(d);
      int width=cached==null?-1:cached.getScreenWidth();
      int height=cached==null?-1:cached.getScreenHeight();
      if(width<=0||height<=0){width=readScreenDimension(screenInfo,true);height=readScreenDimension(screenInfo,false);}
      if(width<=0||height<=0){
       emit("El SDK respondió la lectura del visor pero no entregó una resolución utilizable. No se envió ningún archivo. "+String.valueOf(screenInfo));
       return;
      }
      try{
       File animation=ZnpScreenAsset.forDevice(context,selected,width,height);
       uploadGoroScreenSaver(animation,width,height);
      }catch(Exception error){
       emit("No hay una animación validada para "+modelLabel()+" a "+width+"×"+height+": "+error.getClass().getSimpleName());
      }
     });
    }
    @Override public void onError(BluetoothDevice d,BaseError error){
     mainHandler.post(()->{
      if(epoch!=connectionEpoch)return;
      screenInfoReadInProgress=false;
      if(isCurrentConnection(d,epoch))emit("No se pudo leer la resolución física del visor "+modelLabel()+": "+error.getMessage()+". No se envió ningún archivo.");
     });
    }
   });
  }catch(Exception error){
   screenInfoReadInProgress=false;
   emit("No se pudo consultar la resolución física del visor "+modelLabel()+": "+error.getClass().getSimpleName());
  }
 }
 static int readScreenDimension(Object screenInfo,boolean width){
  if(screenInfo==null)return -1;
  String[] names=width?new String[]{"getScreenWidth","getWidth","getLcdWidth"}:new String[]{"getScreenHeight","getHeight","getLcdHeight"};
  for(String name:names){
   try{
    java.lang.reflect.Method method=screenInfo.getClass().getMethod(name);
    Object value=method.invoke(screenInfo);
    if(value instanceof Number&&((Number)value).intValue()>0)return ((Number)value).intValue();
   }catch(Exception ignored){}
  }
  return -1;
 }
 public synchronized void uploadGoroScreenSaver(File sourceGif){
  if(closed||!connected||!vendorProtocolVerified||target==null||caseOp==null){
   emit(modelLabel()+" sin protocolo de pantalla verificado. No se inició la transferencia.");return;
  }
  ChargingCaseInfo info=caseOp.getChargingCaseInfo(target);
  if(info==null||info.getScreenWidth()<=0||info.getScreenHeight()<=0){
   emit("La resolución real del visor "+modelLabel()+" todavía no está disponible. No se envió ningún archivo.");return;
  }
  uploadGoroScreenSaver(sourceGif,info.getScreenWidth(),info.getScreenHeight());
 }
 private synchronized void uploadGoroScreenSaver(File sourceGif,int screenWidth,int screenHeight){
  if(closed||!connected||!vendorProtocolVerified||target==null||caseOp==null){
   emit(modelLabel()+" sin protocolo de pantalla verificado. No se inició la transferencia.");return;
  }
  if(screenUpload!=null){emit("Ya hay una transferencia de animación en curso.");return;}
  cancelVoice();
  if(sourceGif==null||!sourceGif.isFile()||!sourceGif.canRead()){
   emit("No se encuentra la animación GORO para "+modelLabel()+".");return;
  }
  if(screenWidth<=0||screenHeight<=0){
   emit("La resolución física del visor "+modelLabel()+" no es válida. No se envió ningún archivo.");return;
  }
  try(java.io.FileInputStream input=new java.io.FileInputStream(sourceGif)){
   byte[] header=new byte[10];
   if(input.read(header)!=10||header[0]!='G'||header[1]!='I'||header[2]!='F'){
    emit("El archivo de GORO no es un GIF válido.");return;
   }
   int gifWidth=(header[6]&255)|((header[7]&255)<<8);
   int gifHeight=(header[8]&255)|((header[9]&255)<<8);
   if(gifWidth!=screenWidth||gifHeight!=screenHeight){
    sourceGif=ZnpScreenAsset.forDisplay(context,screenWidth,screenHeight);
   }
  }catch(Exception error){emit("No se pudo verificar la resolución del GIF: "+error.getClass().getSimpleName());return;}
  final File matchedGif=sourceGif;
  final ScreenUpload upload=new ScreenUpload(target,connectionEpoch,caseOp);screenUpload=upload;
  upload.timeout=()->{
   if(!currentScreenUpload(upload))return;
   emit("La transferencia del visor agotó el tiempo. La animación no quedó confirmada.");cancelScreenUpload();
  };
  mainHandler.postDelayed(upload.timeout,120000);
  emit("Preparando animación GORO para el visor "+modelLabel()+" "+screenWidth+"×"+screenHeight+"…");
  try{caseOp.readSDKMessage(upload.device,new OnRcspActionCallback<SDKMessage>(){
   @Override public void onSuccess(BluetoothDevice device,SDKMessage sdk){
    if(!currentScreenUpload(upload)||!upload.device.equals(device))return;
    if(sdk==null||(sdk.getChip()!=SDKMessage.CHIP_701N&&sdk.getChip()!=SDKMessage.CHIP_707N)){
     emit("El visor de "+modelLabel()+" informa chip no compatible con el conversor GIF JieLi 701N/707N (chip="+(sdk==null?"desconocido":sdk.getChip())+"). No se transfirió el archivo.");
     finishScreenUpload(upload);return;
    }
    if(!sdk.isSupportGif()){
     emit("El firmware de "+modelLabel()+" (chip="+sdk.getChip()+") informa isSupportGif=false. El rechazo es del firmware del visor, no de la descarga del GIF. Se necesita firmware compatible; no se envió un archivo que el equipo declaró incompatible.");
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
    if(currentScreenUpload(upload))emit("El conversor GIF JieLi rechazó el archivo para chip "+chip+": "+gifConverterDiagnostic(result)+". El GIF original permanece intacto y no se modificó el estuche.");
    finishScreenUpload(upload);return;
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
 private static String gifConverterDiagnostic(GifConvertResult result){
  if(result==null)return "resultado nulo";
  StringBuilder detail=new StringBuilder(result.getClass().getSimpleName());
  for(String name:new String[]{"getErrorCode","getCode","getMessage","getErrorMsg","getReason"}){
   try{
    java.lang.reflect.Method method=result.getClass().getMethod(name);
    Object value=method.invoke(result);
    if(value!=null&&!String.valueOf(value).trim().isEmpty())
     detail.append(" · ").append(name).append("=").append(String.valueOf(value));
   }catch(Exception ignored){}
  }
  return detail.toString();
 }
 private void beginScreenTransfer(ScreenUpload upload,File output,int byteCount){
  if(!currentScreenUpload(upload)){deletePreparedScreen(output);return;}
  upload.output=output;
  try{
   List<SDCardBean> storages=upload.operation.getOnlineStorages(upload.device);
   SDCardBean flash=null;
   if(storages!=null)for(SDCardBean storage:storages)if(storage.isFlash()&&storage.isOnline()){flash=storage;break;}
   if(flash==null){emit(modelLabel()+" no informa memoria flash disponible. No se transfirió la animación.");finishScreenUpload(upload);return;}
   final SDCardBean selectedFlash=flash;
   FileOpImpl fileOp=upload.operation.getFileOp();upload.fileOperation=fileOp;
   emit("Animación GORO convertida. Transfiriendo "+byteCount+" bytes a "+modelLabel()+"…");
   fileOp.createBigFile(new CreateFileParam(upload.device,output,selectedFlash),new OnTaskStateListener(){
    @Override public void onStart(){if(currentScreenUpload(upload))emit("Transferencia de animación GORO a "+modelLabel()+" iniciada.");}
    @Override public void onProgress(int progress){if(currentScreenUpload(upload))emit(modelLabel()+" · transferencia GORO "+progress+"%.");}
    @Override public void onStop(){
     mainHandler.post(()->{
     if(!currentScreenUpload(upload))return;
     String devicePath="/"+output.getName();
     try{upload.operation.setCurrentScreenSaver(upload.device,selectedFlash.getDevHandler(),devicePath,new OnRcspActionCallback<Boolean>(){
      @Override public void onSuccess(BluetoothDevice device,Boolean ok){
       if(!currentScreenUpload(upload)||!upload.device.equals(device))return;
       if(Boolean.TRUE.equals(ok)){
        emit(modelLabel()+" aceptó la animación GORO. Verificando recurso activo…");
        try{upload.operation.getCurrentScreenSaver(device,new OnRcspActionCallback<com.jieli.bluetooth.bean.settings.v0.ResourceInfo>(){
         @Override public void onSuccess(BluetoothDevice d,com.jieli.bluetooth.bean.settings.v0.ResourceInfo resource){
          if(!currentScreenUpload(upload)||!upload.device.equals(d))return;
          emit(matchesScreenResource(resource,selectedFlash.getDevHandler(),devicePath)
           ?"GORO instalado y seleccionado como salvapantallas. Escucha pausada; el visor puede mantener su pantalla de micrófono hasta volver al inicio."
           :"El visor "+modelLabel()+" informa otro recurso activo. La animación GORO no quedó confirmada.");
          finishScreenUpload(upload);
         }
         @Override public void onError(BluetoothDevice d,BaseError error){
          if(currentScreenUpload(upload)&&upload.device.equals(d)){emit(modelLabel()+" aceptó el cambio, pero no se pudo leer el recurso activo: "+error.getMessage());finishScreenUpload(upload);}
         }
        });}catch(Exception error){emit("No se pudo verificar el recurso del visor: "+error.getClass().getSimpleName());finishScreenUpload(upload);}
       }else{emit(modelLabel()+" no confirmó la activación de la animación GORO.");finishScreenUpload(upload);}
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
 public synchronized void disconnect(){
  if(closed)return;
  if(scanning&&controller!=null){try{controller.stopScan();}catch(RuntimeException ignored){}}
  scanning=false;
  connectionEpoch++;
  releaseVoice();
  cancelScreenUpload();
  releaseCaseObservation();
  if(authPoll!=null)mainHandler.removeCallbacks(authPoll);
  if(authTimeout!=null)mainHandler.removeCallbacks(authTimeout);
  if(connectionCheck!=null)mainHandler.removeCallbacks(connectionCheck);
  if(connectTimeout!=null)mainHandler.removeCallbacks(connectTimeout);
  connectTimeout=null;
  bondSession.close();
  BluetoothDevice previous=target;
  target=null;
  targetDeviceType=-1;
  screenInfoReadInProgress=false;
  connected=false;
  vendorProtocolVerified=false;
  if(controller!=null&&previous!=null)try{controller.disconnectDevice(previous);}catch(RuntimeException ignored){}
  emit("Dispositivo ZNP desconectado desde GORO.");
 }
 public synchronized boolean isConnectedTo(String model){
  String selected=ZnpDeviceProfile.canonicalModel(model);
  return selected!=null&&connected&&target!=null&&activeModel.equals(selected);
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
  if(authPoll!=null)mainHandler.removeCallbacks(authPoll);if(authTimeout!=null)mainHandler.removeCallbacks(authTimeout);if(connectionCheck!=null)mainHandler.removeCallbacks(connectionCheck);if(connectTimeout!=null)mainHandler.removeCallbacks(connectTimeout);connectTimeout=null;
  if(controller!=null){
   if(scanning)controller.stopScan();
   if(callbackRegistered){controller.removeBTRcspEventCallback(eventCallback);callbackRegistered=false;}
   if(target!=null)try{controller.disconnectDevice(target);}catch(RuntimeException ignored){}
  }
  releaseCaseObservation();
  resourceExecutor.shutdownNow();
  scanning=false;connected=false;vendorProtocolVerified=false;target=null;targetDeviceType=-1;screenInfoReadInProgress=false;caseOp=null;
 }
}
