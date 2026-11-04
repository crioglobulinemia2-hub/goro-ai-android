package ar.goro.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.accounts.AccountManager;
import android.location.Location;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import ar.goro.ai.memory.MemoryStore;
import ar.goro.ai.memory.MemoryRecord;
import ar.goro.ai.tools.AssistantCommandParser;
import ar.goro.ai.tools.AlarmClockIntents;
import ar.goro.ai.automation.ReminderScheduler;
import ar.goro.ai.calendar.DeviceCalendarGateway;
import ar.goro.ai.gmail.GoroGmailGateway;
import ar.goro.ai.calendar.CalendarEvent;
import ar.goro.ai.weather.WeatherService;
import ar.goro.ai.weather.DeviceLocationProvider;
import ar.goro.ai.core.NemotronGateway;
import ar.goro.ai.core.NvidiaCredentialStore;
import ar.goro.ai.core.NemotronSpeechGateway;
import ar.goro.ai.core.ParakeetAsrGateway;
import ar.goro.ai.core.SpeechTextChunks;
import ar.goro.ai.voice.Z02Player;
import android.bluetooth.BluetoothDevice;
import ar.goro.ai.avatar.GoroAvatarView;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;
import android.content.SharedPreferences;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import android.provider.AlarmClock;
import android.provider.Settings;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.*;
import android.text.InputType;
import java.util.*;
import java.util.regex.*;

