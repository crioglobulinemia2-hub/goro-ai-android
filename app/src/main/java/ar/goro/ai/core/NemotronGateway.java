package ar.goro.ai.core;

import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Direct NVIDIA NIM chat-completions client. API token is supplied at runtime, never embedded. */
public final class NemotronGateway implements ModelGateway {
 public static final String ENDPOINT="https://integrate.api.nvidia.com/v1/chat/completions";
 public static final String MODEL="nvidia/nemotron-3.5-lightning-30b-a3b";
 private static final String SYSTEM_PROMPT=
   "Sos GORO, asistente personal del usuario. Hablás en español rioplatense natural, claro y directo. "+
   "Tu identidad vocal es masculina, pero el audio lo produce el motor TTS local. "+
   "No inventes acceso a dispositivos, correo, calendario, GPS ni sensores. "+
   "Usá las herramientas locales solo cuando la app las invoque explícitamente. "+
   "Si una capacidad no está conectada, decilo sin simular que ejecutaste algo. "+
   "No afirmes haber encendido el ZNP ni modificado eventos sin confirmación de la aplicación.";
 private final String apiKey;
 private final ExecutorService executor=Executors.newSingleThreadExecutor();
 private final Handler main=new Handler(Looper.getMainLooper());
 private final Map<String,ArrayDeque<JSONObject>> histories=new HashMap<>();
 public NemotronGateway(String apiKey){
  if(apiKey==null||!apiKey.startsWith("nvapi-"))throw new IllegalArgumentException("NVIDIA NIM API key required");
  this.apiKey=apiKey;
 }
 @Override public void request(String conversationId,String userText,Callback callback){
  if(callback==null)throw new IllegalArgumentException("callback required");
  if(userText==null||userText.trim().isEmpty()){callback.completed(null,"Empty request");return;}
  if(userText.length()>4000){callback.completed(null,"Request too long");return;}
  executor.execute(()->{
   HttpURLConnection connection=null;String reply=null,error=null;
   try{
    JSONArray messages=new JSONArray();
    messages.put(new JSONObject().put("role","system").put("content",SYSTEM_PROMPT));
    String id=conversationId==null?"goro-main":conversationId;
    ArrayDeque<JSONObject> history;
    synchronized(histories){history=histories.computeIfAbsent(id,k->new ArrayDeque<>());}
    synchronized(history){for(JSONObject item:history)messages.put(item);}
    JSONObject user=new JSONObject().put("role","user").put("content",userText);
    messages.put(user);
    JSONObject payload=new JSONObject();
    payload.put("model",MODEL);payload.put("messages",messages);
    payload.put("temperature",0.65);payload.put("top_p",0.9);
    payload.put("max_tokens",1024);payload.put("stream",false);
    connection=(HttpURLConnection)new URL(ENDPOINT).openConnection();
    connection.setRequestMethod("POST");connection.setConnectTimeout(12000);
    connection.setReadTimeout(45000);connection.setDoOutput(true);
    connection.setRequestProperty("Authorization","Bearer "+apiKey);
    connection.setRequestProperty("Content-Type","application/json");
    connection.setRequestProperty("Accept","application/json");
    byte[] bytes=payload.toString().getBytes(StandardCharsets.UTF_8);
    connection.setFixedLengthStreamingMode(bytes.length);
    try(OutputStream out=connection.getOutputStream()){out.write(bytes);}
    int status=connection.getResponseCode();
    InputStream stream=status>=200&&status<300?connection.getInputStream():connection.getErrorStream();
    byte[] response=readBounded(stream,256*1024);
    if(status<200||status>=300){
     JSONObject detail=new JSONObject(new String(response,StandardCharsets.UTF_8));
     String message=detail.optJSONObject("error")!=null?
       detail.optJSONObject("error").optString("message","HTTP "+status):"HTTP "+status;
     throw new IllegalStateException("NVIDIA NIM "+status+": "+message);
    }
    JSONObject json=new JSONObject(new String(response,StandardCharsets.UTF_8));
    JSONArray choices=json.optJSONArray("choices");
    if(choices==null||choices.length()==0)throw new IllegalStateException("NVIDIA returned no choices");
    JSONObject message=choices.getJSONObject(0).optJSONObject("message");
    reply=message==null?"":message.optString("content","");
    if(reply.trim().isEmpty())throw new IllegalStateException("NVIDIA returned an empty reply");
    synchronized(history){
     history.addLast(user);
     history.addLast(new JSONObject().put("role","assistant").put("content",reply));
     while(history.size()>12)history.removeFirst();
    }
   }catch(Exception e){
    String raw=e.getMessage()==null?"Connection failed":e.getMessage();
    error=raw.length()>240?raw.substring(0,240):raw;
   }finally{if(connection!=null)connection.disconnect();}
   final String result=reply,problem=error;
   main.post(()->callback.completed(result,problem));
  });
 }
 private static byte[] readBounded(InputStream stream,int limit)throws Exception{
  if(stream==null)throw new IllegalStateException("Empty HTTP response");
  try(InputStream in=stream;java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){
   byte[] buffer=new byte[8192];int read,total=0;
   while((read=in.read(buffer))!=-1){total+=read;if(total>limit)throw new IllegalStateException("NVIDIA response too large");out.write(buffer,0,read);}
   return out.toByteArray();
  }
 }
 public void close(){executor.shutdownNow();synchronized(histories){histories.clear();}}
}