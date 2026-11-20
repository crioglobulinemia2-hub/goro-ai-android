package ar.goro.ai.gmail;

import android.text.Html;
import android.util.Base64;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

/** Reads the actual MIME body, without treating downloadable files as the email body. */
final class GmailMessageText {
 interface BodyLoader { JSONObject load(String attachmentId)throws Exception; }
 private static final int MAX_DEPTH=20;
 private static final int MAX_TEXT_BYTES=1024*1024;
 private static final Pattern CHARSET=Pattern.compile("(?i)(?:^|;)\\s*charset\\s*=\\s*[\"']?([^;\\s\"']+)");
 private static final Pattern RELATED_START=Pattern.compile("(?i)(?:^|;)\\s*start\\s*=\\s*(?:\"([^\"]+)\"|'([^']+)'|([^;\\s]+))");
 private static final Pattern HIDDEN_HTML=Pattern.compile("(?is)<(script|style|head)\\b[^>]*>.*?</\\1\\s*>|<!--.*?-->");
 private int decodedBytes;
 private final BodyLoader loader;
 private GmailMessageText(BodyLoader loader){this.loader=loader;}
 static String extract(JSONObject payload,BodyLoader loader)throws Exception{
  return new GmailMessageText(loader).read(payload,0);
 }
 private String read(JSONObject part,int depth)throws Exception{
  if(part==null||isAttachment(part))return null;
  if(depth>MAX_DEPTH)throw new IllegalStateException("El correo tiene una estructura demasiado compleja para leerlo completo.");
  String type=part.optString("mimeType","").toLowerCase(Locale.ROOT).split(";",2)[0].trim();
  if(type.equals("text/plain")||type.equals("text/html")){
   String text=decode(part);
   if(text==null||text.trim().isEmpty())return null;
   if(type.equals("text/html")){
    // Html.fromHtml produces text only; no WebView, scripts, images or links are loaded.
    String visible=HIDDEN_HTML.matcher(text).replaceAll("")
      .replaceAll("(?i)</(?:td|th)\\s*>"," ")
      .replaceAll("(?i)</tr\\s*>","<br>");
    text=Html.fromHtml(visible,Html.FROM_HTML_MODE_LEGACY).toString()
      .replace('\u00a0',' ').replaceAll("\\n[ \\t]+","\n").replaceAll("\\n{3,}","\n\n");
   }
   return text.trim();
  }
  if(!type.startsWith("multipart/"))return null;
  JSONArray parts=part.optJSONArray("parts");
  if(parts==null)return null;
  if(type.equals("multipart/related"))return read(relatedRoot(part,parts),depth+1);
  if(type.equals("multipart/alternative")){
   // Plain and HTML alternatives represent the same message. Prefer plain without
   // downloading or speaking both representations.
   for(int pass=0;pass<2;pass++)for(int i=0;i<parts.length();i++){
    JSONObject child=parts.optJSONObject(i);
    if(containsPlainBody(child,depth+1)!=(pass==0))continue;
    String text=read(child,depth+1);
    if(text!=null&&!text.isEmpty())return text;
   }
   return null;
  }
  StringBuilder text=new StringBuilder();
  for(int i=0;i<parts.length();i++){
   String next=read(parts.optJSONObject(i),depth+1);
   if(next==null||next.isEmpty())continue;
   if(text.length()>0)text.append("\n\n");
   text.append(next);
  }
  return text.length()==0?null:text.toString();
 }
 private String decode(JSONObject part)throws Exception{
  JSONObject body=part.optJSONObject("body");
  if(body==null)return null;
  if(body.optLong("size",0)>MAX_TEXT_BYTES-decodedBytes)throw tooLarge();
  String data=body.optString("data","");
  if(data.isEmpty()&&!body.optString("attachmentId","").isEmpty()){
   if(loader==null)throw new IllegalStateException("No se pudo recuperar el cuerpo completo del correo.");
   body=loader.load(body.getString("attachmentId"));
   if(body==null)throw new IllegalStateException("Gmail no devolvió el cuerpo del correo.");
   if(body.optLong("size",0)>MAX_TEXT_BYTES-decodedBytes)throw tooLarge();
   data=body.optString("data","");
  }
  if(data.isEmpty())return null;
  // Bound the base64 allocation too, even if a malformed response omitted size.
  if(data.length()>((MAX_TEXT_BYTES-decodedBytes+2L)/3L)*4L+8L)throw tooLarge();
  byte[] bytes;
  try{bytes=Base64.decode(data,Base64.URL_SAFE|Base64.NO_WRAP);}
  catch(IllegalArgumentException e){throw new IllegalStateException("Gmail devolvió un cuerpo de correo ilegible.");}
  decodedBytes+=bytes.length;if(decodedBytes>MAX_TEXT_BYTES)throw tooLarge();
  Charset charset=StandardCharsets.UTF_8;
  Matcher match=CHARSET.matcher(header(part,"Content-Type"));
  if(match.find())try{charset=Charset.forName(match.group(1));}
   catch(IllegalArgumentException e){throw new IllegalStateException("No se reconoce la codificación del correo.");}
  return new String(bytes,charset);
 }
 private static boolean containsPlainBody(JSONObject part,int depth){
  if(part==null||depth>MAX_DEPTH||isAttachment(part))return false;
  String type=part.optString("mimeType","").toLowerCase(Locale.ROOT);
  if(type.startsWith("text/plain"))return true;
  if(!type.startsWith("multipart/"))return false;
  JSONArray parts=part.optJSONArray("parts");
  if(type.equals("multipart/related"))return parts!=null&&containsPlainBody(relatedRoot(part,parts),depth+1);
  if(parts!=null)for(int i=0;i<parts.length();i++)if(containsPlainBody(parts.optJSONObject(i),depth+1))return true;
  return false;
 }
 private static JSONObject relatedRoot(JSONObject part,JSONArray parts){
  // Related siblings are images/styles/resources for one root body, not extra paragraphs.
  Matcher start=RELATED_START.matcher(header(part,"Content-Type"));
  if(start.find()){
   String id=start.group(1)!=null?start.group(1):start.group(2)!=null?start.group(2):start.group(3);
   for(int i=0;i<parts.length();i++){
    JSONObject child=parts.optJSONObject(i);
    if(child!=null&&id.equals(header(child,"Content-ID").trim()))return child;
   }
   return null;
  }
  return parts.optJSONObject(0);
 }
 private static boolean isAttachment(JSONObject part){
  return !part.optString("filename","").isEmpty()
    ||header(part,"Content-Disposition").trim().toLowerCase(Locale.ROOT).startsWith("attachment");
 }
 private static String header(JSONObject part,String name){
  JSONArray headers=part.optJSONArray("headers");
  if(headers!=null)for(int i=0;i<headers.length();i++){
   JSONObject header=headers.optJSONObject(i);
   if(header!=null&&name.equalsIgnoreCase(header.optString("name","")))return header.optString("value","");
  }
  return "";
 }
 private static IllegalStateException tooLarge(){
  return new IllegalStateException("El cuerpo del correo supera 1 MB. Abrilo en Gmail para leerlo completo.");
 }
}
