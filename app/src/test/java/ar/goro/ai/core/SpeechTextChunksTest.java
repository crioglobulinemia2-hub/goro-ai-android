package ar.goro.ai.core;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class SpeechTextChunksTest {
 @Test public void preservesLongReplyExactly(){
  String input=("Matías, revisamos la agenda. La próxima reunión tiene información completa. ").repeat(100);
  List<String> parts=SpeechTextChunks.split(input,1600);
  assertTrue(parts.size()>1);
  assertEquals(input,String.join("",parts));
  for(String part:parts)assertTrue(part.length()<=1600);
 }
 @Test public void doesNotSplitSurrogatesOrCombiningAccents(){
  String input=("á😀e\u0301 ").repeat(1000);
  List<String> parts=SpeechTextChunks.split(input,97);
  assertEquals(input,String.join("",parts));
  for(String part:parts){
   assertFalse(Character.isHighSurrogate(part.charAt(part.length()-1)));
   assertFalse(Character.isLowSurrogate(part.charAt(0)));
   assertFalse(part.startsWith("\u0301"));
   assertTrue(part.length()<=97);
  }
 }
 @Test public void splitsUnbrokenLongTextWithoutLoss(){
  String input="x".repeat(5001);
  List<String> parts=SpeechTextChunks.split(input,1600);
  assertEquals(4,parts.size());assertEquals(input,String.join("",parts));
 }
 @Test public void shortTextIsUnchanged(){assertEquals("Hola Matías.",SpeechTextChunks.split("Hola Matías.",1600).get(0));}
}
