package ar.goro.ai.core;

import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * NVIDIA NIM Chat Completions client, aligned with the validated Cabezón request:
 * same endpoint, model and Bearer nvapi authentication. Streams user-facing content;
 * reasoning_content is deliberately ignored and never exposed in the application UI.
 */
public final class NemotronGateway implements ModelGateway {
 public static final String ENDPOINT="https://integrate.api.nvidia.com/v1/chat/completions";
 public static final String MODEL="nvidia/nemotron-3.5-super-vl-120b-a12b";
 private static final int MAX_REPLY_CHARS=24000;
 private static final String SYSTEM_PROMPT=
   "Sos GORO, asistente personal del usuario. Hablás en español rioplatense natural, claro y directo. "+
   "Tu identidad vocal es masculina; la voz se sintetiza mediante NVIDIA Magpie Diego, nunca mediante TTS local. "+
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
  requestInternal(conversationId,userText,null,callback);
 }
 /** Optional image URL input for the Super-VL model; only explicitly supplied URLs are sent. */
 public void requestWithImage(String conversationId,String userText,String imageUrl,Callback callback){
  if(imageUrl==null||!(imageUrl.startsWith("https://")||imageUrl.startsWith("data:image/"))){
   if(callback!=null)callback.completed(null,"La imagen debe ser una URL HTTPS o un data URL de imagen.");
   return;
  }
  requestInternal(conversationId,userText,imageUrl,callback);
 }
 private void requestInternal(String conversationId,String userText,String imageUrl,Callback callback){
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
    JSONObject user;
    if(imageUrl==null){
     user=new JSONObject().put("role","user").put("content",userText);
    }else{
     JSONArray content=new JSONArray();
     content.put(new JSONObject().put("type","text").put("text",userText));
     content.put(new JSONObject().put("type","image_url")
       .put("image_url",new JSONObject().put("url",imageUrl)));
     user=new JSONObject().put("role","user").put("content",content);
    }
    messages.put(user);
    JSONObject payload=new JSONObject();
    payload.put("model",MODEL);payload.put("messages",messages);
    payload.put("temperature",1.0);payload.put("top_p",0.95);
    payload.put("max_tokens",2048);payload.put("seed",42);
    payload.put("stream",true);
    connection=(HttpURLConnection)new URL(ENDPOINT).openConnection();
    connection.setRequestMethod("POST");connection.setConnectTimeout(15000);
    connection.setReadTimeout(90000);connection.setDoOutput(true);
    connection.setRequestProperty("Authorization","Bearer "+apiKey);
    connection.setRequestProperty("Content-Type","application/json");
    connection.setRequestProperty("Accept","text/event-stream");
    byte[] bytes=payload.toString().getBytes(StandardCharsets.UTF_8);
    connection.setFixedLengthStreamingMode(bytes.length);
    try(OutputStream out=connection.getOutputStream()){out.write(bytes);}
    int status=connection.getResponseCode();
    if(status<200||status>=300){
     InputStream failure=connection.getErrorStream();
     byte[] response=readBounded(failure,256*1024);
     String detail=new String(response,StandardCharsets.UTF_8);
     throw new IllegalStateException("NVIDIA NIM "+status+": "+(detail.length()>240?detail.substring(0,240):detail));
    }
    StringBuilder accumulated=new StringBuilder();
    NvidiaStreamParser parser=new NvidiaStreamParser();
    try(BufferedReader reader=new BufferedReader(new InputStreamReader(connection.getInputStream(),StandardCharsets.UTF_8))){
     String line;
     while((line=reader.readLine())!=null){
      if(!line.startsWith("data:"))continue;
      String piece=parser.accept(line);
      if(!piece.isEmpty()){
       if(accumulated.length()+piece.length()>MAX_REPLY_CHARS)
        throw new IllegalStateException("NVIDIA reply exceeded the application limit");
       accumulated.append(piece);
      }
      if(parser.isDone())break;
     }
    }
    if(!parser.isDone()&&parser.hasAnswer())
      throw new IllegalStateException("NVIDIA streaming response ended before completion");
    reply=accumulated.toString().trim();
    if(reply.isEmpty())throw new IllegalStateException("NVIDIA returned no answer content");
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
  if(stream==null)return new byte[0];
  try(InputStream in=stream;java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){
   byte[] buffer=new byte[8192];int read,total=0;
   while((read=in.read(buffer))!=-1){total+=read;if(total>limit)throw new IllegalStateException("NVIDIA error response too large");out.write(buffer,0,read);}
   return out.toByteArray();
  }
 }
 public void close(){executor.shutdownNow();synchronized(histories){histories.clear();}}
}
