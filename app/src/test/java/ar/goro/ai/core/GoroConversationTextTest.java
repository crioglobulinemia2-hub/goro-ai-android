package ar.goro.ai.core;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class GoroConversationTextTest {
 @Test public void identityWorksWithoutModelAndKeepsUserSeparate(){
  assertEquals(GoroIdentity.INTRODUCTION,GoroIdentity.localReply("Goro, quién sos?"));
  assertTrue(GoroIdentity.localReply("Cómo me llamo?").startsWith("Sos Matías"));
  assertNull(GoroIdentity.localReply("Quién sos para decirme qué tengo que hacer con mi agenda?"));
 }
 @Test public void fragmentsStartSpeechBeforeTheAnswerIsCompleteAndNeverRepeat(){
  StreamingSpeechText stream=new StreamingSpeechText();
  assertTrue(stream.append("Esta es la primera frase").isEmpty());
  assertTrue(stream.append(".").isEmpty());
  assertEquals(Arrays.asList("Esta es la primera frase."),stream.append(" Todavía estoy"));
  assertTrue(stream.append(" respondiendo.").isEmpty());
  assertEquals(Arrays.asList("Todavía estoy respondiendo."),stream.finish());
  assertTrue(stream.finish().isEmpty());
 }
 @Test public void splitDecimalIsNotSpokenAsTwoSentences(){
  StreamingSpeechText stream=new StreamingSpeechText();
  assertTrue(stream.append("El resultado del cálculo es 123.").isEmpty());
  assertTrue(stream.append("45 y está confirmado.").isEmpty());
  assertEquals(Arrays.asList("El resultado del cálculo es 123.45 y está confirmado."),stream.finish());
 }
 @Test public void largeUnpunctuatedResponseIsBoundedWithoutLosingWords(){
  StreamingSpeechText stream=new StreamingSpeechText();
  String input=String.join(" ",Collections.nCopies(200,"palabra"));
  List<String> all=stream.append(input);assertFalse(all.isEmpty());all.addAll(stream.finish());
  assertEquals(input,String.join(" ",all));
  for(String s:all)assertTrue(s.length()<=260);
 }
}
