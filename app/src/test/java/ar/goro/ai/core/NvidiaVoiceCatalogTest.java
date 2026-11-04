package ar.goro.ai.core;
import org.junit.Test;
import static org.junit.Assert.*;

public class NvidiaVoiceCatalogTest {
 private static final String VOICE="Magpie-Multilingual.ES-US.Diego";
 @Test public void acceptsDocumentedMultilingualCatalogue(){
  assertTrue(NvidiaVoiceCatalog.contains("{\"en-US,es-US,fr-FR\":{\"voices\":[\""+VOICE+"\"]}}","es-US",VOICE));
 }
 @Test public void rejectsSubstringAndErrorMentions(){
  assertFalse(NvidiaVoiceCatalog.contains("{\"error\":\"Voice "+VOICE+" unavailable\"}","es-US",VOICE));
  assertFalse(NvidiaVoiceCatalog.contains("{\"es-US\":{\"voices\":[\""+VOICE+".Clone\"]}}","es-US",VOICE));
 }
 @Test public void requiresLanguageAndVoiceArray(){
  assertFalse(NvidiaVoiceCatalog.contains("{\"en-US\":{\"voices\":[\""+VOICE+"\"]}}","es-US",VOICE));
  assertFalse(NvidiaVoiceCatalog.contains("{\"es-US\":{\"voices\":\""+VOICE+"\"}}","es-US",VOICE));
 }
}
