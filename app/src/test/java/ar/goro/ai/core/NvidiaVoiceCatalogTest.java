package ar.goro.ai.core;
import org.junit.Test;
import static org.junit.Assert.*;
public class NvidiaVoiceCatalogTest {
 private static final String VOICE="Magpie-Multilingual.ES-US.Diego";
 @Test public void acceptsOnlyAnExactAdvertisedVoice(){
  assertTrue(NvidiaVoiceCatalog.contains("{\"es-US\":{\"voices\":[\""+VOICE+"\"]}}",VOICE));
  assertFalse(NvidiaVoiceCatalog.contains("{\"es-US\":{\"voices\":[\""+VOICE+".Angry\"]}}",VOICE));
  assertFalse(NvidiaVoiceCatalog.contains("{\"error\":\""+VOICE+" not found\"}",VOICE));
 }
}
