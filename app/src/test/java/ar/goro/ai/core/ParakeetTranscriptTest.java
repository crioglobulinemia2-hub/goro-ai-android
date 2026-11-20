package ar.goro.ai.core;

import nvidia.riva.asr.RecognizeResponse;
import nvidia.riva.asr.SpeechRecognitionAlternative;
import nvidia.riva.asr.SpeechRecognitionResult;
import nvidia.riva.asr.RecognitionConfig;
import nvidia.riva.AudioEncoding;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class ParakeetTranscriptTest {
 @Test public void usesTheHostedProfileThatRecognizedTheSpanishDiagnostic()throws Exception{
  // Golden service contract, verified with a real Diego WAV on 2026-11-05.
  // The older CTC Spanish function was ACTIVE but could not link its worker.
  assertEquals("grpc.nvcf.nvidia.com",ParakeetAsrGateway.SERVER);
  assertEquals(443,ParakeetAsrGateway.PORT);
  assertEquals("71203149-d3b7-4460-8231-1be2543a1fca",ParakeetAsrGateway.FUNCTION_ID);
  RecognitionConfig wire=RecognitionConfig.parseFrom(ParakeetAsrGateway.recognitionConfig(16000).toByteArray());
  assertEquals("multi",wire.getLanguageCode());
  assertEquals(AudioEncoding.LINEAR_PCM,wire.getEncoding());
  assertEquals(16000,wire.getSampleRateHertz());
  assertEquals(1,wire.getAudioChannelCount());
  assertEquals(1,wire.getMaxAlternatives());
  assertEquals(true,wire.getEnableAutomaticPunctuation());
 }
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
