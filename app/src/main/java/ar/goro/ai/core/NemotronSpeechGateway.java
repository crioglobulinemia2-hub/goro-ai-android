package ar.goro.ai.core;

import android.os.Handler;
import android.os.Looper;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** NVIDIA hosted Magpie TTS. Uses the same nvapi credential as Nemotron chat. */
public final class NemotronSpeechGateway {
 public static final String ENDPOINT="https://877104f7-e885-42b9-8de8-f6e4c6303969.invocation.api.nvcf.nvidia.com/v1/audio/synthesize";
 public static final String VOICE="Magpie-Multilingual.ES-US.Diego";
 public static final String LANGUAGE="es-US";
 public interface Callback {void completed(byte[] wav,String error);}
 public interface AvailabilityCallback {void completed(boolean available,String message);}
 public static final String VOICES_ENDPOINT=
   "https://877104f7-e885-42b9-8de8-f6e4c6303969.invocation.api.nvcf.nvidia.com/v1/audio/list_voices";
 interface ConnectionFactory {HttpURLConnection open(String endpoint)throws Exception;}
 private final String apiKey;
 private final ConnectionFactory connections;
 private final HttpRequestScope synthesisRequests=new HttpRequestScope();
 private final HttpRequestScope catalogueRequests=new HttpRequestScope();
 private final Handler main=new Handler(Looper.getMainLooper());
 public NemotronSpeechGateway(String apiKey){
  this(apiKey,endpoint->(HttpURLConnection)new URL(endpoint).openConnection());
 }
 NemotronSpeechGateway(String apiKey,ConnectionFactory connections){
  if(apiKey==null||!apiKey.startsWith("nvapi-"))throw new IllegalArgumentException("NVIDIA NIM API key required");
  if(connections==null)throw new IllegalArgumentException("connection factory required");
  this.apiKey=apiKey;this.connections=connections;
 }
 /** Check the actual remote voice catalogue, not merely the local string constant. */
 public void verifyDiego(AvailabilityCallback callback){
  if(callback==null)throw new IllegalArgumentException("callback required");
  catalogueRequests.submit(ticket->{
   HttpURLConnection connection=null;boolean found=false;String detail=null;
   try{
    connection=connections.open(VOICES_ENDPOINT);
    ticket.attach(connection);
    connection.setRequestMethod("GET");
    connection.setConnectTimeout(12000);connection.setReadTimeout(20000);
    connection.setRequestProperty("Authorization","Bearer "+apiKey);
    connection.setRequestProperty("Accept","application/json");
    int status=connection.getResponseCode();
    if(status!=200)throw new IllegalStateException("Catálogo de voces NVIDIA HTTP "+status);
    String response=new String(readBounded(connection.getInputStream(),256*1024),StandardCharsets.UTF_8);
    found=NvidiaVoiceCatalog.contains(response,VOICE);
    detail=found?"NVIDIA confirmó Magpie Diego en su catálogo.":
      "NVIDIA respondió, pero no anunció la voz Diego solicitada.";
   }catch(Exception e){
    detail="No se pudo verificar Magpie Diego: "+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage());
   }finally{if(connection!=null){ticket.detach(connection);connection.disconnect();}}
   final boolean available=found;final String result=detail;
   main.post(()->{if(ticket.current())callback.completed(available,result);});
  });
 }
 public void synthesize(String text,Callback callback){
  if(callback==null)throw new IllegalArgumentException("callback required");
  synthesisRequests.submit(ticket->{
   HttpURLConnection connection=null;byte[] wav=null;String error=null;
   String boundary="----GORO"+UUID.randomUUID().toString().replace("-","");
   try{
    // Even rejected input replaces the previous turn. Validating before submit()
    // left the old HTTP request alive and allowed its stale audio to arrive later.
    if(text==null||text.trim().isEmpty())throw new IllegalArgumentException("Texto vacío");
    if(text.length()>1800)throw new IllegalArgumentException("Texto demasiado largo para una sola síntesis");
    connection=connections.open(ENDPOINT);
    ticket.attach(connection);
    connection.setRequestMethod("POST");connection.setConnectTimeout(10000);connection.setReadTimeout(25000);
    connection.setDoOutput(true);connection.setRequestProperty("Authorization","Bearer "+apiKey);
    connection.setRequestProperty("Content-Type","multipart/form-data; boundary="+boundary);
    connection.setRequestProperty("Accept","audio/wav, application/json");
    try(OutputStream out=connection.getOutputStream()){
     field(out,boundary,"text",text);
     field(out,boundary,"language",LANGUAGE);
     field(out,boundary,"voice",VOICE);
     field(out,boundary,"sample_rate_hz","22050");
     field(out,boundary,"encoding","LINEAR_PCM");
     out.write(("--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
    }
    int status=connection.getResponseCode();
    InputStream stream=status>=200&&status<300?connection.getInputStream():connection.getErrorStream();
    byte[] response=readBounded(stream,12*1024*1024);
    if(status<200||status>=300){
     String detail=new String(response,StandardCharsets.UTF_8);
     throw new IllegalStateException("NVIDIA Speech "+status+": "+(detail.length()>220?detail.substring(0,220):detail));
    }
    NvidiaWavValidator.Info info=NvidiaWavValidator.inspect(response);
    if(info.durationMs<100)throw new IllegalStateException("La síntesis NVIDIA devolvió menos de 100 ms de audio");
    wav=response;
   }catch(Exception e){error=e.getMessage()==null?"Error de síntesis NVIDIA":e.getMessage();}
   finally{if(connection!=null){ticket.detach(connection);connection.disconnect();}}
   final byte[] audio=wav;final String problem=error;
   main.post(()->{if(ticket.current())callback.completed(audio,problem);});
  });
 }
 private static void field(OutputStream out,String boundary,String name,String value)throws Exception{
  out.write(("--"+boundary+"\r\n").getBytes(StandardCharsets.UTF_8));
  out.write(("Content-Disposition: form-data; name=\""+name+"\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
  out.write(value.getBytes(StandardCharsets.UTF_8));
  out.write("\r\n".getBytes(StandardCharsets.UTF_8));
 }
 private static byte[] readBounded(InputStream stream,int limit)throws Exception{
  if(stream==null)throw new IllegalStateException("NVIDIA Speech devolvió una respuesta vacía");
  try(InputStream in=stream;ByteArrayOutputStream out=new ByteArrayOutputStream()){
   byte[] buffer=new byte[8192];int n,total=0;
   while((n=in.read(buffer))!=-1){total+=n;if(total>limit)throw new IllegalStateException("Audio NVIDIA excede el límite permitido");out.write(buffer,0,n);}
   return out.toByteArray();
  }
 }
 /** Replacing speech must not cancel the startup check that enables hands-free mode. */
 public void cancelSynthesis(){synthesisRequests.cancel();}
 public void cancelPending(){synthesisRequests.cancel();catalogueRequests.cancel();}
 public void close(){synthesisRequests.close();catalogueRequests.close();main.removeCallbacksAndMessages(null);}
}
