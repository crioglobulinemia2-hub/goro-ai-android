package ar.goro.ai.voice;

import java.util.Locale;

/**
 * Distinguish speech that produced no transcript from service failures.
 * Service errors have bounded retries. Silence must not permanently disable
 * the microphone, and the original diagnostic must remain visible.
 */
public final class AsrFailurePolicy {
 public static final int MAX_SERVICE_FAILURES=3;
 private AsrFailurePolicy(){}
 public static boolean shouldPause(String error,int consecutiveErrors){
  if(error==null||error.trim().isEmpty())return false;
  String normalized=error.toUpperCase(Locale.ROOT);
  if(normalized.contains("UNAUTHENTICATED")||normalized.contains("PERMISSION_DENIED")
      ||normalized.contains("INVALID_ARGUMENT")||normalized.contains("UNIMPLEMENTED")
      ||normalized.contains("UNSUPPORTED")||normalized.contains("RESOURCE_EXHAUSTED"))
   return true;
  return consecutiveErrors>=MAX_SERVICE_FAILURES;
 }
 public static long retryDelayMillis(String error,int consecutiveErrors){
  if(error==null||error.trim().isEmpty())return 1500;
  return Math.min(8000,1500L*(1L<<Math.min(Math.max(0,consecutiveErrors-1),3)));
 }
 public static String safeReason(String error){
  if(error==null||error.trim().isEmpty())return "El servidor respondió sin palabras reconocidas.";
  String safe=error.replace('\n',' ').replace('\r',' ').trim();
  return safe.length()>220?safe.substring(0,220):safe;
 }
}
