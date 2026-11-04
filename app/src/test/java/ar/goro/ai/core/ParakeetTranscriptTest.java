package ar.goro.ai.core;
import nvidia.riva.asr.RecognizeResponse;
import nvidia.riva.asr.SpeechRecognitionResult;
import nvidia.riva.asr.SpeechRecognitionAlternative;
import org.junit.Test;
import static org.junit.Assert.*;
public class ParakeetTranscriptTest {
 @Test public void combinesSegmentsWithoutRepeatingAlternativeHypotheses(){
  RecognizeResponse response=RecognizeResponse.newBuilder()
   .addResults(SpeechRecognitionResult.newBuilder()
    .addAlternatives(SpeechRecognitionAlternative.newBuilder().setTranscript("Agendá la reunión"))
    .addAlternatives(SpeechRecognitionAlternative.newBuilder().setTranscript("Agendá la unión")))
   .addResults(SpeechRecognitionResult.newBuilder())
   .addResults(SpeechRecognitionResult.newBuilder()
    .addAlternatives(SpeechRecognitionAlternative.newBuilder().setTranscript(" mañana a las nueve. "))).build();
  assertEquals("Agendá la reunión mañana a las nueve.",ParakeetAsrGateway.transcript(response));
 }
 @Test public void emptyResponseIsEmpty(){assertEquals("",ParakeetAsrGateway.transcript(RecognizeResponse.getDefaultInstance()));}
}
