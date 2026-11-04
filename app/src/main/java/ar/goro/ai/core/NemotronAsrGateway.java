package ar.goro.ai.core;

import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** NVIDIA Parakeet RNNT multilingual ASR (Spanish) for PCM16 mono decoded from Z02 RCSP. */
public final class NemotronAsrGateway {
 public static final String ENDPOINT="https://71203149-d3b7-4460-8231-1be2543a1fca.invocation.api.nvcf.nvidia.com/v1/audio/transcriptions";
 public interface Callback {void completed(String transcript,String error);}
 private final String apiKey;
 private final ExecutorService executor=Executors.newSingleThreadExecutor();
 private final Handler main=new Handler(Looper.getMainLooper());
 public NemotronAsrGateway(String apiKey){
  if(apiKey==null||!apiKey.startsWith("nvapi-"))throw new IllegalArgumentException("NVIDIA NIM API key required");
  this.apiKey=apiKey;
 }
 public void transcribe(byte[] pcm16Mono,int sampleRate,Callback callback){
  if(callback==null)throw new IllegalArgumentException("callback required");
  if(pcm16Mono==null||pcm16Mono.length<3200||pcm16Mono.length>480000||pcm16Mono.length%2!=0||sampleRate!=16000){
   callback.completed(null,"El audio del micrófono Z02 no tiene un formato PCM16 mono válido.");return;
  }
  executor.execute(()->{
   HttpURLConnection connection=null;String transcript=null,error=null;
   String boundary="----GOROASR"+UUID.randomUUID().toString().replace("-","");
   try{
    byte[] wav=wav(pcm16Mono,sampleRate);
    connection=(HttpURLConnection)new URL(ENDPOINT).openConnection();
    connection.setRequestMethod("POST");connection.setConnectTimeout(15000);connection.setReadTimeout(45000);connection.setDoOutput(true);
    connection.setRequestProperty("Authorization","Bearer "+apiKey);
    connection.setRequestProperty("Content-Type","multipart/form-data; boundary="+boundary);
    connection.setRequestProperty("Accept","application/json");
    try(OutputStream out=connection.getOutputStream()){
     field(out,boundary,"language","es-US");
     field(out,boundary,"response_format","json");
     out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"goro-z02.wav\"\r\nContent-Type: audio/wav\r\n\r\n").getBytes(StandardCharsets.UTF_8));
     out.write(wav);out.write("\r\n".getBytes(StandardCharsets.UTF_8));
     out.write(("--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
    }
    int status=connection.getResponseCode();
    InputStream stream=status>=200&&status<300?connection.getInputStream():connection.getErrorStream();
    byte[] response=readBounded(stream,256*1024);
    if(status<200||status>=300){
     String detail=new String(response,StandardCharsets.UTF_8);
     throw new IllegalStateException("NVIDIA ASR HTTP "+status+": "+(detail.length()>180?detail.substring(0,180):detail));
    }
    transcript=new JSONObject(new String(response,StandardCharsets.UTF_8)).optString("text","").trim();
    if(transcript.isEmpty())throw new IllegalStateException("NVIDIA ASR no reconoció palabras en el audio recibido.");
   }catch(Exception e){error=e.getMessage()==null?"No se pudo transcribir el audio del Z02.":e.getMessage();}
   finally{if(connection!=null)connection.disconnect();}
   final String result=transcript,problem=error;
   main.post(()->callback.completed(result,problem));
  });
 }
 private static byte[] wav(byte[] pcm,int rate)throws Exception{
  ByteArrayOutputStream out=new ByteArrayOutputStream(44+pcm.length);
  out.write("RIFF".getBytes(StandardCharsets.US_ASCII));write32(out,36+pcm.length);
  out.write("WAVEfmt ".getBytes(StandardCharsets.US_ASCII));write32(out,16);
  write16(out,1);write16(out,1);write32(out,rate);write32(out,rate*2);
  write16(out,2);write16(out,16);out.write("data".getBytes(StandardCharsets.US_ASCII));
  write32(out,pcm.length);out.write(pcm);return out.toByteArray();
 }
 private static void field(OutputStream out,String boundary,String name,String value)throws Exception{
  out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\""+name+"\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
  out.write(value.getBytes(StandardCharsets.UTF_8));out.write("\r\n".getBytes(StandardCharsets.UTF_8));
 }
 private static void write16(OutputStream out,int value)throws Exception{out.write(value&255);out.write((value>>>8)&255);}
 private static void write32(OutputStream out,int value)throws Exception{out.write(value&255);out.write((value>>>8)&255);out.write((value>>>16)&255);out.write((value>>>24)&255);}
 private static byte[] readBounded(InputStream stream,int limit)throws Exception{
  if(stream==null)throw new IllegalStateException("NVIDIA ASR devolvió una respuesta vacía");
  try(InputStream in=stream;ByteArrayOutputStream out=new ByteArrayOutputStream()){
   byte[] buffer=new byte[8192];int n,total=0;
   while((n=in.read(buffer))!=-1){total+=n;if(total>limit)throw new IllegalStateException("Respuesta ASR demasiado grande");out.write(buffer,0,n);}
   return out.toByteArray();
  }
 }
 public void close(){executor.shutdownNow();}
}
