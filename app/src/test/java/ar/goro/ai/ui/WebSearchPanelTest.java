package ar.goro.ai.ui;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
public class WebSearchPanelTest {
 private JSONObject item(String url,String snippet)throws Exception{return new JSONObject().put("url",url).put("title","Fuente").put("snippet",snippet);}
 @Test public void returnedSnippetAndClickableSourceArePreserved()throws Exception{
  String[] r=WebSearchPanel.parse(JSONObject.quote(new JSONArray().put(item("https://developer.android.com/guide","Texto real recuperado")).toString()));
  assertNotNull(r);assertTrue(r[0].contains("https://developer.android.com/guide"));assertTrue(r[1].contains("Texto real recuperado"));assertTrue(r[1].startsWith("Te leo los fragmentos"));
 }
 @Test public void emptyChallengeOrInvalidRepliesNeverProduceSpokenFacts(){assertNull(WebSearchPanel.parse("null"));assertNull(WebSearchPanel.parse(JSONObject.quote("[]")));assertNull(WebSearchPanel.parse("captcha"));}
 @Test public void unsafeInternalAndDuplicateLinksAreExcluded()throws Exception{
  JSONArray items=new JSONArray().put(item("javascript:alert(1)","No leer"))
   .put(item("https://www.google.com/search?q=x","No leer"))
   .put(item("https://example.org/source","Texto recuperado"))
   .put(item("https://example.org/source","Duplicado"));
  String[] r=WebSearchPanel.parse(JSONObject.quote(items.toString()));assertNotNull(r);assertFalse(r[0].contains("No leer"));assertFalse(r[0].contains("Duplicado"));
 }
}
