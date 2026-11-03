package ar.goro.ai.gmail;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import com.google.android.gms.auth.api.identity.AuthorizationRequest;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Read-only Gmail API using the native Google Identity AuthorizationClient.
 * Requires an Android OAuth client registered for the GORO package and signing
 * certificate in a Google Cloud project with Gmail API and consent configured.
 * Tokens exist in memory only and are not committed, logged, or persisted.
 */
public final class GoroGmailGateway {
 public static final int REQUEST_AUTHORIZATION=723;
 public static final String GMAIL_SCOPE="https://www.googleapis.com/auth/gmail.readonly";
 public interface Callback {void complete(String result,String error);}
 private final Activity activity;
 private final Handler main=new Handler(Looper.getMainLooper());
 private final ExecutorService io=Executors.newSingleThreadExecutor();
 private boolean closed=false;
 private String accessToken;
 private String accountEmail;
 private final List<String> latestInboxIds=new ArrayList<>();
 private Callback pending;
 private String pendingQuery;
 public GoroGmailGateway(Activity activity){this.activity=activity;}
 public void readInbox(Callback callback){authorizeAndRead(callback,null);}
 public void searchInbox(String query,Callback callback){
  if(callback==null)throw new IllegalArgumentException("callback");
  if(query==null||query.trim().isEmpty()||query.length()>120){
   callback.complete(null,"La búsqueda Gmail requiere entre 1 y 120 caracteres");
   return;
  }
  authorizeAndRead(callback,query.trim());
 }
 private void authorizeAndRead(Callback callback,String query){
  if(callback==null)throw new IllegalArgumentException("callback");
  if(closed){callback.complete(null,"Gmail cerrado");return;}
  if(pending!=null){callback.complete(null,"Gmail tiene otra autorización en curso");return;}
  pending=callback;
  pendingQuery=query;
  AuthorizationRequest request=AuthorizationRequest.builder()
    .setRequestedScopes(Collections.singletonList(new Scope(GMAIL_SCOPE)))
    .build();
  Identity.getAuthorizationClient(activity).authorize(request)
   .addOnSuccessListener(result->{
    if(closed)return;
    if(result.hasResolution()){
     PendingIntent intent=result.getPendingIntent();
     if(intent==null){complete(null,"Google no devolvió una autorización");return;}
     try{activity.startIntentSenderForResult(intent.getIntentSender(),REQUEST_AUTHORIZATION,
       null,0,0,0);}
     catch(IntentSender.SendIntentException e){complete(null,"No se pudo abrir el consentimiento Google");}
    }else onAuthorized(result);
   })
   .addOnFailureListener(e->complete(null,"No se pudo iniciar OAuth Gmail: "+e.getClass().getSimpleName()));
 }
 public void onActivityResult(int requestCode,int resultCode,Intent intent){
  if(requestCode!=REQUEST_AUTHORIZATION)return;
  if(resultCode!=Activity.RESULT_OK||intent==null){complete(null,"Autorización Gmail cancelada");return;}
  try{onAuthorized(Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(intent));}
  catch(ApiException e){complete(null,"Gmail OAuth rechazado: "+e.getStatusCode());}
 }
 private void onAuthorized(AuthorizationResult result){
  if(result==null){complete(null,"No se recibió autorización Gmail");return;}
  if(!result.getGrantedScopes().contains(GMAIL_SCOPE)){
   complete(null,"No se concedió lectura de Gmail");return;
  }
  String token=result.getAccessToken();
  if(token==null||token.isEmpty()){complete(null,"No se recibió token de acceso Gmail");return;}
  accessToken=token;
  if(result.toGoogleSignInAccount()!=null)accountEmail=result.toGoogleSignInAccount().getEmail();
  io.execute(()->{
   try{
    String resultText=fetchInbox(token,pendingQuery);
    main.post(()->complete(resultText,null));
   }catch(Exception e){
    String error=e.getMessage()==null?"Error Gmail":e.getMessage();
    main.post(()->complete(null,error));
   }
  });
 }
 private String fetchInbox(String token,String query)throws Exception{
  String url="https://gmail.googleapis.com/gmail/v1/users/me/messages?maxResults=10"+
    (query==null?"&labelIds=INBOX":"&q="+URLEncoder.encode(query,"UTF-8"));
  JSONObject response=getJson(url,token);
  JSONArray messages=response.optJSONArray("messages");
  if(messages==null||messages.length()==0){
   synchronized(latestInboxIds){latestInboxIds.clear();}
   return query==null?"Gmail: no hay mensajes en la bandeja de entrada.":
    "Gmail: no se encontraron correos para la búsqueda.";
  }
  List<String> ids=new ArrayList<>();
  StringBuilder output=new StringBuilder(query==null?"Gmail · últimos mensajes":
    "Gmail · resultados de búsqueda");
  if(accountEmail!=null&&!accountEmail.isEmpty())output.append(" · ").append(accountEmail);
  for(int i=0;i<messages.length();i++){
   String id=messages.getJSONObject(i).optString("id","");
   if(id.isEmpty())continue;
   ids.add(id);
   JSONObject item=getJson("https://gmail.googleapis.com/gmail/v1/users/me/messages/"
     +URLEncoder.encode(id,"UTF-8")+"?format=metadata&metadataHeaders=From&metadataHeaders=Subject",token);
   JSONObject payload=item.optJSONObject("payload");
   JSONArray headers=payload==null?null:payload.optJSONArray("headers");
   String subject="Sin asunto",from="Remitente no disponible";
   if(headers!=null)for(int j=0;j<headers.length();j++){
    JSONObject h=headers.optJSONObject(j);
    if(h==null)continue;
    String key=h.optString("name","");
    if(key.equalsIgnoreCase("Subject"))subject=h.optString("value","Sin asunto");
    if(key.equalsIgnoreCase("From"))from=h.optString("value","Remitente no disponible");
   }
   output.append("\n").append(i+1).append(". ").append(limit(subject,120))
     .append("\n   ").append(limit(from,120));
  }
  synchronized(latestInboxIds){latestInboxIds.clear();latestInboxIds.addAll(ids);}
  return output.toString();
 }
 /** Reads one previously listed email. Explicit user request is required. */
 public void readMessage(int number,Callback callback){
  if(callback==null)throw new IllegalArgumentException("callback");
  if(closed){callback.complete(null,"Gmail cerrado");return;}
  if(accessToken==null){callback.complete(null,"Primero autorizá Gmail y consultá la bandeja");return;}
  String id;
  synchronized(latestInboxIds){
   if(number<1||number>latestInboxIds.size()){callback.complete(null,"Ese número de correo no está en la lista consultada");return;}
   id=latestInboxIds.get(number-1);
  }
  String token=accessToken;
  io.execute(()->{
   String text=null,error=null;
   try{
    JSONObject item=getJson("https://gmail.googleapis.com/gmail/v1/users/me/messages/"
      +URLEncoder.encode(id,"UTF-8")+"?format=full",token);
    JSONObject payload=item.optJSONObject("payload");
    JSONArray headers=payload==null?null:payload.optJSONArray("headers");
    String subject="Sin asunto",from="Remitente no disponible";
    if(headers!=null)for(int n=0;n<headers.length();n++){
     JSONObject header=headers.optJSONObject(n);
     if(header==null)continue;
     String name=header.optString("name","");
     if(name.equalsIgnoreCase("Subject"))subject=header.optString("value",subject);
     if(name.equalsIgnoreCase("From"))from=header.optString("value",from);
    }
    String body=extractPlainText(payload,0);
    if(body==null||body.isEmpty())body=item.optString("snippet","Mensaje sin vista previa de texto");
    text="Correo "+number+" · "+limit(subject,150)+"\nDe: "+limit(from,150)
      +"\n\n"+limit(body,3200);
   }catch(Exception e){error=e.getMessage()==null?"No se pudo leer el correo":e.getMessage();}
   final String message=text,problem=error;
   main.post(()->{if(!closed)callback.complete(message,problem);});
  });
 }
 private static String extractPlainText(JSONObject payload,int depth){
  if(payload==null||depth>8)return null;
  String type=payload.optString("mimeType","");
  JSONObject body=payload.optJSONObject("body");
  String encoded=body==null?null:body.optString("data",null);
  if(type.toLowerCase(java.util.Locale.ROOT).startsWith("text/plain")&&encoded!=null&&!encoded.isEmpty()){
   try{return new String(Base64.decode(encoded,Base64.URL_SAFE|Base64.NO_WRAP),StandardCharsets.UTF_8);}
   catch(IllegalArgumentException ignored){}
  }
  JSONArray parts=payload.optJSONArray("parts");
  if(parts!=null)for(int n=0;n<parts.length();n++){
   String found=extractPlainText(parts.optJSONObject(n),depth+1);
   if(found!=null&&!found.isEmpty())return found;
  }
  return null;
 }
 private static JSONObject getJson(String url,String token)throws Exception{
  HttpURLConnection connection=(HttpURLConnection)new URL(url).openConnection();
  connection.setRequestMethod("GET");connection.setConnectTimeout(14000);
  connection.setReadTimeout(20000);
  connection.setRequestProperty("Authorization","Bearer "+token);
  connection.setRequestProperty("Accept","application/json");
  try{
   int status=connection.getResponseCode();
   InputStream source=status>=200&&status<300?connection.getInputStream():connection.getErrorStream();
   if(source==null)throw new IllegalStateException("Gmail HTTP "+status);
   byte[] data=readLimited(source,512*1024);
   if(status<200||status>=300)
    throw new IllegalStateException(status==401?"Token Gmail vencido; volver a autorizar":
       status==403?"Permiso Gmail rechazado, o API no habilitada": "Gmail HTTP "+status);
   return new JSONObject(new String(data,StandardCharsets.UTF_8));
  }finally{connection.disconnect();}
 }
 private static byte[] readLimited(InputStream input,int max)throws Exception{
  try(InputStream in=input;ByteArrayOutputStream output=new ByteArrayOutputStream()){
   byte[] bytes=new byte[4096];int n,total=0;
   while((n=in.read(bytes))!=-1){total+=n;if(total>max)throw new IllegalStateException("Respuesta Gmail demasiado grande");output.write(bytes,0,n);}
   return output.toByteArray();
  }
 }
 private static String limit(String text,int max){return text.length()<=max?text:text.substring(0,max);}
 private void complete(String text,String error){
  if(closed)return;
  Callback cb=pending;pending=null;pendingQuery=null;
  if(cb!=null)cb.complete(text,error);
 }
 public void close(){closed=true;accessToken=null;pending=null;pendingQuery=null;io.shutdownNow();}
}