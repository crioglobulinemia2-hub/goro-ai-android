package ar.goro.ai.core;

import android.os.Looper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** Exercises concurrent HTTP requests locally; no NVIDIA service or credentials are used. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,manifest=Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NemotronSpeechGatewayLifecycleTest {
 private static final class HeldConnection extends HttpURLConnection {
  final CountDownLatch started=new CountDownLatch(1),release=new CountDownLatch(1);
  final AtomicBoolean disconnected=new AtomicBoolean();
  final byte[] response;
  HeldConnection(byte[] response)throws Exception{super(new URL("https://example.invalid"));this.response=response;}
  @Override public int getResponseCode(){
   started.countDown();
   try{if(!release.await(15,TimeUnit.SECONDS))throw new IllegalStateException("Test did not release request");}
   catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("Cancelled",e);}
   if(disconnected.get())throw new IllegalStateException("Disconnected");
   return 200;
  }
  @Override public InputStream getInputStream(){return new ByteArrayInputStream(response);}
  @Override public OutputStream getOutputStream(){return new ByteArrayOutputStream();}
  @Override public void disconnect(){disconnected.set(true);release.countDown();}
  @Override public boolean usingProxy(){return false;}
  @Override public void connect(){}
  void awaitStarted()throws Exception{assertTrue("Request never started",started.await(10,TimeUnit.SECONDS));}
 }
 private static HeldConnection catalogue()throws Exception{
  return new HeldConnection(("{\"es-US\":{\"voices\":[\""+NemotronSpeechGateway.VOICE+"\"]}}")
    .getBytes(StandardCharsets.UTF_8));
 }
 private static void awaitCallbacks(AtomicInteger count,int expected)throws Exception{
  long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
  while(count.get()<expected&&System.nanoTime()<deadline){shadowOf(Looper.getMainLooper()).idle();Thread.sleep(1);}
  shadowOf(Looper.getMainLooper()).idle();assertEquals(expected,count.get());
 }
 private static void awaitQueuedCallback()throws Exception{
  long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
  // Do not drain the paused main looper: cancellation must happen after post()
  // and before delivery, not while the network operation is still pending.
  while(shadowOf(Looper.getMainLooper()).isIdle()&&System.nanoTime()<deadline)Thread.sleep(1);
  assertFalse("Completion was not queued on the main looper",shadowOf(Looper.getMainLooper()).isIdle());
 }
 @Test public void checkingCatalogueDoesNotCancelSpeechAlreadyBeingSynthesized()throws Exception{
  HeldConnection speech=new HeldConnection(DiagnosticWavFixture.shortSpeech()),catalogue=catalogue();
  NemotronSpeechGateway gateway=new NemotronSpeechGateway("nvapi-test",
    endpoint->endpoint.equals(NemotronSpeechGateway.VOICES_ENDPOINT)?catalogue:speech);
  AtomicInteger results=new AtomicInteger();
  try{
   gateway.synthesize("Una respuesta en curso.",(wav,error)->{assertNull(error);assertNotNull(wav);results.incrementAndGet();});
   speech.awaitStarted();
   gateway.verifyDiego((ready,message)->{assertTrue(ready);results.incrementAndGet();});
   catalogue.awaitStarted();
   assertFalse("Catalogue check cancelled speech",speech.disconnected.get());
   speech.release.countDown();catalogue.release.countDown();awaitCallbacks(results,2);
  }finally{gateway.close();}
 }
 @Test public void replacingSpeechPreservesTheStartupCatalogueCheck()throws Exception{
  HeldConnection speech=new HeldConnection(DiagnosticWavFixture.shortSpeech()),catalogue=catalogue();
  NemotronSpeechGateway gateway=new NemotronSpeechGateway("nvapi-test",
    endpoint->endpoint.equals(NemotronSpeechGateway.VOICES_ENDPOINT)?catalogue:speech);
  AtomicInteger results=new AtomicInteger();
  try{
   gateway.verifyDiego((ready,message)->{assertTrue(ready);results.incrementAndGet();});
   catalogue.awaitStarted();
   gateway.cancelSynthesis();
   gateway.synthesize("La nueva respuesta.",(wav,error)->{assertNull(error);assertNotNull(wav);results.incrementAndGet();});
   speech.awaitStarted();
   assertFalse("Replacing speech cancelled startup readiness",catalogue.disconnected.get());
   speech.release.countDown();catalogue.release.countDown();awaitCallbacks(results,2);
  }finally{gateway.close();}
 }
 @Test public void cancellingConversationDisconnectsBothRequestTypes()throws Exception{
  HeldConnection speech=new HeldConnection(DiagnosticWavFixture.shortSpeech()),catalogue=catalogue();
  NemotronSpeechGateway gateway=new NemotronSpeechGateway("nvapi-test",
    endpoint->endpoint.equals(NemotronSpeechGateway.VOICES_ENDPOINT)?catalogue:speech);
  try{
   gateway.verifyDiego((ready,message)->fail("Cancelled catalogue callback escaped"));catalogue.awaitStarted();
   gateway.synthesize("Voz en curso.",(wav,error)->fail("Cancelled speech callback escaped"));speech.awaitStarted();
   gateway.cancelPending();
   assertTrue(speech.disconnected.get());assertTrue(catalogue.disconnected.get());
   shadowOf(Looper.getMainLooper()).idle();
  }finally{gateway.close();}
 }
 @Test public void cancelSynthesisRejectsSpeechCompletionAlreadyQueuedOnMain()throws Exception{
  shadowOf(Looper.getMainLooper()).idle();
  HeldConnection speech=new HeldConnection(DiagnosticWavFixture.shortSpeech());
  NemotronSpeechGateway gateway=new NemotronSpeechGateway("nvapi-test",endpoint->speech);
  AtomicInteger results=new AtomicInteger();
  try{
   gateway.synthesize("Respuesta anterior.",(wav,error)->results.incrementAndGet());
   speech.awaitStarted();speech.release.countDown();awaitQueuedCallback();
   assertEquals(0,results.get());
   gateway.cancelSynthesis();shadowOf(Looper.getMainLooper()).idle();
   assertEquals("Cancelled queued speech reached the client",0,results.get());
  }finally{gateway.close();}
 }
 @Test public void cancelSynthesisPreservesCatalogueCompletionAlreadyQueuedOnMain()throws Exception{
  shadowOf(Looper.getMainLooper()).idle();
  HeldConnection catalogue=catalogue();
  NemotronSpeechGateway gateway=new NemotronSpeechGateway("nvapi-test",endpoint->catalogue);
  AtomicInteger results=new AtomicInteger();
  try{
   gateway.verifyDiego((ready,message)->{assertTrue(ready);results.incrementAndGet();});
   catalogue.awaitStarted();catalogue.release.countDown();awaitQueuedCallback();
   assertEquals(0,results.get());
   gateway.cancelSynthesis();shadowOf(Looper.getMainLooper()).idle();
   assertEquals("Speech cancellation suppressed startup readiness",1,results.get());
  }finally{gateway.close();}
 }
 @Test public void cancelPendingRejectsSpeechCompletionAlreadyQueuedOnMain()throws Exception{
  shadowOf(Looper.getMainLooper()).idle();
  HeldConnection speech=new HeldConnection(DiagnosticWavFixture.shortSpeech());
  NemotronSpeechGateway gateway=new NemotronSpeechGateway("nvapi-test",endpoint->speech);
  AtomicInteger results=new AtomicInteger();
  try{
   gateway.synthesize("Respuesta anterior.",(wav,error)->results.incrementAndGet());
   speech.awaitStarted();speech.release.countDown();awaitQueuedCallback();
   assertEquals(0,results.get());
   gateway.cancelPending();shadowOf(Looper.getMainLooper()).idle();
   assertEquals("Cancelled queued speech reached the client",0,results.get());
  }finally{gateway.close();}
 }
 @Test public void cancelPendingRejectsCatalogueCompletionAlreadyQueuedOnMain()throws Exception{
  shadowOf(Looper.getMainLooper()).idle();
  HeldConnection catalogue=catalogue();
  NemotronSpeechGateway gateway=new NemotronSpeechGateway("nvapi-test",endpoint->catalogue);
  AtomicInteger results=new AtomicInteger();
  try{
   gateway.verifyDiego((ready,message)->results.incrementAndGet());
   catalogue.awaitStarted();catalogue.release.countDown();awaitQueuedCallback();
   assertEquals(0,results.get());
   gateway.cancelPending();shadowOf(Looper.getMainLooper()).idle();
   assertEquals("Cancelled queued catalogue reached the client",0,results.get());
  }finally{gateway.close();}
 }
 @Test public void invalidReplacementCancelsThePreviousSpeechAndKeepsCatalogue()throws Exception{
  HeldConnection speech=new HeldConnection(DiagnosticWavFixture.shortSpeech()),catalogue=catalogue();
  AtomicInteger requests=new AtomicInteger(),results=new AtomicInteger();
  NemotronSpeechGateway gateway=new NemotronSpeechGateway("nvapi-test",endpoint->{
   requests.incrementAndGet();
   return endpoint.equals(NemotronSpeechGateway.VOICES_ENDPOINT)?catalogue:speech;
  });
  try{
   gateway.verifyDiego((ready,message)->{assertTrue(ready);results.incrementAndGet();});catalogue.awaitStarted();
   gateway.synthesize("Respuesta anterior.",(wav,error)->fail("Replaced audio reached the client"));speech.awaitStarted();
   gateway.synthesize("  ",(wav,error)->{assertNull(wav);assertEquals("Texto vacío",error);results.incrementAndGet();});
   assertTrue("Invalid replacement left old audio running",speech.disconnected.get());
   assertFalse("Invalid replacement cancelled startup readiness",catalogue.disconnected.get());
   catalogue.release.countDown();awaitCallbacks(results,2);
   assertEquals("Invalid input must not reach NVIDIA",2,requests.get());
  }finally{gateway.close();}
 }
 @Test public void closedGatewayDoesNotDeliverValidationCallbacks()throws Exception{
  NemotronSpeechGateway gateway=new NemotronSpeechGateway("nvapi-test",endpoint->{throw new AssertionError("Closed gateway opened HTTP");});
  gateway.close();
  gateway.synthesize(null,(wav,error)->fail("Closed gateway delivered a callback"));
  gateway.synthesize(new String(new char[1801]).replace('\0','x'),(wav,error)->fail("Closed gateway delivered a callback"));
  shadowOf(Looper.getMainLooper()).idle();
 }
}
