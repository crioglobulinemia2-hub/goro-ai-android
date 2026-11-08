package ar.goro.ai.voice;

import org.junit.Test;
import static org.junit.Assert.*;

public class AsrFailurePolicyTest {
 @Test public void silenceDoesNotCountAsAServiceFailure(){
  assertTrue(AsrFailurePolicy.isNoSpeech("Parakeet no detectó palabras en el audio recibido."));
  assertFalse(AsrFailurePolicy.shouldPause("Parakeet no detectó palabras en el audio recibido.",99));
 }
 @Test public void transientServiceErrorsGetFiveAttempts(){
  String error="NVIDIA Parakeet/Riva: UNAVAILABLE";
  assertFalse(AsrFailurePolicy.shouldPause(error,1));
  assertFalse(AsrFailurePolicy.shouldPause(error,2));
  assertFalse(AsrFailurePolicy.shouldPause(error,3));
  assertFalse(AsrFailurePolicy.shouldPause(error,4));
  assertTrue(AsrFailurePolicy.shouldPause(error,5));
 }
 @Test public void authenticationErrorsStopImmediatelyWithVisibleDiagnostic(){
  assertTrue(AsrFailurePolicy.shouldPause("NVIDIA Parakeet/Riva: UNAUTHENTICATED",1));
  assertTrue(AsrFailurePolicy.shouldPause("NVIDIA Parakeet/Riva: PERMISSION_DENIED",1));
 }
 @Test public void retryDelayIsBoundedAndIncreases(){
  assertEquals(1500,AsrFailurePolicy.retryDelayMillis("UNAVAILABLE",1));
  assertEquals(3000,AsrFailurePolicy.retryDelayMillis("UNAVAILABLE",2));
  assertEquals(8000,AsrFailurePolicy.retryDelayMillis("UNAVAILABLE",8));
 }
 @Test public void reasonIsKeptAndBounded(){
  assertEquals("UNAVAILABLE · server busy",AsrFailurePolicy.safeReason("UNAVAILABLE · server busy"));
  assertEquals(220,AsrFailurePolicy.safeReason("x".repeat(300)).length());
 }
}
