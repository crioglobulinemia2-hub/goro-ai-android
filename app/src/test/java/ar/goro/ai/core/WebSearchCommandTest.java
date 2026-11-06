package ar.goro.ai.core;
import android.content.Intent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
public class WebSearchCommandTest {
 @Test public void spokenInternetCommandsPreserveTheActualQuery(){
  assertEquals("hospitales en Vicente López",WebSearchCommand.query("Goro, buscá por internet hospitales en Vicente López"));
  assertEquals("Android & Java",WebSearchCommand.query("busca Android & Java en internet"));
  assertEquals("clima",WebSearchCommand.query("buscar en la web clima"));
 }
 @Test public void mailAndMemorySearchesAreNotSentToWeb(){assertNull(WebSearchCommand.query("buscá en gmail turnos"));assertNull(WebSearchCommand.query("buscá en recuerdos Matías"));assertNull(WebSearchCommand.query(null));}
 @Test public void queryCharactersCannotChangeSearchDestination(){
  Intent i=WebSearchCommand.intent("a&b # c? ñ");assertEquals("https",i.getData().getScheme());assertEquals("www.google.com",i.getData().getHost());assertEquals("/search",i.getData().getPath());assertEquals("a&b # c? ñ",i.getData().getQueryParameter("q"));
 }
}
