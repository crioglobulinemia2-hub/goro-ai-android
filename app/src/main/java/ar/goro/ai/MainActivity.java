package ar.goro.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.accounts.AccountManager;
import android.location.Location;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.speech.tts.UtteranceProgressListener;
import ar.goro.ai.voice.ContinuousVoiceSession;
import ar.goro.ai.voice.GoroVoiceSelector;
import android.speech.tts.Voice;
import ar.goro.ai.voice.ConversationState;
import ar.goro.ai.memory.MemoryStore;
import ar.goro.ai.memory.MemoryRecord;
import ar.goro.ai.tools.AssistantCommandParser;
import ar.goro.ai.tools.AlarmClockIntents;
import ar.goro.ai.automation.ReminderScheduler;
import ar.goro.ai.calendar.DeviceCalendarGateway;
import ar.goro.ai.calendar.CalendarEvent;
import ar.goro.ai.weather.WeatherService;
import ar.goro.ai.weather.DeviceLocationProvider;
import ar.goro.ai.core.NemotronGateway;
import ar.goro.ai.core.NvidiaCredentialStore;
import ar.goro.ai.core.NemotronSpeechGateway;
import ar.goro.ai.voice.GoroAudioPlayer;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;
import android.content.SharedPreferences;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.provider.AlarmClock;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.*;
import android.text.InputType;
import java.util.*;
import java.util.regex.*;

