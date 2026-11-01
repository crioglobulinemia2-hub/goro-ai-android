package ar.goro.ai.core;

import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** HTTPS client for a trusted, authenticated proxy. No NVIDIA API key in the APK. */
public final class NemotronGateway implements ModelGateway {
 private final String endpoint;
 private final ExecutorService executor=Executors.newSingleThreadExecutor();
 private final Handler main=new Handler(Looper.getMainLooper());
 public NemotronGateway(String endpoint) {
  if(endpoint==null || !endpoint.startsWith("https://") || endpoint.contains("@"))
   throw new IllegalArgumentException("HTTPS proxy endpoint required");
  this.endpoint=endpoint;
 }
 @Override public void request(String conversationId,String userText,Callback callback) {
  if(callback==null)throw new IllegalArgumentException("callback required");
  if(userText==null || userText.trim().isEmpty()){callback.completed(null,"Empty request");return;}
  if(userText.length()>4000){callback.completed(null,"Request too long");return;}
  executor.execute(()->{
   HttpURLConnection connection=null;
   String reply=null,error=null;
   try{
    connection=(HttpURLConnection)new URL(endpoint).openConnection();
    connection.setRequestMethod("POST");
    connection.setConnectTimeout(10000);
    connection.setReadTimeout(30000);
    connection.setDoOutput(true);
    connection.setRequestProperty("Content-Type","application/json; charset=utf-8");
    JSONObject payload=new JSONObject();
    payload.put("conversationId",conversationId==null?"":conversationId);
    payload.put("text",userText);
    byte[] bytes=payload.toString().getBytes(StandardCharsets.UTF_8);
    try(OutputStream out=connection.getOutputStream()){out.write(bytes);}
    int status=connection.getResponseCode();
    if(status<200||status>=300)throw new IllegalStateException("HTTP "+status);
    try(InputStream in=connection.getInputStream()){
     byte[] response=new byte[65536];
     int used=0,read;
     while(used<response.length && (read=in.read(response,used,response.length-used))!=-1)used+=read;
     if(used==response.length)throw new IllegalStateException("Response too large");
     JSONObject json=new JSONObject(new String(response,0,used,StandardCharsets.UTF_8));
     reply=json.optString("reply","");
     if(reply.isEmpty())throw new IllegalStateException("Empty model response");
    }
   }catch(Exception e){error="NVIDIA service unavailable or proxy not configured";}
   finally{if(connection!=null)connection.disconnect();}
   final String result=reply,problem=error;
   main.post(()->callback.completed(result,problem));
  });
 }
 public void close(){executor.shutdownNow();}
}
