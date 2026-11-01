package ar.goro.ai;

import android.app.Activity;
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
  @Override public void onCreate(Bundle saved) {
    super.onCreate(saved);
    memory=getSharedPreferences("goro_memory",MODE_PRIVATE);
    memoryStore=new MemoryStore(this);
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
    if(voiceReady) { if(voiceSession!=null)voiceSession.pauseForResponse(); tts.speak(message,TextToSpeech.QUEUE_FLUSH,null,"goro_reply"); }
    else if(voiceSession!=null)voiceSession.resumeAfterResponse();
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
  @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==41&&results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED){voiceActive=true;voiceSession.start();}}
  @Override protected void onDestroy(){if(voiceSession!=null)voiceSession.destroy();if(tts!=null){tts.stop();tts.shutdown();}super.onDestroy();}
}
