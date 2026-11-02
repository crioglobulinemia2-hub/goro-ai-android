package ar.goro.ai.voice;

import android.speech.tts.Voice;
import org.junit.Test;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import static org.junit.Assert.*;

public class GoroVoiceSelectorTest {
 private Voice voice(String name,String locale){
  return new Voice(name,Locale.forLanguageTag(locale),Voice.QUALITY_NORMAL,
      Voice.LATENCY_NORMAL,false,new HashSet<>());
 }
 @Test public void prefersArgentineMaleVoice(){
  Voice us=voice("es-us-x-sfb#male_1-local","es-US");
  Voice ar=voice("es-ar-x-abc#male_1-local","es-AR");
  Voice female=voice("es-ar-x-abc#female_1-local","es-AR");
  assertEquals(ar,GoroVoiceSelector.select(new HashSet<>(Arrays.asList(us,ar,female))));
 }
 @Test public void neverFallsBackToFemaleVoice(){
  Voice female=voice("es-ar-x-abc#female_1-local","es-AR");
  assertNull(GoroVoiceSelector.select(new HashSet<>(Arrays.asList(female))));
 }
 @Test public void rejectsMaleVoiceInWrongLanguage(){
  Voice english=voice("en-us-x-sfb#male_1-local","en-US");
  assertNull(GoroVoiceSelector.select(new HashSet<>(Arrays.asList(english))));
 }
}
