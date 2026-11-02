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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** NVIDIA hosted Magpie TTS. Uses the same nvapi credential as Nemotron chat. */
public final class NemotronSpeechGateway {
 public static final String ENDPOINT="https://877104f7-e885-42b9-8de8-f6e4c6303969.invocation.api.nvcf.nvidia.com/v1/audio/synthesize";
 public static final String VOICE="Magpie-Multilingual.ES-US.Diego";
 public static final String LANGUAGE="es-US";
 public interface Callback {void completed(byte[] wav,String error);}
 private final String apiKey;
 private final ExecutorService executor=Executors.newSingleThreadExecutor();
 private final Handler main=new Handler(Looper.getMainLooper());
 public NemotronSpeechGateway(String apiKey){
  if(apiKey==null||!apiKey.startsWith("nvapi-"))throw new IllegalArgumentException("NVIDIA NIM API key required");
  this.apiKey=apiKey;
 }
 public void synthesize(String text,Callback callback){
  if(callback==null)throw new IllegalArgumentException("callback required");
  if(text==null||text.trim().isEmpty()){callback.completed(null,"Texto vacío");return;}
  if(text.length()>1800){callback.completed(null,"Texto demasiado largo para una sola síntesis");return;}
  executor.execute(()->{
   HttpURLConnection connection=null;byte[] wav=null;String error=null;
   String boundary="----GORO"+UUID.randomUUID().toString().replace("-","");
   try{
    connection=(HttpURLConnection)new URL(ENDPOINT).openConnection();
    connection.setRequestMethod("POST");connection.setConnectTimeout(15000);connection.setReadTimeout(60000);
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
    if(response.length<44||response[0]!='R'||response[1]!='I'||response[2]!='F'||response[3]!='F'||
       response[8]!='W'||response[9]!='A'||response[10]!='V'||response[11]!='E')
      throw new IllegalStateException("NVIDIA Speech devolvió un audio WAV inválido");
    wav=response;
   }catch(Exception e){error=e.getMessage()==null?"Error de síntesis NVIDIA":e.getMessage();}
   finally{if(connection!=null)connection.disconnect();}
   final byte[] audio=wav;final String problem=error;
   main.post(()->callback.completed(audio,problem));
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
 public void close(){executor.shutdownNow();}
}
