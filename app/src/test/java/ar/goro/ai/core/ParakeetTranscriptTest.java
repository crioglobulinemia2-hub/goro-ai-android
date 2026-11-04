package ar.goro.ai.core;

import nvidia.riva.asr.RecognizeResponse;
import nvidia.riva.asr.SpeechRecognitionAlternative;
import nvidia.riva.asr.SpeechRecognitionResult;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class ParakeetTranscriptTest {
 @Test public void retainsEveryRecognizedSegment(){
  RecognizeResponse response=RecognizeResponse.newBuilder()
   .addResults(segment("Hola Goro."))
   .addResults(SpeechRecognitionResult.getDefaultInstance())
   .addResults(segment("  Recordame la reunión de mañana.  "))
   .build();
  assertEquals("Hola Goro. Recordame la reunión de mañana.",ParakeetAsrGateway.transcriptOf(response));
 }
 @Test public void doesNotTreatEmptyResultsAsSpeech(){
  assertEquals("",ParakeetAsrGateway.transcriptOf(RecognizeResponse.newBuilder().addResults(segment(" ")).build()));
 }
 private static SpeechRecognitionResult segment(String text){
  return SpeechRecognitionResult.newBuilder()
   .addAlternatives(SpeechRecognitionAlternative.newBuilder().setTranscript(text))
   .build();
 }
}
