package ar.goro.ai.core;

import android.os.Handler;
import android.os.Looper;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.StatusRuntimeException;
import io.grpc.okhttp.OkHttpChannelBuilder;
import io.grpc.stub.MetadataUtils;
import nvidia.riva.AudioEncoding;
import nvidia.riva.asr.RecognitionConfig;
import nvidia.riva.asr.RecognizeRequest;
import nvidia.riva.asr.RecognizeResponse;
import nvidia.riva.asr.RivaSpeechRecognitionGrpc;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** NVIDIA-hosted Parakeet CTC Spanish ASR through the documented Riva gRPC API. */
public final class ParakeetAsrGateway {
 public static final String SERVER="grpc.nvcf.nvidia.com";
 public static final int PORT=443;
 public static final String FUNCTION_ID="a9eeee8f-b509-4712-b19d-194361fa5f31";
 public static final String LANGUAGE="es-US";
 public interface Callback {void completed(String transcript,String error);}
 private final String apiKey;
 private final ManagedChannel channel;
 private final ExecutorService executor=Executors.newSingleThreadExecutor();
 private volatile boolean closed;
 private final java.util.concurrent.atomic.AtomicLong generation=new java.util.concurrent.atomic.AtomicLong();
 private volatile io.grpc.Context.CancellableContext activeContext;
 private final Handler main=new Handler(Looper.getMainLooper());
 public ParakeetAsrGateway(String apiKey){
  if(apiKey==null||!apiKey.startsWith("nvapi-"))throw new IllegalArgumentException("NVIDIA NIM API key required");
  this.apiKey=apiKey;
  channel=OkHttpChannelBuilder.forAddress(SERVER,PORT).useTransportSecurity().build();
 }
 public void transcribe(byte[] pcm16Mono,int sampleRate,Callback callback){
  if(callback==null)throw new IllegalArgumentException("callback required");
  if(pcm16Mono==null||pcm16Mono.length<3200||pcm16Mono.length>480000
      ||pcm16Mono.length%2!=0||sampleRate!=16000){
   callback.completed(null,"El audio del micrófono Z02 no tiene formato PCM16 mono a 16 kHz.");
   return;
  }
  if(closed)return;
  cancelPending();
  long requestGeneration=generation.get();
  byte[] audio=pcm16Mono.clone();
  executor.execute(()->{
   if(closed||generation.get()!=requestGeneration)return;
   String transcript=null,error=null;
   io.grpc.Context.CancellableContext requestContext=io.grpc.Context.current().withCancellation();
   activeContext=requestContext;
   try{
    if(closed||generation.get()!=requestGeneration)return;
    RecognitionConfig config=RecognitionConfig.newBuilder()
      .setEncoding(AudioEncoding.LINEAR_PCM)
      .setSampleRateHertz(sampleRate)
      .setLanguageCode(LANGUAGE)
      .setAudioChannelCount(1)
      .setMaxAlternatives(1)
      .setEnableAutomaticPunctuation(true)
      .build();
    RecognizeRequest request=RecognizeRequest.newBuilder()
      .setConfig(config)
      .setAudio(ByteString.copyFrom(audio))
      .build();
    Metadata headers=new Metadata();
    headers.put(Metadata.Key.of("function-id",Metadata.ASCII_STRING_MARSHALLER),FUNCTION_ID);
    headers.put(Metadata.Key.of("authorization",Metadata.ASCII_STRING_MARSHALLER),"Bearer "+apiKey);
    RivaSpeechRecognitionGrpc.RivaSpeechRecognitionBlockingStub stub=
      RivaSpeechRecognitionGrpc.newBlockingStub(channel)
        .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers))
        .withDeadlineAfter(35,TimeUnit.SECONDS);
    RecognizeResponse response=requestContext.call(()->stub.recognize(request));
    if(response.getResultsCount()>0
        &&response.getResults(0).getAlternativesCount()>0){
      transcript=response.getResults(0).getAlternatives(0).getTranscript().trim();
    }
    if(transcript==null||transcript.isEmpty())
      throw new IllegalStateException("Parakeet no detectó palabras en el audio recibido.");
   }catch(StatusRuntimeException e){
    error="NVIDIA Parakeet/Riva: "+e.getStatus().getCode()
      +(e.getStatus().getDescription()==null?"":" · "+e.getStatus().getDescription());
   }catch(Exception e){
    error=e.getMessage()==null?"Error de reconocimiento NVIDIA Parakeet":e.getMessage();
   }
   finally{requestContext.cancel(null);if(activeContext==requestContext)activeContext=null;}
   final String text=transcript,problem=error;
   main.post(()->{if(!closed&&generation.get()==requestGeneration)callback.completed(text,problem);});
  });
 }
 public void cancelPending(){generation.incrementAndGet();io.grpc.Context.CancellableContext context=activeContext;if(context!=null)context.cancel(null);}
 public void close(){
  closed=true;cancelPending();main.removeCallbacksAndMessages(null);
  executor.shutdownNow();
  channel.shutdownNow();
 }
}
