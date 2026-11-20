package ar.goro.ai.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class NvidiaStreamParserTest {
 @Test public void readsContentOnly(){
  NvidiaStreamParser stream=new NvidiaStreamParser();
  String event="data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"internal analysis\",\"content\":\"Hola\"}}]}";
  assertEquals("Hola",stream.accept(event));
  assertTrue(stream.hasAnswer());
  assertFalse(stream.isDone());
 }
 @Test public void hidesReasoningOnly(){
  NvidiaStreamParser stream=new NvidiaStreamParser();
  assertEquals("",stream.accept("data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"hidden\"}}]}"));
  assertFalse(stream.hasAnswer());
 }
 @Test public void concatenatesPartialAnswerAndFinishes(){
  NvidiaStreamParser stream=new NvidiaStreamParser();
  assertEquals("GORO",stream.accept("data: {\"choices\":[{\"delta\":{\"content\":\"GORO\"}}]}"));
  assertEquals(" conectado",stream.accept("data: {\"choices\":[{\"delta\":{\"content\":\" conectado\"}}]}"));
  assertEquals("",stream.accept("data: [DONE]"));
  assertTrue(stream.isDone());
  assertEquals("",stream.accept("data: {\"choices\":[{\"delta\":{\"content\":\"ignored\"}}]}"));
 }
 @Test public void handlesEmptyChoicesAndNullDelta(){
  NvidiaStreamParser stream=new NvidiaStreamParser();
  assertEquals("",stream.accept("data: {\"choices\":[]}"));
  assertEquals("",stream.accept("data: {\"choices\":[{\"delta\":null}]}"));
 }
 @Test public void refusesServerError(){
  try{
   new NvidiaStreamParser().accept("data: {\"error\":{\"message\":\"Rate limited\"}}");
   fail("server error must be propagated");
  }catch(IllegalStateException expected){assertTrue(expected.getMessage().contains("Rate limited"));}
 }
 @Test public void rejectsMalformedJson(){
  try{new NvidiaStreamParser().accept("data: {this is not json");fail("invalid json should fail");}
  catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains("invalid"));}
 }
}
