package ar.goro.ai.voice;

import org.junit.Test;
import java.util.Arrays;
import java.util.Locale;
import static org.junit.Assert.*;

public class GoroVoiceSelectorTest {
 private GoroVoiceSelector.Candidate candidate(String name,String locale,boolean network){
  return new GoroVoiceSelector.Candidate(name,Locale.forLanguageTag(locale),network);
 }
 @Test public void prefersArgentineMaleVoice(){
  GoroVoiceSelector.Candidate us=candidate("es-us-x-sfb#male_1-local","es-US",false);
  GoroVoiceSelector.Candidate ar=candidate("es-ar-x-abc#male_1-local","es-AR",false);
  GoroVoiceSelector.Candidate female=candidate("es-ar-x-abc#female_1-local","es-AR",false);
  assertSame(ar,GoroVoiceSelector.chooseCandidate(Arrays.asList(us,ar,female)));
 }
 @Test public void neverFallsBackToFemaleVoice(){
  GoroVoiceSelector.Candidate female=candidate("es-ar-x-abc#female_1-local","es-AR",false);
  assertNull(GoroVoiceSelector.chooseCandidate(Arrays.asList(female)));
 }
 @Test public void rejectsMaleVoiceInWrongLanguage(){
  GoroVoiceSelector.Candidate english=candidate("en-us-x-sfb#male_1-local","en-US",false);
  assertNull(GoroVoiceSelector.chooseCandidate(Arrays.asList(english)));
 }
}