public class MainActivity extends Activity {
  private TextView output, state;
  private ar.goro.ai.ui.GoroDashboardView dashboard;
  private GoroZn02Controller zn02Controller;
  private GoroZn02GattInspector zn02GattInspector;
  private boolean pendingZn02Inspection=false;
  private static final int ZN02_BLE_PERMISSION_REQUEST=65;
  private SharedPreferences memory;
  private MemoryStore memoryStore;
  private boolean voiceActive=false;
  private boolean zn02ReadyForVoice=false;
  private boolean nvidiaChatReady=false;
  private boolean magpieDiegoReady=false;
  private ReminderScheduler reminderScheduler;
  private DeviceCalendarGateway calendarGateway;
  private GoroGmailGateway gmailGateway;
  private WeatherService weatherService;
  private DeviceLocationProvider locationProvider;
  private NvidiaCredentialStore nvidiaCredentialStore;
  private NemotronGateway nemotronGateway;
  private NemotronSpeechGateway speechGateway;
  private ParakeetAsrGateway asrGateway;
  private Z02Player z02Player;
  private String pendingSpeechText="";
  private byte[] pendingSpeechWav;
  private final ArrayDeque<String> speechChunks=new ArrayDeque<>();
  private long speechGeneration;
  private boolean speechInProgress;
  private final android.os.Handler voiceRetryHandler=new android.os.Handler(android.os.Looper.getMainLooper());
  private Runnable pendingVoiceRetry;
  private boolean foreground;
  private boolean pendingZn02Connection=false;
  private TextView calendarAccountLabel;
  private boolean pendingWeatherForecast=false;
  private CalendarEvent pendingCalendarEvent;
  private String pendingCalendarDeleteId;
  private final java.util.List<CalendarEvent> lastCalendarEvents=new java.util.ArrayList<>();
  private static final int CALENDAR_PERMISSION_REQUEST=62;
  private static final int GOOGLE_ACCOUNT_REQUEST=63;
  private static final int LOCATION_PERMISSION_REQUEST=64;
  @Override public void onCreate(Bundle saved) {
    super.onCreate(saved);
    memory=getSharedPreferences("goro_memory",MODE_PRIVATE);
    memoryStore=new MemoryStore(this);
    reminderScheduler=new ReminderScheduler(this);
    weatherService=new WeatherService();
    locationProvider=new DeviceLocationProvider(this);
    z02Player=new Z02Player(this,new Z02Player.Listener(){
      public void onState(String value){
        if(state!=null)state.setText("GORO · "+value);
        if("Reproduciendo por Z02".equals(value)&&dashboard!=null)dashboard.avatarSpeak(pendingSpeechText,pendingSpeechWav);
      }
      public void onError(String error){
        speechInProgress=false;speechChunks.clear();pendingSpeechWav=null;
        if(state!=null)state.setText("GORO · Audio Z02 no disponible");
        if(output!=null)output.setText(output.getText()+"\n\n"+error);
        if(dashboard!=null)dashboard.showAvatarMood(GoroAvatarView.Mood.ALERT);
        if(voiceActive)startZ02Listening();
      }
      public void onCompleted(){
        if(!speechChunks.isEmpty()){speakNextChunk(speechGeneration);return;}
        speechInProgress=false;pendingSpeechWav=null;
        if(state!=null)state.setText(voiceActive?"GORO · Escuchando desde Z02":"GORO · Listo");
        if(dashboard!=null)dashboard.showAvatarMood(voiceActive?GoroAvatarView.Mood.LISTENING:GoroAvatarView.Mood.IDLE);
        if(voiceActive)startZ02Listening();
      }
    });
    nvidiaCredentialStore=new NvidiaCredentialStore(this);
    try{
      ar.goro.ai.core.PersonalNvidiaSetup.importIfPresent(this,nvidiaCredentialStore);
      String savedNvidiaKey=nvidiaCredentialStore.read();
      if(savedNvidiaKey!=null){
        nemotronGateway=new NemotronGateway(savedNvidiaKey);
        speechGateway=new NemotronSpeechGateway(savedNvidiaKey);
        asrGateway=new ParakeetAsrGateway(savedNvidiaKey);
      }
    }catch(Exception credentialError){nvidiaCredentialStore.clear();}
    calendarGateway=new DeviceCalendarGateway(this);
    gmailGateway=new GoroGmailGateway(this);
    gmailGateway.setSelectedAccount(calendarGateway.selectedGoogleAccount());
    zn02Controller=new GoroZn02Controller(this,new GoroZn02Controller.Listener(){
      public void onStatus(String message){
        if(message!=null){
          String normalized=message.toLowerCase(Locale.ROOT);
          if(normalized.contains("desconectado")||normalized.contains("conexión fallida")||normalized.contains("conexión rechazada")){
            zn02ReadyForVoice=false;
            invalidateVoiceConversation();
          }
        }
        if(dashboard!=null)dashboard.showDeviceStatus(message);
      }
      public void onMicrophonePcm(byte[] pcm){handleZ02Pcm(pcm);}
      public void onMicrophoneStarted(){
        if(!foreground||!voiceActive||speechInProgress)return;
        if(state!=null)state.setText("GORO · Escuchando desde el micrófono Z02");
        if(dashboard!=null)dashboard.showAvatarMood(GoroAvatarView.Mood.LISTENING);
      }
      public void onMicrophoneIdle(){scheduleZ02ListenRetry();}
      public void onMicrophoneError(String error){
        invalidateVoiceConversation();
        if(state!=null)state.setText("GORO · Micrófono Z02 no disponible");
        if(dashboard!=null){dashboard.showAvatarMood(GoroAvatarView.Mood.ALERT);dashboard.showDeviceStatus(error);}
      }
      public void onAudioTarget(BluetoothDevice audioTarget){
        invalidateVoiceConversation();
        try{
          z02Player.setTarget(audioTarget.getAddress(),audioTarget.getName());
          zn02ReadyForVoice=true;
          if(!nvidiaChatReady||!magpieDiegoReady)verifyNvidiaServices();
          else maybeStartHandsFreeConversation();
        }catch(SecurityException error){
          zn02ReadyForVoice=false;
          if(dashboard!=null)dashboard.showDeviceStatus("Android no autorizó identificar la salida de audio Z02.");
        }
      }
    });
    zn02GattInspector=new GoroZn02GattInspector(this,message->{if(dashboard!=null)dashboard.showDeviceStatus(message);});
    memoryStore.migrateLegacy(memory);
    dashboard=new ar.goro.ai.ui.GoroDashboardView(this,new ar.goro.ai.ui.GoroDashboardView.Actions(){
      public void onCommand(String text){runCommand(text);}
      public void onLinkAccount(){chooseGoogleAccount();}
      public void onReadGmail(){readGmail();}
      public void onCalendarPermission(){
        if(calendarGateway.selectedGoogleAccount()==null)chooseGoogleAccount();
        else requestCalendarPermissions();
      }
      public void onUnlinkAccount(){
        calendarGateway.unlinkGoogleAccount();
        gmailGateway.setSelectedAccount(null);
        dashboard.showGmailStatus("Gmail · cuenta desvinculada");
        calendarAccountLabel.setText("Cuenta Google: sin vincular");
        lastCalendarEvents.clear();pendingCalendarEvent=null;pendingCalendarDeleteId=null;
        say("Cuenta desvinculada. No se eliminaron eventos.");
      }
      public void onWeather(){requestWeatherLocation(false);}
      public void onForecast(){requestWeatherLocation(true);}
      public void onConfigureNvidia(){configureNvidia();}
      public void onTestNvidia(){testNemotron();}
      public void onConnectZn02(){connectZn02();}
      public void onInspectZn02(){inspectZn02();}
      public void onZn02LightOn(){if(zn02Controller!=null)zn02Controller.setFlashlight(true);}
      public void onZn02LightOff(){if(zn02Controller!=null)zn02Controller.setFlashlight(false);}
      public void onZn02ReadLight(){if(zn02Controller!=null)zn02Controller.readFlashlight();}
      public void onZn02AudioOn(){if(dashboard!=null)dashboard.showDeviceStatus(z02Player!=null&&!z02Player.outputs().isEmpty()?"Salida multimedia Z02 disponible y verificada por Android.":"La salida multimedia Z02 todavía no está disponible.");}
      public void onZn02AudioOff(){if(z02Player!=null)z02Player.stop();if(dashboard!=null)dashboard.showDeviceStatus("Reproducción Z02 detenida.");}
      public void onInstallGoroAnimation(){installGoroAnimationOnZn02();}
      public void onReminders(){
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
          requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},52);
      }
    },calendarGateway.selectedGoogleAccount());
    output=dashboard.output;
    state=dashboard.state;
    calendarAccountLabel=dashboard.accountLabel;
    dashboard.showNvidiaStatus(nemotronGateway==null?"NVIDIA NIM: requiere API key":"NVIDIA NIM: clave guardada · sin prueba de conexión");
    dashboard.showVoiceStatus(speechGateway!=null?"NVIDIA Magpie · voz Diego · pendiente de prueba":"NVIDIA Magpie: falta API key · salida de voz deshabilitada");
    setContentView(dashboard);
    connectZn02();
  }
  @Override protected void onActivityResult(int request,int result,Intent data) {
    super.onActivityResult(request,result,data);
    if(request==GoroGmailGateway.REQUEST_AUTHORIZATION){
      if(gmailGateway!=null)gmailGateway.onActivityResult(request,result,data);
      return;
    }
    if(request==GOOGLE_ACCOUNT_REQUEST){
      if(result==RESULT_OK&&data!=null){String email=data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);String type=data.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE);if(email==null||!"com.google".equals(type)){say("No se seleccionó una cuenta Google válida.");return;}try{calendarGateway.setSelectedGoogleAccount(email);gmailGateway.setSelectedAccount(email);dashboard.showGmailStatus("Gmail · cuenta seleccionada: "+email+" · requiere autorización");calendarAccountLabel.setText("Google Calendar: "+email);say("Cuenta "+email+" seleccionada para GORO. Ahora autorizá el acceso al calendario.");requestCalendarPermissions();}catch(Exception e){say("No pude vincular esa cuenta Google.");}}
      else say("Vinculación de Gmail cancelada.");
    }
  }
  private void say(String message) {
    if(output!=null)output.setText(message);
    if(dashboard!=null)dashboard.showAssistantMessage(message);
    if(!foreground)return;
    if(speechGateway==null){
      if(state!=null)state.setText("GORO · NVIDIA Magpie no configurado");
      if(output!=null)output.setText(message+"\n\nLa respuesta queda en pantalla; no se usa la tablet como salida de voz.");
      if(voiceActive){voiceActive=false;if(dashboard!=null)dashboard.setVoiceActive(false);if(zn02Controller!=null)zn02Controller.stopVoice();}
      return;
    }
    speechInProgress=true;cancelVoiceRetry();
    if(voiceActive&&zn02Controller!=null)zn02Controller.cancelVoice();
    if(z02Player!=null)z02Player.stop();
    speechGeneration++;
    speechChunks.clear();
    speechChunks.addAll(SpeechTextChunks.split(message,1600));
    speechGateway.cancelPending();
    speakNextChunk(speechGeneration);
  }
  private void speakNextChunk(long generation){
    if(!foreground||generation!=speechGeneration||isFinishing()||isDestroyed())return;
    if(speechChunks.isEmpty()){speechInProgress=false;if(voiceActive)startZ02Listening();return;}
    String chunk=speechChunks.removeFirst();
    pendingSpeechText=chunk;pendingSpeechWav=null;
    if(state!=null)state.setText("GORO · Preparando voz Diego");
    if(dashboard!=null)dashboard.showAvatarMood(GoroAvatarView.Mood.THINKING);
    speechGateway.synthesize(chunk,(wav,error)->{
      if(!foreground||generation!=speechGeneration||isFinishing()||isDestroyed())return;
      if(error!=null||wav==null){
        speechInProgress=false;speechChunks.clear();
        if(state!=null)state.setText("GORO · Magpie no disponible");
        if(dashboard!=null)dashboard.showAvatarMood(GoroAvatarView.Mood.ALERT);
        if(output!=null)output.append("\n\nNo se pudo completar la voz NVIDIA: "+(error==null?"respuesta vacía":error));
        if(voiceActive)startZ02Listening();
        return;
      }
      if(z02Player==null){speechInProgress=false;speechChunks.clear();if(voiceActive)startZ02Listening();return;}
      pendingSpeechWav=wav;
      z02Player.playWav(wav);
    });
  }
  private void startZ02Listening(){
    if(!foreground||!voiceActive||speechInProgress||zn02Controller==null)return;
    cancelVoiceRetry();
    if(!zn02Controller.startVoice()){
      voiceActive=false;
      if(dashboard!=null)dashboard.setVoiceActive(false);
      if(state!=null)state.setText("GORO · Micrófono Z02 no disponible");
      return;
    }
    if(state!=null)state.setText("GORO · Abriendo micrófono Z02");
  }
  private void cancelVoiceRetry(){
    if(pendingVoiceRetry!=null)voiceRetryHandler.removeCallbacks(pendingVoiceRetry);
    pendingVoiceRetry=null;
  }
  private void scheduleZ02ListenRetry(){
    if(!foreground||!voiceActive||speechInProgress||pendingVoiceRetry!=null)return;
    final long generation=speechGeneration;
    pendingVoiceRetry=()->{
      pendingVoiceRetry=null;
      if(generation!=speechGeneration||!foreground||!voiceActive||speechInProgress)return;
      startZ02Listening();
    };
    voiceRetryHandler.postDelayed(pendingVoiceRetry,500);
  }
  private void invalidateVoiceConversation(){
    voiceActive=false;speechInProgress=false;
    speechGeneration++;speechChunks.clear();pendingSpeechWav=null;cancelVoiceRetry();
    if(asrGateway!=null)asrGateway.cancelPending();
    if(nemotronGateway!=null)nemotronGateway.cancelPending();
    if(speechGateway!=null)speechGateway.cancelPending();
    if(zn02Controller!=null)zn02Controller.cancelVoice();
    if(z02Player!=null)z02Player.stop();
    if(dashboard!=null)dashboard.setVoiceActive(false);
  }
  private void handleZ02Pcm(byte[] pcm){
    if(!foreground||!voiceActive||speechInProgress)return;
    if(asrGateway==null){say("NVIDIA ASR no está configurado.");return;}
    if(state!=null)state.setText("GORO · Transcribiendo voz Z02 con NVIDIA");
    if(dashboard!=null)dashboard.showAvatarMood(GoroAvatarView.Mood.THINKING);
    final long generation=speechGeneration;
    asrGateway.transcribe(pcm,16000,(transcript,error)->{
      if(!foreground||!voiceActive||speechInProgress||generation!=speechGeneration)return;
      if(error!=null||transcript==null||transcript.trim().isEmpty()){
        if(output!=null)output.setText("No pude reconocer la frase del Z02: "+(error==null?"audio sin transcripción":error));
        startZ02Listening();return;
      }
      runCommand(transcript);
    });
  }
  private void runCommand(String raw) {
    if(raw.isEmpty()) return;
    String text=raw.toLowerCase(new Locale("es","AR")).trim();
    if(text.equals("confirmar")||text.equals("confirmá")){confirmCalendarAction();return;}
    Matcher emailNumber=Pattern.compile("^(?:(?:leeme|léeme|lee|leé|leer|abrí|abrir|mostrame|mostrar)\\s+)?(?:el\\s+)?(?:correo|mail|mensaje)(?:\\s+(?:n[uú]mero|nro))?\\s+(\\d{1,2})$").matcher(text);
    if(emailNumber.matches()){
      int number=Integer.parseInt(emailNumber.group(1));
      boolean aloud=text.startsWith("lee")||text.startsWith("leé")||text.startsWith("léeme");
      openGmailMessage(number,aloud);
      return;
    }
    Matcher gmailSearch=Pattern.compile("^(?:busc[aá]|buscar|encontr[aá]|encontrar) (?:en )?(?:gmail|correos|mails|emails)(?: (?:sobre|con|de))? (.+)$").matcher(text);
    if(gmailSearch.matches()){
      searchGmail(gmailSearch.group(1));
      return;
    }
    if(text.equals("gmail")||text.contains("mis correos")||text.contains("mis mails")
       ||text.contains("bandeja de entrada")||text.contains("leer correos")
       ||text.contains("leé los mails")||text.contains("últimos correos")){
      readGmail();
      return;
    }
    if(text.equals("clima")||text.equals("clima ahora")||text.equals("clima actual")||text.equals("qué clima hace")||text.equals("que clima hace")||text.equals("tiempo")||text.equals("temperatura")||text.equals("clima de acá")||text.equals("clima de aca")){requestWeatherLocation(false);return;}
    if(text.equals("pronóstico")||text.equals("pronostico")||text.equals("pronóstico de tres días")||text.equals("pronostico de tres dias")){requestWeatherLocation(true);return;}
    if(text.equals("cancelar")||text.equals("cancelá")){pendingCalendarEvent=null;pendingCalendarDeleteId=null;say("Operación de agenda cancelada.");return;}
    if(text.startsWith("cancelá recordatorio recurrente ")||text.startsWith("cancela recordatorio recurrente ")){String label=raw.substring(raw.toLowerCase(new Locale("es","AR")).indexOf("recurrente ")+11).trim();String key="repeat-"+Integer.toHexString(label.toLowerCase(Locale.ROOT).hashCode());reminderScheduler.cancel(key);say("Cancelé el recordatorio recurrente "+label+".");return;}
    if(text.startsWith("pronóstico en ")||text.startsWith("pronostico en ")){showForecast(raw.substring(raw.toLowerCase(new Locale("es","AR")).indexOf(" en ")+4).trim());return;}
    if(text.startsWith("clima en ")||text.startsWith("qué clima hace en ")||text.startsWith("que clima hace en ")){showWeather(raw.substring(raw.toLowerCase(new Locale("es","AR")).indexOf(" en ")+4).trim());return;}
    if(text.matches(".*(agenda|calendario|eventos).*(hoy|mañana|semana).*")||text.matches(".*(qué tengo|que tengo|mis eventos).*(hoy|mañana).*")){showCalendar(text);return;}
    if(text.startsWith("agendá ")||text.startsWith("agenda ")||text.startsWith("creá evento ")||text.startsWith("crea evento ")){prepareCalendarEvent(raw);return;}
    if(text.startsWith("reprogramá evento ")||text.startsWith("reprograma evento ")){prepareCalendarUpdate(raw);return;}
    if(text.startsWith("eliminá evento ")||text.startsWith("elimina evento ")){prepareCalendarDelete(raw);return;}
    if(text.startsWith("acordate que ") || text.startsWith("recordá que ") || text.startsWith("recorda que ")) {
      String fact=raw.substring(raw.toLowerCase(new Locale("es","AR")).indexOf(" que ")+5).trim();
      if(fact.length()<3){say("Decime qué querés que recuerde.");return;}
      try{memoryStore.add("general",fact);say("Guardé el recuerdo en este dispositivo.");}
      catch(Exception e){say("No pude guardar ese recuerdo.");}
    } else if(text.contains("qué recordás") || text.contains("que recordas") || text.contains("mis recuerdos")) {
      try{
        java.util.List<MemoryRecord> records=memoryStore.recent(8);
        if(records.isEmpty()){say("Todavía no tengo recuerdos guardados.");return;}
        StringBuilder list=new StringBuilder("Recuerdo lo siguiente: ");
        for(MemoryRecord record:records)list.append(record.content).append(". ");
        say(list.toString());
      }catch(Exception e){say("No pude recuperar la memoria.");}
    } else if(text.startsWith("buscá en recuerdos ") || text.startsWith("busca en recuerdos ")) {
      String query=raw.substring(raw.toLowerCase(new Locale("es","AR")).indexOf("recuerdos ")+10).trim();
      try{java.util.List<MemoryRecord> found=memoryStore.search(query,8);if(found.isEmpty()){say("No encontré recuerdos sobre "+query+".");return;}StringBuilder b=new StringBuilder("Encontré: ");for(MemoryRecord record:found)b.append("Número ").append(record.id).append(": ").append(record.content).append(". ");say(b.toString());}
      catch(Exception e){say("No pude buscar en la memoria.");}
    } else if(text.startsWith("borrá recuerdo ") || text.startsWith("borra recuerdo ")) {
      try{long id=Long.parseLong(text.replaceAll("[^0-9]",""));say(memoryStore.delete(id)?"Recuerdo eliminado.":"No encontré ese número de recuerdo.");}
      catch(Exception e){say("Decime el número del recuerdo que querés borrar.");}
    } else if(text.startsWith("editá recuerdo ") || text.startsWith("edita recuerdo ")) {
      Matcher edit=Pattern.compile("(?i)edit[aá] recuerdo (\\d+) (.+)").matcher(raw);
      if(!edit.matches()){say("Decime: editá recuerdo, número y nuevo texto.");return;}
      try{boolean ok=memoryStore.update(Long.parseLong(edit.group(1)),"general",edit.group(2));say(ok?"Recuerdo actualizado.":"No encontré ese número de recuerdo.");}
      catch(Exception e){say("No pude actualizar ese recuerdo.");}
    } else if(text.contains("borrá todos los recuerdos") || text.contains("borra todos los recuerdos")) {
      try{int deleted=memoryStore.clearAll();say("Eliminé "+deleted+" recuerdos locales.");}
      catch(Exception e){say("No pude eliminar la memoria.");}
    } else if(text.startsWith("recordame cada ") || text.startsWith("recordáme cada ") || text.startsWith("recuérdame cada ")) {
      Matcher recurring=Pattern.compile("(?i)(?:recordame|recordáme|recuérdame) cada (\\d+) (minutos?|horas?|d[ií]as?) (?:que |para )?(.+)").matcher(raw);
      if(!recurring.matches()){say("Decime: recordame cada 2 horas que tengo que revisar el correo.");return;}
      long amount;try{amount=Long.parseLong(recurring.group(1));}catch(Exception e){say("Intervalo inválido.");return;}
      String unitText=recurring.group(2).toLowerCase(Locale.ROOT);TimeUnit unit=unitText.startsWith("min")?TimeUnit.MINUTES:unitText.startsWith("hora")?TimeUnit.HOURS:TimeUnit.DAYS;
      String title=recurring.group(3).trim();if(title.isEmpty()){say("Falta el contenido del recordatorio.");return;}
      if(Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=PackageManager.PERMISSION_GRANTED){say("Primero activá las notificaciones con el botón Activar recordatorios.");return;}
      String id="repeat-"+Integer.toHexString(title.toLowerCase(Locale.ROOT).hashCode());
      try{reminderScheduler.recurring(id,"GORO AI: "+title,"Recordatorio recurrente",amount,unit);say("Programé el recordatorio recurrente cada "+amount+" "+unitText+".");}
      catch(Exception e){say("No pude programar ese intervalo. El mínimo es 15 minutos.");}
    } else if(text.startsWith("recordame en ") || text.startsWith("recordáme en ") || text.startsWith("recuérdame en ")) {
      Matcher reminder=Pattern.compile("(?i)(?:recordame|recordáme|recuérdame) en (\\d+) (segundos?|minutos?|horas?) (?:que |para )?(.+)").matcher(raw);
      if(!reminder.matches()){say("Decime: recordame en 20 minutos que tengo que salir.");return;}
      long amount;try{amount=Long.parseLong(reminder.group(1));}catch(Exception e){say("Duración inválida.");return;}
      String unit=reminder.group(2).toLowerCase(Locale.ROOT);long seconds=amount*(unit.startsWith("hora")?3600:unit.startsWith("minuto")?60:1);
      if(seconds<1||seconds>604800){say("El recordatorio debe ser dentro de los próximos siete días.");return;}
      if(Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=PackageManager.PERMISSION_GRANTED){say("Primero activá las notificaciones con el botón Activar recordatorios.");return;}
      String title=reminder.group(3).trim();if(title.isEmpty()){say("Falta el contenido del recordatorio.");return;}
      try{String id=java.util.UUID.randomUUID().toString();reminderScheduler.once(id,"GORO AI: "+title,"Recordatorio programado",seconds,TimeUnit.SECONDS);say("Programé el recordatorio para dentro de "+amount+" "+unit+".");}
      catch(Exception e){say("No pude programar el recordatorio.");}
    } else if(AssistantCommandParser.requestsAlarmList(text)) {
      Intent show=AlarmClockIntents.showAlarms();
      try{startActivity(show);say("Abrí la lista de alarmas del teléfono.");}
      catch(Exception e){say("El reloj del teléfono no permite mostrar las alarmas.");}
    } else if(AssistantCommandParser.requestsTimerList(text)) {
      Intent show=AlarmClockIntents.showTimers();
      try{startActivity(show);say("Abrí la lista de temporizadores del teléfono.");}
      catch(Exception e){say("El reloj del teléfono no permite mostrar los temporizadores.");}
    } else if(text.contains("alarma") || text.contains("despert")) {
      AssistantCommandParser.Alarm parsed=AssistantCommandParser.alarm(text);
      if(parsed==null){say("Decime una hora válida, por ejemplo siete y treinta usando 07:30.");return;}
      Intent alarm=AlarmClockIntents.setAlarm(parsed);
      try {startActivity(alarm);say("Abrí el reloj para configurar la alarma de "+parsed.hour+":"+String.format(Locale.ROOT,"%02d",parsed.minute)+". Confirmá el guardado en el teléfono.");}
      catch(Exception e){say("No pude abrir la aplicación de reloj.");}
    } else if(text.contains("temporizador")||text.contains("cuenta regresiva")) {
      AssistantCommandParser.Timer parsed=AssistantCommandParser.timer(text);
      if(parsed==null){say("Decime una duración válida en segundos, minutos u horas.");return;}
      Intent timer=AlarmClockIntents.setTimer(parsed);
      try {startActivity(timer);say("Abrí el reloj para configurar el temporizador de "+parsed.seconds+" segundos. Confirmá que haya comenzado.");}
      catch(Exception e){say("No pude abrir el temporizador del teléfono.");}
    } else if(text.contains("hora")) {
      say("Son las "+new java.text.SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date()));
    } else if(text.matches("\\s*-?\\d+(?:[.,]\\d+)?\\s*[+*/-]\\s*-?\\d+(?:[.,]\\d+)?\\s*")) {
      try {
        Matcher m=Pattern.compile("(-?\\d+(?:\\.\\d+)?)([+*/-])(-?\\d+(?:\\.\\d+)?)").matcher(text.replace(',','.').replace(" ",""));
        if(!m.matches()) throw new IllegalArgumentException();
        double a=Double.parseDouble(m.group(1)),b=Double.parseDouble(m.group(3)),value;
        switch(m.group(2)){case "+":value=a+b;break;case "-":value=a-b;break;case "*":value=a*b;break;default:if(b==0){say("No se puede dividir por cero.");return;}value=a/b;}
        say("El resultado es "+value);
      }catch(Exception e){say("No pude resolver esa operación.");}
    } else {
      if(nemotronGateway==null){say("Nemotron necesita una API key de NVIDIA NIM. Abrí Ajustes y configurá la clave para habilitar la conversación.");return;}
      requestNemotron(raw);
    }
  }
  private void configureNvidia(){
    EditText input=new EditText(this);
    input.setSingleLine(true);
    input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
    input.setHint("nvapi-…");
    input.setPadding(36,24,36,24);
    new AlertDialog.Builder(this).setTitle("Conectar NVIDIA Nemotron")
      .setMessage("La clave se cifra con Android Keystore y se guarda solo en este dispositivo. No se publica en GitHub. Una edición personal puede traerla preconfigurada.")
      .setView(input)
      .setNegativeButton("Cancelar",null)
      .setPositiveButton("Guardar y probar",(dialog,which)->{
       String key=input.getText().toString().trim();
       try{
        nvidiaCredentialStore.save(key);
        invalidateVoiceConversation();
        if(nemotronGateway!=null)nemotronGateway.close();
        if(speechGateway!=null)speechGateway.close();
        if(asrGateway!=null)asrGateway.close();
        nemotronGateway=new NemotronGateway(key);
        speechGateway=new NemotronSpeechGateway(key);
        asrGateway=new ParakeetAsrGateway(key);
        if(dashboard!=null)dashboard.showNvidiaStatus("NVIDIA NIM: clave cifrada · verificando…");
        if(dashboard!=null)dashboard.showVoiceStatus("Magpie Diego: consultando catálogo NVIDIA…");
        verifyNvidiaServices();
       }catch(Exception e){if(dashboard!=null)dashboard.showNvidiaStatus("NVIDIA NIM: clave no guardada");say("No pude guardar la clave NVIDIA. Verificá que sea una API key nvapi válida.");}
      }).show();
  }
  private void testNemotron(){
    verifyNvidiaServices();
  }
  private void verifyNvidiaServices(){
    if(nemotronGateway==null||speechGateway==null||asrGateway==null){
      nvidiaChatReady=false;magpieDiegoReady=false;
      if(dashboard!=null)dashboard.showVoiceStatus("Falta configurar NVIDIA NIM para la conversación automática.");
      return;
    }
    nvidiaChatReady=false;magpieDiegoReady=false;
    if(dashboard!=null){
      dashboard.showNvidiaStatus("NVIDIA Nemotron 3.5 · verificando conexión real…");
      dashboard.showVoiceStatus("Magpie Diego · verificando catálogo de voces…");
    }
    nemotronGateway.request("goro-healthcheck","Respondé exactamente: GORO conectado.",(reply,error)->{
      nvidiaChatReady=error==null&&reply!=null&&!reply.trim().isEmpty();
      if(dashboard!=null)dashboard.showNvidiaStatus(nvidiaChatReady
        ?"NVIDIA NIM conectado · "+NemotronGateway.MODEL
        :"NVIDIA NIM sin conexión · "+(error==null?"respuesta vacía":error));
      maybeStartHandsFreeConversation();
    });
    speechGateway.verifyDiego((ready,detail)->{
      magpieDiegoReady=ready;
      if(dashboard!=null)dashboard.showVoiceStatus(detail);
      maybeStartHandsFreeConversation();
    });
  }
  private void maybeStartHandsFreeConversation(){
    if(!foreground)return;
    if(voiceActive||!zn02ReadyForVoice)return;
    if(!nvidiaChatReady||!magpieDiegoReady||asrGateway==null){
      if(dashboard!=null)dashboard.showDeviceStatus("Z02 conectado. La conversación manos libres espera la validación de Nemotron, Parakeet y Magpie Diego.");
      return;
    }
    if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
      requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},41);return;
    }
    voiceActive=true;
    if(dashboard!=null)dashboard.setVoiceActive(true);
    startZ02Listening();
  }
  private void requestNemotron(String text){
    if(dashboard!=null)dashboard.showNvidiaStatus("NVIDIA NIM: procesando…");
    output.setText("GORO está consultando Nemotron…");
    if(dashboard!=null)dashboard.showAvatarMood(GoroAvatarView.Mood.THINKING);
    String prompt=text;
    try{
      java.util.List<MemoryRecord> saved=memoryStore.recent(8);
      if(!saved.isEmpty()){
        StringBuilder context=new StringBuilder("MEMORIA PERSISTENTE DEL USUARIO (guardada localmente). Usala solo cuando sea pertinente; no inventes recuerdos ni la repitas sin necesidad.\n");
        int budget=Math.max(0,3800-text.length()-context.length()-40);
        for(MemoryRecord record:saved){
          String fact=record.content==null?"":record.content.trim();
          if(fact.isEmpty())continue;
          if(fact.length()>budget)continue;
          context.append("- ").append(fact).append("\n");
          budget-=fact.length()+3;
          if(budget<=0)break;
        }
        if(context.length()>140)prompt=context+"\nMENSAJE ACTUAL DEL USUARIO:\n"+text;
      }
    }catch(Exception ignored){}
    nemotronGateway.request("goro-main",prompt,(reply,error)->{
      if(error!=null){if(dashboard!=null)dashboard.showNvidiaStatus("NVIDIA NIM: error · "+error);say("La consulta a Nemotron falló. "+error);}
      else{if(dashboard!=null)dashboard.showNvidiaStatus("NVIDIA NIM: conectado");say(reply);}
    });
  }
  private void installGoroAnimationOnZn02(){
    if(zn02Controller==null){say("El controlador propio del ZN02 no está disponible.");return;}
    String[] parts={"znp_faces_00.b64","znp_faces_00b.b64","znp_faces_01.b64","znp_faces_02.b64","znp_faces_03.b64","znp_faces_04.b64","znp_faces_05.b64","znp_faces_05b.b64","znp_faces_06.b64","znp_faces_07.b64","znp_faces_08.b64","znp_faces_09.b64","znp_faces_10.b64","znp_faces_11.b64","znp_faces_12.b64","znp_faces_12b.b64","znp_faces_13.b64","znp_faces_14.b64","znp_faces_15.b64","znp_faces_16.b64","znp_faces_17.b64"};
    try{
      StringBuilder encoded=new StringBuilder();
      for(String part:parts){
        try(InputStream input=getAssets().open("goro/"+part);ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
          byte[] buffer=new byte[4096];int count;
          while((count=input.read(buffer))!=-1)bytes.write(buffer,0,count);
          encoded.append(bytes.toString("US-ASCII"));
        }
      }
      byte[] gif=Base64.decode(encoded.toString(),Base64.DEFAULT);
      File file=new File(getCacheDir(),"GORO-ZN02-320x172.gif");
      try(FileOutputStream outputFile=new FileOutputStream(file)){outputFile.write(gif);outputFile.flush();}
      zn02Controller.uploadGoroScreenSaver(file);
    }catch(Exception error){
      if(dashboard!=null)dashboard.showDeviceStatus("No se pudo preparar la animación GORO para el ZN02: "+error.getClass().getSimpleName());
    }
  }
  private void inspectZn02(){
    if(zn02GattInspector==null){if(dashboard!=null)dashboard.showDeviceStatus("Inspector BLE del ZN02 no disponible.");return;}
    String[] missing=zn02GattInspector.neededPermissions();
    if(missing.length>0){pendingZn02Inspection=true;requestPermissions(missing,ZN02_BLE_PERMISSION_REQUEST);return;}
    pendingZn02Inspection=false;zn02GattInspector.scan();
  }
  private void connectZn02(){
    if(zn02Controller==null){say("El controlador Bluetooth Z02 no está disponible.");return;}
    String[] missing=zn02Controller.neededPermissions();
    if(missing.length>0){pendingZn02Connection=true;requestPermissions(missing,ZN02_BLE_PERMISSION_REQUEST);return;}
    pendingZn02Connection=false;
    zn02Controller.startScan();
  }
  @Override protected void onResume(){
    super.onResume();
    foreground=true;
    // A permission/account screen may have interrupted the initial health
    // checks. Retry on return rather than leaving hands-free mode waiting
    // forever for callbacks that onStop correctly cancelled.
    if(nemotronGateway!=null&&(!nvidiaChatReady||!magpieDiegoReady))verifyNvidiaServices();
    if(zn02ReadyForVoice&&nvidiaChatReady&&magpieDiegoReady&&!voiceActive)maybeStartHandsFreeConversation();
    if(pendingZn02Connection&&zn02Controller!=null){
      String[] missing=zn02Controller.neededPermissions();
      if(missing.length>0)requestPermissions(missing,ZN02_BLE_PERMISSION_REQUEST);
      else{pendingZn02Connection=false;zn02Controller.startScan();}
    }
    if(pendingZn02Inspection&&zn02GattInspector!=null){
      String[] missing=zn02GattInspector.neededPermissions();
      if(missing.length>0)requestPermissions(missing,ZN02_BLE_PERMISSION_REQUEST);
      else{pendingZn02Inspection=false;zn02GattInspector.scan();}
    }
  }
  private void searchGmail(String query){
    if(gmailGateway==null){if(dashboard!=null)dashboard.showGmailStatus("Gmail no disponible");return;}
    if(dashboard!=null)dashboard.showGmailStatus("Buscando correos con autorización Google…");
    gmailGateway.searchInbox(query,(text,error)->{
      String result=error==null?text:"Gmail · "+error;
      if(dashboard!=null)dashboard.showGmailStatus(result);
      if(output!=null)output.setText(result);
    });
  }
  private void readGmail(){
    if(gmailGateway==null){if(dashboard!=null)dashboard.showGmailStatus("Gmail no disponible");return;}
    if(dashboard!=null)dashboard.showGmailStatus("Solicitando autorización Gmail…");
    gmailGateway.readInbox((text,error)->{
      if(dashboard!=null)dashboard.showGmailStatus(error==null?text:"Gmail · "+error);
      if(error==null&&output!=null)output.setText(text);
    });
  }
  private void openGmailMessage(int number,boolean speakAloud){
    if(gmailGateway==null){if(dashboard!=null)dashboard.showGmailStatus("Gmail no disponible");return;}
    gmailGateway.readMessage(number,(message,error)->{
      if(error!=null){
        if(dashboard!=null)dashboard.showGmailStatus("Gmail · "+error);
        if(output!=null)output.setText(error);
        return;
      }
      if(dashboard!=null)dashboard.showGmailStatus(message);
      if(output!=null)output.setText(message);
      if(!speakAloud)return;
      new AlertDialog.Builder(this)
        .setTitle("Leer correo usando NVIDIA Diego")
        .setMessage("El contenido del correo se enviará a NVIDIA para sintetizar la voz. Autorizás esta lectura?")
        .setPositiveButton("Leer por voz",(dialog,which)->say(message.length()>1450?message.substring(0,1450):message))
        .setNegativeButton("Solo en pantalla",null)
        .show();
    });
  }
  private void chooseGoogleAccount(){
    try{Intent picker=AccountManager.newChooseAccountIntent(null,null,new String[]{"com.google"},null,null,null,null);startActivityForResult(picker,GOOGLE_ACCOUNT_REQUEST);}
    catch(Exception e){say("Android no pudo abrir el selector de cuentas Google.");}
  }
  private void requestCalendarPermissions(){if(checkSelfPermission(Manifest.permission.READ_CALENDAR)!=PackageManager.PERMISSION_GRANTED||checkSelfPermission(Manifest.permission.WRITE_CALENDAR)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.READ_CALENDAR,Manifest.permission.WRITE_CALENDAR},CALENDAR_PERMISSION_REQUEST);else say("Google Calendar vinculado a "+calendarGateway.selectedGoogleAccount()+".");}
  private void requestWeatherLocation(boolean forecast){pendingWeatherForecast=forecast;if(locationProvider.hasPermission()){if(forecast)showForecastHere();else showWeatherHere();return;}requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},LOCATION_PERMISSION_REQUEST);}
  private void showWeatherHere(){say("Obteniendo tu ubicación GPS para consultar el clima.");locationProvider.current((location,error)->{if(error!=null||location==null){say(error==null?"No pude determinar tu ubicación.":error);return;}weatherService.current(location.getLatitude(),location.getLongitude(),(weather,problem)->{if(problem!=null||weather==null){say(problem==null?"Clima no disponible.":problem);return;}String report="En tu ubicación actual: "+Math.round(weather.temperature)+" "+weather.temperatureUnit+", viento "+Math.round(weather.wind)+" "+weather.windUnit+". "+weatherDescription(weather.code)+".";if(dashboard!=null)dashboard.showWeather(report);say(report);});});}
  private void showForecastHere(){say("Obteniendo tu ubicación GPS para preparar el pronóstico.");locationProvider.current((location,error)->{if(error!=null||location==null){say(error==null?"No pude determinar tu ubicación.":error);return;}weatherService.forecast(location.getLatitude(),location.getLongitude(),3,(days,problem)->{if(problem!=null||days==null){if(dashboard!=null)dashboard.showWeather(problem==null?"Pronóstico no disponible.":problem);say(problem==null?"Pronóstico no disponible.":problem);return;}StringBuilder b=new StringBuilder("Pronóstico de tres días para tu ubicación: ");for(WeatherService.ForecastDay d:days)b.append(d.date).append(", mínima ").append(Math.round(d.min)).append(" y máxima ").append(Math.round(d.max)).append(" grados, probabilidad de lluvia ").append(d.precipitationChance).append(" por ciento. ");if(dashboard!=null)dashboard.showWeather(b.toString());say(b.toString());});});}
  private void showWeather(String place){
    if(weatherService==null){say("El servicio meteorológico no está disponible.");return;}
    say("Buscando el clima de "+place+".");
    weatherService.geocode(place,(places,error)->{
      if(error!=null||places==null||places.isEmpty()){say(error==null?"No encontré esa localidad.":error);return;}
      WeatherService.Place selected=null;
      for(WeatherService.Place p:places){if("Argentina".equalsIgnoreCase(p.country)){selected=p;break;}}
      if(selected==null)selected=places.get(0);
      WeatherService.Place chosen=selected;
      weatherService.current(chosen.latitude,chosen.longitude,(weather,problem)->{
        if(problem!=null||weather==null){say(problem==null?"Clima no disponible.":problem);return;}
        String report="En "+chosen.name+", "+chosen.country+": "+Math.round(weather.temperature)+" grados, viento "+Math.round(weather.wind)+" kilómetros por hora. "+weatherDescription(weather.code)+".";if(dashboard!=null)dashboard.showWeather(report);say(report);
      });
    });
  }
  private void showForecast(String place){
    if(weatherService==null){say("El servicio meteorológico no está disponible.");return;}
    say("Buscando el pronóstico de "+place+".");weatherService.geocode(place,(places,error)->{if(error!=null||places==null||places.isEmpty()){say(error==null?"No encontré esa localidad.":error);return;}WeatherService.Place chosen=null;for(WeatherService.Place p:places)if("Argentina".equalsIgnoreCase(p.country)){chosen=p;break;}if(chosen==null)chosen=places.get(0);WeatherService.Place selected=chosen;weatherService.forecast(selected.latitude,selected.longitude,3,(days,problem)->{if(problem!=null||days==null){say(problem==null?"Pronóstico no disponible.":problem);return;}StringBuilder b=new StringBuilder("Pronóstico para ").append(selected.name).append(": ");for(WeatherService.ForecastDay d:days)b.append(d.date).append(", mínima ").append(Math.round(d.min)).append(" y máxima ").append(Math.round(d.max)).append(" grados, probabilidad de lluvia ").append(d.precipitationChance).append(" por ciento. ");if(dashboard!=null)dashboard.showWeather(b.toString());say(b.toString());});});
  }
  private String weatherDescription(int code){
    if(code==0)return "cielo despejado";if(code<=3)return "parcialmente nublado";if(code==45||code==48)return "niebla";if(code>=51&&code<=67)return "llovizna o lluvia";if(code>=71&&code<=77)return "nieve";if(code>=80&&code<=82)return "chaparrones";if(code>=95)return "tormenta";return "condiciones variables";
  }
  private void showCalendar(String commandText){
    if(calendarGateway==null){say("El calendario del dispositivo no está disponible.");return;}if(calendarGateway.selectedGoogleAccount()==null){say("Primero vinculá la cuenta Gmail que querés usar con GORO.");return;}
    ZoneId zone=ZoneId.systemDefault();LocalDate today=LocalDate.now(zone);LocalDate startDate;
    if(commandText.contains("semana")){startDate=today.with(java.time.DayOfWeek.MONDAY);}else if(commandText.contains("mañana")){startDate=today.plusDays(1);}else{startDate=today;}
    LocalDate endDate=commandText.contains("semana")?startDate.plusDays(7):startDate.plusDays(1);
    String min=startDate.atStartOfDay(zone).toOffsetDateTime().toString();String max=endDate.atStartOfDay(zone).toOffsetDateTime().toString();
    say("Consultando tu calendario.");
    calendarGateway.list("",min,max,(events,error)->{
      if(error!=null||events==null){if(dashboard!=null)dashboard.showAgenda(error==null?"No pude consultar la agenda.":error);say(error==null?"No pude consultar Google Calendar.":error);return;}
      if(events.isEmpty()){if(dashboard!=null)dashboard.showAgenda("No hay eventos en ese período.");lastCalendarEvents.clear();say("No tenés eventos en ese período.");return;}
      lastCalendarEvents.clear();lastCalendarEvents.addAll(events);if(dashboard!=null){StringBuilder overview=new StringBuilder();for(CalendarEvent e:events)overview.append(e.title).append(" · ").append(e.start).append("\n");dashboard.showAgenda(overview.toString());}
      StringBuilder b=new StringBuilder("Tenés ").append(events.size()).append(" eventos: ");
      for(int i=0;i<events.size();i++){CalendarEvent e=events.get(i);b.append("Número ").append(i+1).append(": ").append(e.title).append(", ").append(e.start).append(". ");}
      say(b.toString());
    });
  }
  private void prepareCalendarEvent(String raw){
    if(calendarGateway==null){say("Google Calendar todavía no está configurado.");return;}
    Matcher m=Pattern.compile("(?i)^(?:agendá|agenda|creá evento|crea evento)\\s+(.+?)\\s+(hoy|mañana|el\\s+\\d{4}-\\d{2}-\\d{2})\\s+a las\\s+(\\d{1,2}):(\\d{2})(?:\\s+por\\s+(\\d+)\\s+minutos?)?$").matcher(raw.trim());
    if(!m.matches()){say("Decime: agendá reunión mañana a las 15:30 por 60 minutos.");return;}
    try{ZoneId zone=ZoneId.systemDefault();LocalDate date;
      String day=m.group(2).toLowerCase(new Locale("es","AR"));
      if(day.equals("hoy"))date=LocalDate.now(zone);else if(day.equals("mañana"))date=LocalDate.now(zone).plusDays(1);else date=LocalDate.parse(day.substring(3).trim());
      int hour=Integer.parseInt(m.group(3)),minute=Integer.parseInt(m.group(4)),duration=m.group(5)==null?60:Integer.parseInt(m.group(5));
      if(hour>23||minute>59||duration<1||duration>1440)throw new IllegalArgumentException();
      ZonedDateTime start=ZonedDateTime.of(date,LocalTime.of(hour,minute),zone);ZonedDateTime end=start.plusMinutes(duration);
      pendingCalendarEvent=new CalendarEvent(null,m.group(1),start.toOffsetDateTime().toString(),end.toOffsetDateTime().toString(),zone.getId(),"Creado por GORO AI");pendingCalendarDeleteId=null;
      say("Voy a crear "+pendingCalendarEvent.title+" el "+date+" a las "+String.format(Locale.ROOT,"%02d:%02d",hour,minute)+". Decí confirmar para guardar o cancelar.");
    }catch(Exception e){pendingCalendarEvent=null;say("La fecha, hora o duración no es válida.");}
  }
  private void prepareCalendarDelete(String raw){
    if(calendarGateway==null){say("Google Calendar todavía no está configurado.");return;}
    String target=raw.substring(raw.indexOf("evento")+6).trim();CalendarEvent match=null;
    try{int index=Integer.parseInt(target)-1;if(index>=0&&index<lastCalendarEvents.size())match=lastCalendarEvents.get(index);}catch(NumberFormatException ignored){for(CalendarEvent e:lastCalendarEvents)if(e.title.equalsIgnoreCase(target)){if(match!=null){say("Hay varios eventos con ese nombre. Consultá la agenda y elegí un número.");return;}match=e;}}
    if(match==null){say("No encuentro ese evento. Consultá primero tu agenda de hoy o de la semana.");return;}
    pendingCalendarDeleteId=match.id;pendingCalendarEvent=null;say("Voy a eliminar "+match.title+". Decí confirmar para ejecutar o cancelar.");
  }
  private void prepareCalendarUpdate(String raw){
    if(calendarGateway==null){say("Google Calendar todavía no está configurado.");return;}
    Matcher m=Pattern.compile("(?i)^reprogram[áa] evento (\\d+) (hoy|mañana|el\\s+\\d{4}-\\d{2}-\\d{2}) a las (\\d{1,2}):(\\d{2})(?: por (\\d+) minutos?)?$").matcher(raw.trim());
    if(!m.matches()){say("Decime: reprogramá evento 2 mañana a las 15:30 por 45 minutos.");return;}
    try{int index=Integer.parseInt(m.group(1))-1;if(index<0||index>=lastCalendarEvents.size())throw new IllegalArgumentException();CalendarEvent old=lastCalendarEvents.get(index);ZoneId zone=ZoneId.systemDefault();String day=m.group(2).toLowerCase(new Locale("es","AR"));LocalDate date=day.equals("hoy")?LocalDate.now(zone):day.equals("mañana")?LocalDate.now(zone).plusDays(1):LocalDate.parse(day.substring(3).trim());int hour=Integer.parseInt(m.group(3)),minute=Integer.parseInt(m.group(4)),duration=m.group(5)==null?60:Integer.parseInt(m.group(5));if(hour>23||minute>59||duration<1||duration>1440)throw new IllegalArgumentException();ZonedDateTime start=ZonedDateTime.of(date,LocalTime.of(hour,minute),zone);pendingCalendarEvent=new CalendarEvent(old.id,old.title,start.toOffsetDateTime().toString(),start.plusMinutes(duration).toOffsetDateTime().toString(),zone.getId(),old.description);pendingCalendarDeleteId=null;say("Voy a reprogramar "+old.title+" para "+date+" a las "+String.format(Locale.ROOT,"%02d:%02d",hour,minute)+". Decí confirmar o cancelar.");}catch(Exception e){say("Número de evento, fecha u horario inválido. Consultá primero la agenda.");}
  }
  private void confirmCalendarAction(){
    if(pendingCalendarEvent!=null){CalendarEvent event=pendingCalendarEvent;pendingCalendarEvent=null;if(event.id!=null&&!event.id.isEmpty()){calendarGateway.update(event,(updated,error)->say(error==null&&updated!=null?"Evento reprogramado: "+updated.title+".":error==null?"No se pudo confirmar la actualización.":error));}else{calendarGateway.create(event,(created,error)->say(error==null&&created!=null?"Evento creado en Google Calendar: "+created.title+".":error==null?"No se pudo confirmar la creación.":error));}return;}
    if(pendingCalendarDeleteId!=null){String id=pendingCalendarDeleteId;pendingCalendarDeleteId=null;calendarGateway.delete(id,(ok,error)->say(Boolean.TRUE.equals(ok)?"Evento eliminado de Google Calendar.":error==null?"No se pudo confirmar la eliminación.":error));return;}
    say("No hay ninguna operación de calendario pendiente.");
  }
  @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){
    super.onRequestPermissionsResult(request,permissions,results);
    if(request==ZN02_BLE_PERMISSION_REQUEST){
      boolean granted=zn02Controller!=null&&zn02Controller.neededPermissions().length==0;
      if(granted&&pendingZn02Connection){
        pendingZn02Connection=false;zn02Controller.startScan();
      }else if(granted&&pendingZn02Inspection&&zn02GattInspector!=null){
        pendingZn02Inspection=false;zn02GattInspector.scan();
      }else if(!granted&&dashboard!=null){
        pendingZn02Connection=false;pendingZn02Inspection=false;
        dashboard.showDeviceStatus("Android no concedió los permisos Bluetooth necesarios para Z02.");
      }
      return;
    }
    if(request==41){
      if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED){
        maybeStartHandsFreeConversation();
      }else{
        voiceActive=false;if(dashboard!=null){dashboard.setVoiceActive(false);dashboard.showDeviceStatus("Android no concedió permiso de micrófono para capturar audio del Z02.");}
      }
      return;
    }
    if(results.length==0)return;
    if(request==CALENDAR_PERMISSION_REQUEST){
      boolean granted=checkSelfPermission(Manifest.permission.READ_CALENDAR)==PackageManager.PERMISSION_GRANTED&&checkSelfPermission(Manifest.permission.WRITE_CALENDAR)==PackageManager.PERMISSION_GRANTED;
      say(granted?"Google Calendar vinculado a "+calendarGateway.selectedGoogleAccount()+".":"El calendario necesita permisos de lectura y escritura; no se modificó ningún evento.");
    }else if(request==LOCATION_PERMISSION_REQUEST){
      if(locationProvider.hasPermission()){if(pendingWeatherForecast)showForecastHere();else showWeatherHere();}
      else say("No se concedió permiso de ubicación. No consulté una ubicación aproximada ni inventé tu clima.");
    }else if(request==52){
      say(results[0]==PackageManager.PERMISSION_GRANTED?"Notificaciones habilitadas. Ya puedo entregarte recordatorios.":"Las notificaciones están deshabilitadas; GORO no puede mostrar recordatorios hasta que las habilites en Android.");
    }
  }
  @Override protected void onStop(){
    foreground=false;
    invalidateVoiceConversation();
    if(dashboard!=null){dashboard.setVoiceActive(false);dashboard.showAvatarMood(GoroAvatarView.Mood.IDLE);}
    super.onStop();
  }
  @Override protected void onDestroy(){
    foreground=false;invalidateVoiceConversation();
    if(nemotronGateway!=null)nemotronGateway.close();
    if(speechGateway!=null)speechGateway.close();
    if(asrGateway!=null)asrGateway.close();
    if(z02Player!=null)z02Player.close();
    if(zn02GattInspector!=null)zn02GattInspector.close();
    if(zn02Controller!=null)zn02Controller.close();
    if(calendarGateway!=null)calendarGateway.close();
    if(gmailGateway!=null)gmailGateway.close();
    if(weatherService!=null)weatherService.close();
    super.onDestroy();
  }
}
