package ar.goro.ai;

import android.app.Activity;
import android.app.PendingIntent;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.speech.tts.UtteranceProgressListener;
import ar.goro.ai.voice.ContinuousVoiceSession;
import ar.goro.ai.voice.ConversationState;
import ar.goro.ai.memory.MemoryStore;
import ar.goro.ai.memory.MemoryRecord;
import ar.goro.ai.tools.AssistantCommandParser;
import ar.goro.ai.tools.AlarmClockIntents;
import ar.goro.ai.automation.ReminderScheduler;
import ar.goro.ai.auth.GoogleTokenStore;
import ar.goro.ai.auth.GoogleOAuthManager;
import ar.goro.ai.auth.GoogleCalendarTokenProvider;
import ar.goro.ai.BuildConfig;
import ar.goro.ai.calendar.GoogleCalendarRestGateway;
import ar.goro.ai.calendar.CalendarEvent;
import ar.goro.ai.weather.WeatherService;
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
import java.util.*;
import java.util.regex.*;

public class MainActivity extends Activity {
  private static final int VOICE_REQUEST=27;
  private TextView output, state;
  private EditText command;
  private TextToSpeech tts;
  private boolean voiceReady=false;
  private SharedPreferences memory;
  private MemoryStore memoryStore;
  private ContinuousVoiceSession voiceSession;
  private boolean voiceActive=false;
  private ReminderScheduler reminderScheduler;
  private GoogleTokenStore googleTokenStore;
  private GoogleOAuthManager googleOAuth;
  private GoogleCalendarRestGateway calendarGateway;
  private WeatherService weatherService;
  private CalendarEvent pendingCalendarEvent;
  private String pendingCalendarDeleteId;
  private final java.util.List<CalendarEvent> lastCalendarEvents=new java.util.ArrayList<>();
  private static final int GOOGLE_AUTH_REQUEST=61;
  @Override public void onCreate(Bundle saved) {
    super.onCreate(saved);
    memory=getSharedPreferences("goro_memory",MODE_PRIVATE);
    memoryStore=new MemoryStore(this);
    reminderScheduler=new ReminderScheduler(this);
    weatherService=new WeatherService();
    try{googleTokenStore=new GoogleTokenStore(this);String clientId=BuildConfig.GOOGLE_OAUTH_CLIENT_ID;googleOAuth=new GoogleOAuthManager(this,googleTokenStore,clientId);GoogleCalendarTokenProvider tokenProvider=new GoogleCalendarTokenProvider(this,googleTokenStore,clientId);calendarGateway=new GoogleCalendarRestGateway(tokenProvider::validAccessToken); }catch(Exception e){googleTokenStore=null;}
    memoryStore.migrateLegacy(memory);
    voiceSession=new ContinuousVoiceSession(this,new ContinuousVoiceSession.Listener(){
      public void onState(ConversationState s){if(state!=null)state.setText("GORO • "+s.name());}
      public void onFinalText(String text){if(voiceSession!=null){voiceSession.pauseForResponse();} if(command!=null)command.setText(text);runCommand(text);}
      public void onPartialText(String text){if(command!=null)command.setText(text);}
      public void onError(String message){if(output!=null)output.setText(message);}
      public void onUserSpeechStarted(){if(tts!=null)tts.stop();}
    });
    tts=new TextToSpeech(this, status -> {
      voiceReady=status==TextToSpeech.SUCCESS;
      if(voiceReady) {
        tts.setLanguage(new Locale("es","AR"));
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
          public void onStart(String id){}
          public void onDone(String id){runOnUiThread(()->{if(voiceSession!=null&&voiceActive)voiceSession.resumeAfterResponse();});}
          public void onError(String id){runOnUiThread(()->{if(voiceSession!=null&&voiceActive)voiceSession.resumeAfterResponse();});}
        });
      }
    });
    LinearLayout root=new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(30,38,30,25);
    root.setBackgroundColor(Color.rgb(234,237,234));
    ScrollView scroll=new ScrollView(this);
    LinearLayout body=new LinearLayout(this);
    body.setOrientation(LinearLayout.VERTICAL);
    TextView title=new TextView(this);
    title.setText("GORO"); title.setTextSize(42);title.setTypeface(null,Typeface.BOLD);
    title.setTextColor(0xff263446); body.addView(title);
    state=new TextView(this);state.setText("Asistente de voz • Android");state.setTextColor(0xff607589);
    body.addView(state);
    TextView avatar=new TextView(this);
    avatar.setText("◉

GORO AI");avatar.setTextSize(30);avatar.setGravity(Gravity.CENTER);
    avatar.setTextColor(0xff214E73);avatar.setBackgroundColor(0xffd1e4e2);
    LinearLayout.LayoutParams avatarParams=new LinearLayout.LayoutParams(-1,300);
    avatarParams.setMargins(0,25,0,20);body.addView(avatar,avatarParams);
    output=new TextView(this);output.setText("Podés pedirme la hora, una alarma, un temporizador o una cuenta.");
    output.setTextSize(19);output.setTextColor(0xff253342);output.setPadding(10,20,10,20);
    body.addView(output);
    command=new EditText(this);command.setHint("Escribí o dictá una orden");command.setSingleLine(false);
    body.addView(command);
    Button talk=new Button(this);talk.setText("Hablar con GORO");body.addView(talk);
    talk.setText("Conversación continua");
    talk.setOnClickListener(v->{
      if(voiceActive){voiceActive=false;voiceSession.stop();talk.setText("Conversación continua");return;}
      if(Build.VERSION.SDK_INT>=23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},41);return;}
      if(!voiceSession.available()){say("No hay reconocimiento de voz disponible.");return;}
      voiceActive=true;talk.setText("Detener sesión");voiceSession.start();
    });
    Button calendarConnect=new Button(this);calendarConnect.setText("Conectar Google Calendar");body.addView(calendarConnect);
    calendarConnect.setOnClickListener(v->{try{if(googleOAuth==null)throw new IllegalStateException("Almacenamiento seguro no disponible.");Intent done=new Intent(this,MainActivity.class).setAction("ar.goro.ai.GOOGLE_AUTH_DONE").addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);Intent cancel=new Intent(this,MainActivity.class).setAction("ar.goro.ai.GOOGLE_AUTH_CANCEL").addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);int flags=PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=31?PendingIntent.FLAG_MUTABLE:0);PendingIntent donePi=PendingIntent.getActivity(this,61,done,flags);PendingIntent cancelPi=PendingIntent.getActivity(this,62,cancel,flags);googleOAuth.performAuthorizationRequest(this,donePi,cancelPi);}catch(Exception e){say("Google Calendar requiere configurar el ID de cliente OAuth Android en la compilación.");}});
    Button reminders=new Button(this);reminders.setText("Activar recordatorios");body.addView(reminders);
    reminders.setOnClickListener(v->{if(Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},52);}else say("Notificaciones disponibles para los recordatorios de GORO.");});
    Button execute=new Button(this);execute.setText("Enviar texto");body.addView(execute);
    execute.setOnClickListener(v->runCommand(command.getText().toString().trim()));
    scroll.addView(body);root.addView(scroll);setContentView(root);
    handleGoogleAuthIntent(getIntent());
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
    state.setText("Asistente de voz • Android");
    if(request==VOICE_REQUEST && result==RESULT_OK && data!=null){
      ArrayList<String> words=data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
      if(words!=null&&!words.isEmpty()){command.setText(words.get(0));runCommand(words.get(0));}
    }
  }
  @Override protected void onNewIntent(Intent intent){
    super.onNewIntent(intent);setIntent(intent);
    if(intent==null||googleOAuth==null)return;
    if("ar.goro.ai.GOOGLE_AUTH_DONE".equals(intent.getAction()))googleOAuth.handleAuthorizationResult(intent,(ok,error)->say(ok?"Google Calendar quedó autorizado.":"No se pudo autorizar Google Calendar: "+error));
    else if("ar.goro.ai.GOOGLE_AUTH_CANCEL".equals(intent.getAction()))say("Autorización de Google Calendar cancelada.");
  }
  private void handleGoogleAuthIntent(Intent intent){
    if(intent==null||googleOAuth==null)return;
    if(googleOAuth.isAuthorizationResponse(intent))googleOAuth.handleAuthorizationResult(intent,(ok,error)->say(ok?"Google Calendar quedó autorizado.":"No se pudo autorizar Google Calendar: "+error));
    else if("ar.goro.ai.GOOGLE_AUTH_CANCEL".equals(intent.getAction()))say("Autorización de Google Calendar cancelada.");
  }
  private void say(String message) {
    output.setText(message);
    if(voiceReady) { if(voiceSession!=null)voiceSession.pauseForResponse(); tts.speak(message,TextToSpeech.QUEUE_FLUSH,null,"goro_reply"); }
    else if(voiceSession!=null)voiceSession.resumeAfterResponse();
  }
  private void runCommand(String raw) {
    if(raw.isEmpty()) return;
    String text=raw.toLowerCase(new Locale("es","AR")).trim();
    if(text.equals("confirmar")||text.equals("confirmá")||text.equals("confirmar evento")){confirmCalendarAction();return;}
    if(text.equals("cancelar")||text.equals("cancelá")||text.equals("cancelar evento")){pendingCalendarEvent=null;pendingCalendarDeleteId=null;say("Operación de calendario cancelada.");return;}
    if(text.startsWith("pronóstico en ")||text.startsWith("pronostico en ")){String place=raw.substring(raw.toLowerCase(new Locale("es","AR")).indexOf(" en ")+4).trim();showForecast(place);return;}
    if(text.startsWith("clima en ")||text.startsWith("qué clima hace en ")||text.startsWith("que clima hace en ")){String place=raw.substring(raw.toLowerCase(new Locale("es","AR")).indexOf(" en ")+4).trim();showWeather(place);return;}
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
    } else if(text.contains("mostrame las alarmas") || text.contains("mostrá las alarmas") || text.contains("ver alarmas")) {
      Intent show=AlarmClockIntents.showAlarms();
      try{startActivity(show);say("Abrí la lista de alarmas del teléfono.");}
      catch(Exception e){say("El reloj del teléfono no permite mostrar las alarmas.");}
    } else if(text.contains("mostrame los temporizadores") || text.contains("mostrá los temporizadores") || text.contains("ver temporizadores")) {
      Intent show=AlarmClockIntents.showTimers();
      try{startActivity(show);say("Abrí la lista de temporizadores del teléfono.");}
      catch(Exception e){say("El reloj del teléfono no permite mostrar los temporizadores.");}
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
      say("Todavía no tengo conexión a Nemotron. No ejecuté ninguna acción.");
    }
  }
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
        say("En "+chosen.name+", "+chosen.country+": "+Math.round(weather.temperature)+" grados, viento "+Math.round(weather.wind)+" kilómetros por hora. "+weatherDescription(weather.code)+".");
      });
    });
  }
  private void showForecast(String place){
    if(weatherService==null){say("El servicio meteorológico no está disponible.");return;}
    say("Buscando el pronóstico de "+place+".");weatherService.geocode(place,(places,error)->{if(error!=null||places==null||places.isEmpty()){say(error==null?"No encontré esa localidad.":error);return;}WeatherService.Place chosen=null;for(WeatherService.Place p:places)if("Argentina".equalsIgnoreCase(p.country)){chosen=p;break;}if(chosen==null)chosen=places.get(0);WeatherService.Place selected=chosen;weatherService.forecast(selected.latitude,selected.longitude,3,(days,problem)->{if(problem!=null||days==null){say(problem==null?"Pronóstico no disponible.":problem);return;}StringBuilder b=new StringBuilder("Pronóstico para ").append(selected.name).append(": ");for(WeatherService.ForecastDay d:days)b.append(d.date).append(", mínima ").append(Math.round(d.min)).append(" y máxima ").append(Math.round(d.max)).append(" grados, probabilidad de lluvia ").append(d.precipitationChance).append(" por ciento. ");say(b.toString());});});
  }
  private String weatherDescription(int code){
    if(code==0)return "cielo despejado";if(code<=3)return "parcialmente nublado";if(code==45||code==48)return "niebla";if(code>=51&&code<=67)return "llovizna o lluvia";if(code>=71&&code<=77)return "nieve";if(code>=80&&code<=82)return "chaparrones";if(code>=95)return "tormenta";return "condiciones variables";
  }
  private void showCalendar(String commandText){
    if(calendarGateway==null){say("Google Calendar todavía no está configurado.");return;}
    ZoneId zone=ZoneId.systemDefault();LocalDate today=LocalDate.now(zone);LocalDate startDate;
    if(commandText.contains("semana")){startDate=today.with(java.time.DayOfWeek.MONDAY);}else if(commandText.contains("mañana")){startDate=today.plusDays(1);}else{startDate=today;}
    LocalDate endDate=commandText.contains("semana")?startDate.plusDays(7):startDate.plusDays(1);
    String min=startDate.atStartOfDay(zone).toOffsetDateTime().toString();String max=endDate.atStartOfDay(zone).toOffsetDateTime().toString();
    say("Consultando tu calendario.");
    calendarGateway.list("",min,max,(events,error)->{
      if(error!=null||events==null){say(error==null?"No pude consultar Google Calendar.":error);return;}
      if(events.isEmpty()){say("No tenés eventos en ese período.");return;}
      lastCalendarEvents.clear();lastCalendarEvents.addAll(events);
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
  @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==41&&results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED){voiceActive=true;voiceSession.start();}}
  @Override protected void onDestroy(){if(calendarGateway!=null)calendarGateway.close();if(googleOAuth!=null)googleOAuth.close();if(weatherService!=null)weatherService.close();if(voiceSession!=null)voiceSession.destroy();if(tts!=null){tts.stop();tts.shutdown();}super.onDestroy();}
}
