package ar.goro.ai;

import android.app.Activity;\nimport android.Manifest;\nimport android.content.pm.PackageManager;\nimport android.os.Build;\nimport android.speech.tts.UtteranceProgressListener;\nimport ar.goro.ai.voice.ContinuousVoiceSession;\nimport ar.goro.ai.voice.ConversationState;
import ar.goro.ai.memory.MemoryStore;
import ar.goro.ai.memory.MemoryRecord;
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
  private MemoryStore memoryStore;\n  private ContinuousVoiceSession voiceSession;\n  private boolean voiceActive=false;
  @Override public void onCreate(Bundle saved) {
    super.onCreate(saved);
    memory=getSharedPreferences("goro_memory",MODE_PRIVATE);
    memoryStore=new MemoryStore(this);\n    voiceSession=new ContinuousVoiceSession(this,new ContinuousVoiceSession.Listener(){\n      public void onState(ConversationState s){if(state!=null)state.setText("GORO • "+s.name());}\n      public void onFinalText(String text){if(voiceSession!=null){voiceSession.pauseForResponse();} if(command!=null)command.setText(text);runCommand(text);}\n      public void onPartialText(String text){if(command!=null)command.setText(text);}\n      public void onError(String message){if(output!=null)output.setText(message);}\n      public void onUserSpeechStarted(){if(tts!=null)tts.stop();}\n    });
    tts=new TextToSpeech(this, status -> {
      voiceReady=status==TextToSpeech.SUCCESS;
      if(voiceReady) {\n        tts.setLanguage(new Locale("es","AR"));\n        tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){\n          public void onStart(String id){}\n          public void onDone(String id){runOnUiThread(()->{if(voiceSession!=null&&voiceActive)voiceSession.resumeAfterResponse();});}\n          public void onError(String id){runOnUiThread(()->{if(voiceSession!=null&&voiceActive)voiceSession.resumeAfterResponse();});}\n        });\n      }
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
    talk.setText("Conversación continua");\n    talk.setOnClickListener(v->{\n      if(voiceActive){voiceActive=false;voiceSession.stop();talk.setText("Conversación continua");return;}\n      if(Build.VERSION.SDK_INT>=23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},41);return;}\n      if(!voiceSession.available()){say("No hay reconocimiento de voz disponible.");return;}\n      voiceActive=true;talk.setText("Detener sesión");voiceSession.start();\n    });
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
  private void say(String message) {
    output.setText(message);
    if(voiceReady) { if(voiceSession!=null)voiceSession.pauseForResponse(); tts.speak(message,TextToSpeech.QUEUE_FLUSH,null,"goro_reply"); }\n    else if(voiceSession!=null)voiceSession.resumeAfterResponse();
  }
  private void runCommand(String raw) {
    if(raw.isEmpty()) return;
    String text=raw.toLowerCase(new Locale("es","AR")).trim();
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
    } else if(text.contains("borrá todos los recuerdos") || text.contains("borra todos los recuerdos")) {
      try{int deleted=memoryStore.clearAll();say("Eliminé "+deleted+" recuerdos locales.");}
      catch(Exception e){say("No pude eliminar la memoria.");}
    } else if(text.contains("alarma") || text.contains("despert")) {
      Matcher time=Pattern.compile("(?:a las |para las |las )?(\\d{1,2})(?:[:.](\\d{2}))").matcher(text);
      Matcher hour=Pattern.compile("(?:a las |para las )(\\d{1,2})(?!\\d)").matcher(text);
      int h=-1,m=0;
      if(time.find()){h=Integer.parseInt(time.group(1));m=Integer.parseInt(time.group(2));}
      else if(hour.find())h=Integer.parseInt(hour.group(1));
      if(h<0||h>23||m>59){say("Decime la hora de la alarma. Por ejemplo, a las siete y treinta.");return;}
      Intent alarm=new Intent(AlarmClock.ACTION_SET_ALARM);
      alarm.putExtra(AlarmClock.EXTRA_HOUR,h);alarm.putExtra(AlarmClock.EXTRA_MINUTES,m);
      alarm.putExtra(AlarmClock.EXTRA_MESSAGE,"GORO AI");
      try {startActivity(alarm);say("Envié la alarma de "+h+" y "+String.format(Locale.ROOT,"%02d",m)+" al reloj del teléfono. Verificá su confirmación.");}
      catch(Exception e){say("No pude abrir el reloj para crear la alarma.");}
    } else if(text.contains("temporizador")||text.contains("cuenta regresiva")) {
      Matcher duration=Pattern.compile("(\\d+)\\s*(segundos?|minutos?|horas?)").matcher(text);
      if(!duration.find()){say("Decime cuánto debe durar el temporizador.");return;}
      long n=Long.parseLong(duration.group(1));
      String unit=duration.group(2);
      long seconds=n*(unit.startsWith("hora")?3600:unit.startsWith("minuto")?60:1);
      if(seconds<=0||seconds>86400){say("Elegí una duración de hasta veinticuatro horas.");return;}
      Intent timer=new Intent(AlarmClock.ACTION_SET_TIMER);
      timer.putExtra(AlarmClock.EXTRA_LENGTH,(int)seconds);
      timer.putExtra(AlarmClock.EXTRA_MESSAGE,"GORO AI");
      try {startActivity(timer);say("Envié el temporizador al reloj. Comprobá que haya comenzado.");}
      catch(Exception e){say("No pude abrir el temporizador.");}
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
  @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==41&&results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED){voiceActive=true;voiceSession.start();}}\n  @Override protected void onDestroy(){if(voiceSession!=null)voiceSession.destroy();if(tts!=null){tts.stop();tts.shutdown();}super.onDestroy();}
}
