package ar.goro.ai.gmail;

import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class GmailMessageTextTest {
 private static JSONObject body(String type,String text)throws Exception{
  return new JSONObject().put("mimeType",type).put("body",encoded(text.getBytes(StandardCharsets.UTF_8)));
 }
 private static JSONObject encoded(byte[] data)throws Exception{
  return new JSONObject().put("size",data.length)
    .put("data",Base64.encodeToString(data,Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING));
 }
 private static JSONObject multipart(String type,JSONObject... children)throws Exception{
  JSONArray parts=new JSONArray();for(JSONObject child:children)parts.put(child);
  return new JSONObject().put("mimeType",type).put("parts",parts);
 }
 private static JSONObject header(String name,String value)throws Exception{
  return new JSONObject().put("name",name).put("value",value);
 }
 @Test public void longMessageIncludesItsEndingWithoutTheOld3200CharacterCut()throws Exception{
  String message="Inicio "+String.join("",java.util.Collections.nCopies(5000,"x"))+" FIN DEL CORREO";
  assertEquals(message,GmailMessageText.extract(body("text/plain",message),null));
 }
 @Test public void htmlOnlyMessageBecomesCompleteReadableText()throws Exception{
  String html="<html><head><style>.secret {color:red}</style></head><body>"
    +"<p>Hola, Mat&iacute;as.</p><script>invisible()</script>"
    +"<p>La reuni&oacute;n es a las <b>15:30</b>.</p></body></html>";
  String text=GmailMessageText.extract(body("text/html",html),null);
  assertTrue(text.contains("Hola, Matías."));assertTrue(text.contains("La reunión es a las 15:30."));
  assertFalse(text.contains("invisible"));assertFalse(text.contains("secret"));assertFalse(text.contains("<"));
 }
 @Test public void plainAlternativeWinsWithoutFetchingItsHtmlEquivalent()throws Exception{
  JSONObject external=new JSONObject().put("mimeType","text/html")
    .put("body",new JSONObject().put("attachmentId","html-body"));
  AtomicInteger loads=new AtomicInteger();
  String text=GmailMessageText.extract(multipart("multipart/alternative",external,body("text/plain","Texto completo")),
    id->{loads.incrementAndGet();throw new AssertionError("Unneeded alternative download");});
  assertEquals("Texto completo",text);assertEquals(0,loads.get());
 }
 @Test public void htmlAlternativeIsUsedWhenPlainIsEmpty()throws Exception{
  assertEquals("Texto completo",GmailMessageText.extract(multipart("multipart/alternative",
    body("text/plain"," "),body("text/html","<p>Texto completo</p>")),null));
 }
 @Test public void multipartBodyKeepsAllSectionsAndSkipsAttachedTextFiles()throws Exception{
  JSONObject attached=body("text/plain","NO LEER ARCHIVO").put("filename","datos.txt");
  JSONObject disposition=body("text/plain","NO LEER OTRO ARCHIVO").put("headers",
    new JSONArray().put(header("Content-Disposition","attachment; filename=datos.txt")));
  String text=GmailMessageText.extract(multipart("multipart/mixed",attached,body("text/plain","Primera parte"),
    body("text/plain","Segunda parte"),disposition),null);
  assertEquals("Primera parte\n\nSegunda parte",text);
 }
 @Test public void separateBodyDataIsFetchedWithoutRelistingOrChangingMessage()throws Exception{
  JSONObject external=new JSONObject().put("mimeType","text/plain")
    .put("body",new JSONObject().put("attachmentId","body-id").put("size",14));
  AtomicInteger calls=new AtomicInteger();
  String text=GmailMessageText.extract(external,id->{
   assertEquals("body-id",id);calls.incrementAndGet();return encoded("Cuerpo completo".getBytes(StandardCharsets.UTF_8));
  });
  assertEquals("Cuerpo completo",text);assertEquals(1,calls.get());
 }
 @Test public void latin1BodyKeepsSpanishAccents()throws Exception{
  JSONObject part=new JSONObject().put("mimeType","text/plain")
    .put("headers",new JSONArray().put(header("Content-Type","text/plain; charset=\"ISO-8859-1\"")))
    .put("body",encoded("Reunión mañana".getBytes(StandardCharsets.ISO_8859_1)));
  assertEquals("Reunión mañana",GmailMessageText.extract(part,null));
 }
 @Test public void oversizedBodyFailsExplicitlyInsteadOfReturningPartialText()throws Exception{
  JSONObject part=body("text/plain","Primeros caracteres")
    .put("body",new JSONObject().put("attachmentId","huge-body").put("size",2*1024*1024));
  try{
   GmailMessageText.extract(part,id->{throw new AssertionError("Do not download oversized body");});
   fail("Must report incomplete body");
  }catch(IllegalStateException e){assertTrue(e.getMessage().contains("leerlo completo"));}
 }
 @Test public void fileOnlyMessageDoesNotReadTheAttachmentAsBody()throws Exception{
  JSONObject file=body("text/plain","Contenido del archivo").put("filename","adjunto.txt");
  assertNull(GmailMessageText.extract(multipart("multipart/mixed",file),null));
 }
 @Test public void relatedMimeUsesNamedRootAndDoesNotReadInlineResources()throws Exception{
  JSONObject resource=body("text/plain","CSS QUE NO DEBE LEERSE").put("headers",
    new JSONArray().put(header("Content-ID","<resource>")));
  JSONObject content=body("text/html","<p>Texto del correo</p>").put("headers",
    new JSONArray().put(header("Content-ID","<main>")));
  JSONObject related=multipart("multipart/related",resource,content).put("headers",
    new JSONArray().put(header("Content-Type","multipart/related; start=\"<main>\"")));
  assertEquals("Texto del correo",GmailMessageText.extract(related,null));
 }
 @Test public void plainAlternativeWinsOverHtmlInsideRelatedWithoutReadingResources()throws Exception{
  JSONObject resource=new JSONObject().put("mimeType","text/plain")
    .put("body",new JSONObject().put("attachmentId","inline-resource"));
  JSONObject related=multipart("multipart/related",body("text/html","<p>Versión HTML</p>"),resource);
  String text=GmailMessageText.extract(multipart("multipart/alternative",related,body("text/plain","Versión texto")),
    id->{throw new AssertionError("Inline resources are not the message body");});
  assertEquals("Versión texto",text);
 }
 @Test public void malformedBodyDoesNotMasqueradeAsCompleteContent()throws Exception{
  JSONObject part=new JSONObject().put("mimeType","text/plain")
    .put("body",new JSONObject().put("data","a"));
  try{GmailMessageText.extract(part,null);fail("Malformed base64 must be reported");}
  catch(IllegalStateException e){assertTrue(e.getMessage().contains("ilegible"));}
 }
}