public class MainActivity extends Activity {
  private static final int VOICE_REQUEST=27;
  private TextView output, state;
  private ar.goro.ai.ui.GoroDashboardView dashboard;
  private ZnpBleDiscovery znpBleDiscovery;
  private JieliChargingCaseBridge jieliChargingCaseBridge;
  private boolean pendingJieliScan=false;
  private static final int ZNP_BLE_PERMISSION_REQUEST=65;
  private EditText command;
  private TextToSpeech tts;
  private boolean voiceReady=false;
  private boolean maleVoiceReady=false;
  private String selectedGoroVoice="";
  private SharedPreferences memory;
  private MemoryStore memoryStore;
  private ContinuousVoiceSession voiceSession;
  private boolean voiceActive=false;
  private ReminderScheduler reminderScheduler;
  private DeviceCalendarGateway calendarGateway;
  private WeatherService weatherService;
  private DeviceLocationProvider locationProvider;
  private NvidiaCredentialStore nvidiaCredentialStore;
  private NemotronGateway nemotronGateway;
  private NemotronSpeechGateway speechGateway;
  private GoroAudioPlayer goroAudioPlayer;
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
    goroAudioPlayer=new GoroAudioPlayer(this);
    nvidiaCredentialStore=new NvidiaCredentialStore(this);
    try{String savedNvidiaKey=nvidiaCredentialStore.read();if(savedNvidiaKey!=null){nemotronGateway=new NemotronGateway(savedNvidiaKey);speechGateway=new NemotronSpeechGateway(savedNvidiaKey);}}catch(Exception credentialError){nvidiaCredentialStore.clear();}
    calendarGateway=new DeviceCalendarGateway(this);
    znpBleDiscovery=new ZnpBleDiscovery(this,message->{if(dashboard!=null){dashboard.showDeviceStatus(message);}});
    jieliChargingCaseBridge=new JieliChargingCaseBridge(this,message->{if(dashboard!=null)dashboard.showDeviceStatus(message);});
    memoryStore.migrateLegacy(memory);
    voiceSession=new ContinuousVoiceSession(this,new ContinuousVoiceSession.Listener(){
      public void onState(ConversationState s){if(state!=null)state.setText("GORO • "+s.name());}
      public void onFinalText(String text){if(voiceSession!=null){voiceSession.pauseForResponse();} if(command!=null)command.setText(text);runCommand(text);}
      public void onPartialText(String text){if(command!=null)command.setText(text);}
      public void onError(String message){if(output!=null)output.setText(message);}
      public void onUserSpeechStarted(){if(tts!=null)tts.stop();if(goroAudioPlayer!=null)goroAudioPlayer.stop();}
    });
    tts=new TextToSpeech(this, status -> {
      voiceReady=status==TextToSpeech.SUCCESS;
      maleVoiceReady=false;selectedGoroVoice="";
      if(voiceReady) {
        tts.setLanguage(new Locale("es","AR"));
        Voice male=GoroVoiceSelector.select(tts.getVoices());
        if(male!=null){tts.setVoice(male);tts.setSpeechRate(0.96f);tts.setPitch(0.92f);maleVoiceReady=true;selectedGoroVoice=male.getName();}
        runOnUiThread(()->{if(dashboard!=null)dashboard.showVoiceStatus(maleVoiceReady?"Voz masculina · "+selectedGoroVoice:"Sin voz masculina española instalada");});
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
          public void onStart(String id){runOnUiThread(()->{if(state!=null)state.setText("GORO · Hablando");});}
          public void onDone(String id){runOnUiThread(()->{if(state!=null)state.setText(voiceActive?"GORO · Escuchando":"GORO · Listo");if(voiceSession!=null&&voiceActive)voiceSession.resumeAfterResponse();});}
          public void onError(String id){runOnUiThread(()->{if(state!=null)state.setText("GORO · Voz no disponible");if(voiceSession!=null&&voiceActive)voiceSession.resumeAfterResponse();});}
        });
      }
    });
    dashboard=new ar.goro.ai.ui.GoroDashboardView(this,new ar.goro.ai.ui.GoroDashboardView.Actions(){
      public void onCommand(String text){runCommand(text);}
      public void onVoiceSession(){
        if(voiceActive){voiceActive=false;voiceSession.stop();dashboard.setVoiceActive(false);return;}
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
          requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},41);return;
        }
        if(!voiceSession.available()){say("No hay reconocimiento de voz disponible.");return;}
        voiceActive=true;dashboard.setVoiceActive(true);voiceSession.start();
      }
      public void onLinkAccount(){chooseGoogleAccount();}
      public void onCalendarPermission(){
        if(calendarGateway.selectedGoogleAccount()==null)chooseGoogleAccount();
        else requestCalendarPermissions();
      }
      public void onUnlinkAccount(){
        calendarGateway.unlinkGoogleAccount();
        calendarAccountLabel.setText("Cuenta Google: sin vincular");
        lastCalendarEvents.clear();pendingCalendarEvent=null;pendingCalendarDeleteId=null;
        say("Cuenta desvinculada. No se eliminaron eventos.");
      }
      public void onWeather(){requestWeatherLocation(false);}
      public void onForecast(){requestWeatherLocation(true);}
      public void onBleScan(){startZnpBleInspection();}
      public void onConfigureNvidia(){configureNvidia();}
      public void onTestNvidia(){testNemotron();}
      public void onConnectZnp(){connectZnpWithJieli();}
      public void onZnpLightOn(){jieliChargingCaseBridge.setFlashlight(true);}
      public void onZnpLightOff(){jieliChargingCaseBridge.setFlashlight(false);}
      public void onZnpReadLight(){jieliChargingCaseBridge.readFlashlight();}
      public void onReminders(){
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
          requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},52);
      }
    },calendarGateway.selectedGoogleAccount());
    output=dashboard.output;
    state=dashboard.state;
    command=dashboard.command;
    calendarAccountLabel=dashboard.accountLabel;
    dashboard.showNvidiaStatus(nemotronGateway==null?"NVIDIA NIM: requiere API key":"NVIDIA NIM: clave guardada · sin prueba de conexión");
    dashboard.showVoiceStatus(speechGateway!=null?"NVIDIA Magpie · voz masculina Diego · sin prueba de audio":"Voz masculina local: "+(maleVoiceReady?selectedGoroVoice:"no disponible"));
    setContentView(dashboard);
  }
  private void listen() {
    Intent intent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
    intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
    intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"es-AR");
    intent.putExtra(RecognizerIntent.EXTRA_PROMPT,"Hablale a GORO");
    try {state.setText("Escuchando...");startActivityForResult(intent,VOICE_REQUEST);}
    catch(ActivityNotFoundException e){say("No hay reconocimiento de voz disponible en este dispositivo.");}
  }
  @Override protected void onActivityResult(int request,int result,Intent data) {
    super.onActivityResult(request,result,data);
    if(request==GOOGLE_ACCOUNT_REQUEST){
      if(result==RESULT_OK&&data!=null){String email=data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);String type=data.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE);if(email==null||!"com.google".equals(type)){say("No se seleccionó una cuenta Google válida.");return;}try{calendarGateway.setSelectedGoogleAccount(email);calendarAccountLabel.setText("Google Calendar: "+email);say("Cuenta "+email+" seleccionada para GORO. Ahora autorizá el acceso al calendario.");requestCalendarPermissions();}catch(Exception e){say("No pude vincular esa cuenta Google.");}}
      else say("Vinculación de Gmail cancelada.");return;
    }
    state.setText("Asistente de voz • Android");
    if(request==VOICE_REQUEST && result==RESULT_OK && data!=null){
      ArrayList<String> words=data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
      if(words!=null&&!words.isEmpty()){command.setText(words.get(0));runCommand(words.get(0));}
    }
  }
  private void say(String message) {
    output.setText(message);
    if(speechGateway!=null){
      if(state!=null)state.setText("GORO · Generando voz masculina NVIDIA");
      if(voiceSession!=null)voiceSession.pauseForResponse();
      if(goroAudioPlayer!=null)goroAudioPlayer.stop();
      speechGateway.synthesize(message,(wav,error)->{
        if(error!=null||wav==null){
          if(maleVoiceReady){if(state!=null)state.setText("GORO · Voz local masculina");tts.speak(message,TextToSpeech.QUEUE_FLUSH,null,"goro_reply");}
          else{if(state!=null)state.setText("GORO · Error de voz NVIDIA");if(output!=null)output.setText(message+"\\n\\nVoz NVIDIA no disponible: "+error);if(voiceSession!=null&&voiceActive)voiceSession.resumeAfterResponse();}
          return;
        }
        goroAudioPlayer.play(wav,new GoroAudioPlayer.Listener(){
          public void onStarted(){if(state!=null)state.setText("GORO · Hablando · Diego");}
          public void onCompleted(){if(state!=null)state.setText(voiceActive?"GORO · Escuchando":"GORO · Listo");if(voiceSession!=null&&voiceActive)voiceSession.resumeAfterResponse();}
          public void onError(String problem){if(state!=null)state.setText("GORO · Error de reproducción");if(voiceSession!=null&&voiceActive)voiceSession.resumeAfterResponse();}
        });
      });
    }else if(maleVoiceReady){
      if(state!=null)state.setText("GORO · Voz masculina local");
      if(voiceSession!=null)voiceSession.pauseForResponse();
      tts.speak(message,TextToSpeech.QUEUE_FLUSH,null,"goro_reply");
    }else{
      if(state!=null)state.setText("GORO · Configurá la clave NVIDIA para voz masculina");
      if(voiceSession!=null&&voiceActive)voiceSession.resumeAfterResponse();
    }
  }
  private void runCommand(String raw) {
    if(raw.isEmpty()) return;
    String text=raw.toLowerCase(new Locale("es","AR")).trim();
    if(text.equals("confirmar")||text.equals("confirmá")){confirmCalendarAction();return;}
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
      .setMessage("La clave se cifra con Android Keystore y se guarda solo en este dispositivo. No se incluye en la APK ni en GitHub.")
      .setView(input)
      .setNegativeButton("Cancelar",null)
      .setPositiveButton("Guardar y probar",(dialog,which)->{
       String key=input.getText().toString().trim();
       try{
        nvidiaCredentialStore.save(key);
        if(nemotronGateway!=null)nemotronGateway.close();
        nemotronGateway=new NemotronGateway(key);
        speechGateway=new NemotronSpeechGateway(key);
        if(dashboard!=null)dashboard.showNvidiaStatus("NVIDIA NIM: clave cifrada · verificando…");
        testNemotron();
       }catch(Exception e){if(dashboard!=null)dashboard.showNvidiaStatus("NVIDIA NIM: clave no guardada");say("No pude guardar la clave NVIDIA. Verificá que sea una API key nvapi válida.");}
      }).show();
  }
  private void testNemotron(){
    if(nemotronGateway==null){if(dashboard!=null)dashboard.showNvidiaStatus("NVIDIA NIM: falta API key");say("Primero configurá la API key de NVIDIA NIM.");return;}
    if(dashboard!=null)dashboard.showNvidiaStatus("NVIDIA NIM: probando endpoint…");
    nemotronGateway.request("goro-main","Respondé exactamente: GORO conectado.",(reply,error)->{
      if(error!=null){if(dashboard!=null)dashboard.showNvidiaStatus("NVIDIA NIM: error · "+error);say("No pude autenticar con NVIDIA NIM. "+error);}
      else{if(dashboard!=null)dashboard.showNvidiaStatus("NVIDIA NIM: conectado · "+NemotronGateway.MODEL);say(reply);}
    });
  }
  private void requestNemotron(String text){
    if(dashboard!=null)dashboard.showNvidiaStatus("NVIDIA NIM: procesando…");
    output.setText("GORO está consultando Nemotron…");
    nemotronGateway.request("goro-main",text,(reply,error)->{
      if(error!=null){if(dashboard!=null)dashboard.showNvidiaStatus("NVIDIA NIM: error · "+error);say("La consulta a Nemotron falló. "+error);}
      else{if(dashboard!=null)dashboard.showNvidiaStatus("NVIDIA NIM: conectado");say(reply);}
    });
  }
  private void connectZnpWithJieli(){
    if(jieliChargingCaseBridge==null){say("El puente JieLi no está disponible.");return;}
    String[] needed=znpBleDiscovery.neededPermissions();
    if(needed.length>0){pendingJieliScan=true;requestPermissions(needed,ZNP_BLE_PERMISSION_REQUEST);return;}
    jieliChargingCaseBridge.startScan();
  }
  private void startZnpBleInspection(){
    if(znpBleDiscovery==null)return;
    String[] missing=znpBleDiscovery.neededPermissions();
    if(missing.length>0){requestPermissions(missing,ZNP_BLE_PERMISSION_REQUEST);return;}
    znpBleDiscovery.begin();
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
  @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(results.length==0)return;if(request==ZNP_BLE_PERMISSION_REQUEST){if(znpBleDiscovery.neededPermissions().length==0){if(pendingJieliScan){pendingJieliScan=false;jieliChargingCaseBridge.startScan();}else znpBleDiscovery.begin();}else{pendingJieliScan=false;if(dashboard!=null)dashboard.showDeviceStatus("Permiso Bluetooth denegado: conexión cancelada.");}}else if(request==41&&results[0]==PackageManager.PERMISSION_GRANTED){voiceActive=true;if(dashboard!=null)dashboard.setVoiceActive(true);voiceSession.start();}else if(request==CALENDAR_PERMISSION_REQUEST){boolean granted=checkSelfPermission(Manifest.permission.READ_CALENDAR)==PackageManager.PERMISSION_GRANTED&&checkSelfPermission(Manifest.permission.WRITE_CALENDAR)==PackageManager.PERMISSION_GRANTED;say(granted?"Google Calendar vinculado a "+calendarGateway.selectedGoogleAccount()+".":"El calendario necesita permisos de lectura y escritura; no se modificó ningún evento.");}else if(request==LOCATION_PERMISSION_REQUEST){if(locationProvider.hasPermission()){if(pendingWeatherForecast)showForecastHere();else showWeatherHere();}else say("No se concedió permiso de ubicación. No consulté una ubicación aproximada ni inventé tu clima.");}else if(request==52){say(results[0]==PackageManager.PERMISSION_GRANTED?"Notificaciones habilitadas. Ya puedo entregarte recordatorios.":"Las notificaciones están deshabilitadas; GORO no puede mostrar recordatorios hasta que las habilites en Android.");}}
  @Override protected void onDestroy(){if(nemotronGateway!=null)nemotronGateway.close();if(speechGateway!=null)speechGateway.close();if(goroAudioPlayer!=null)goroAudioPlayer.stop();if(jieliChargingCaseBridge!=null)jieliChargingCaseBridge.close();if(znpBleDiscovery!=null)znpBleDiscovery.close();if(calendarGateway!=null)calendarGateway.close();if(weatherService!=null)weatherService.close();if(voiceSession!=null)voiceSession.destroy();if(tts!=null){tts.stop();tts.shutdown();}super.onDestroy();}
}
