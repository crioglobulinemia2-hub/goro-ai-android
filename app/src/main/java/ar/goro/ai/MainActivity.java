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
  private static final int GOOGLE_AUTH_REQUEST=61;
  @Override public void onCreate(Bundle saved) {
    super.onCreate(saved);
    memory=getSharedPreferences("goro_memory",MODE_PRIVATE);
    memoryStore=new MemoryStore(this);
    reminderScheduler=new ReminderScheduler(this);
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
    avatar.setText("◉\n\nGORO AI");avatar.setTextSize(30);avatar.setGravity(Gravity.CENTER);
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
  @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==41&&results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED){voiceActive=true;voiceSession.start();}}
  @Override protected void onDestroy(){if(calendarGateway!=null)calendarGateway.close();if(googleOAuth!=null)googleOAuth.close();if(voiceSession!=null)voiceSession.destroy();if(tts!=null){tts.stop();tts.shutdown();}super.onDestroy();}
}
