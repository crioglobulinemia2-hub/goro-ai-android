package ar.goro.ai.gmail;

import android.app.Activity;
import android.accounts.Account;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import ar.goro.ai.core.HttpRequestScope;
import com.google.android.gms.auth.api.identity.AuthorizationRequest;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.Task;
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
 interface AuthorizationStarter {Task<AuthorizationResult> authorize(AuthorizationRequest request);}
 private final Activity activity;
 private final AuthorizationStarter authorization;
 private final Handler main=new Handler(Looper.getMainLooper());
 private final HttpRequestScope requests=new HttpRequestScope();
 private volatile boolean closed=false;
 private final GmailAccountState session=new GmailAccountState();
 private long pendingEpoch=-1;
 private long resolutionEpoch=-1;
 private Callback pending;
 private String pendingQuery;
 private String pendingMessageId;
 private int pendingMessageNumber;
 public GoroGmailGateway(Activity activity){
  this(activity,request->Identity.getAuthorizationClient(activity).authorize(request));
 }
 GoroGmailGateway(Activity activity,AuthorizationStarter authorization){
  this.activity=activity;this.authorization=authorization;
 }
 public void setSelectedAccount(String email){
  long previous=session.generation();
  session.select(email);
  if(previous!=session.generation()){
   requests.cancel();pending=null;pendingQuery=null;pendingMessageId=null;pendingEpoch=-1;
  }
 }
 private boolean current(long epoch){return !closed&&!activity.isFinishing()&&!activity.isDestroyed()&&session.current(epoch);}
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
  authorizeRead(callback,query,null,0);
 }
 private void authorizeRead(Callback callback,String query,String messageId,int messageNumber){
  if(callback==null)throw new IllegalArgumentException("callback");
  if(closed||activity.isFinishing()||activity.isDestroyed()){callback.complete(null,"Gmail cerrado");return;}
  String email=session.account();
  if(email==null){callback.complete(null,"Elegí tu cuenta Google en Ajustes para vincular Calendar y Gmail.");return;}
  if(pending!=null||resolutionEpoch!=-1){callback.complete(null,"Completá o cerrá la autorización Google en curso.");return;}
  final long epoch=messageId==null?session.begin():session.beginMessageRead();
  requests.cancel();
  pending=callback;pendingQuery=query;pendingMessageId=messageId;pendingMessageNumber=messageNumber;pendingEpoch=epoch;
  AuthorizationRequest request=AuthorizationRequest.builder()
    .setAccount(new Account(email,"com.google"))
    .setRequestedScopes(Collections.singletonList(new Scope(GMAIL_SCOPE)))
    .build();
  authorization.authorize(request)
   .addOnSuccessListener(result->{
    if(!current(epoch))return;
    if(result==null){complete(epoch,null,"No se recibió autorización Gmail");return;}
    if(result.hasResolution()){
     PendingIntent intent=result.getPendingIntent();
     if(intent==null){complete(epoch,null,"Google no devolvió una autorización");return;}
     resolutionEpoch=epoch;
     try{activity.startIntentSenderForResult(intent.getIntentSender(),REQUEST_AUTHORIZATION,null,0,0,0);}
     catch(IntentSender.SendIntentException e){resolutionEpoch=-1;complete(epoch,null,"No se pudo abrir el consentimiento Google");}
    }else onAuthorized(epoch,result);
   })
   .addOnFailureListener(e->complete(epoch,null,"No se pudo iniciar OAuth Gmail: "+e.getClass().getSimpleName()));
 }
 public void onActivityResult(int requestCode,int resultCode,Intent intent){
  if(requestCode!=REQUEST_AUTHORIZATION||resolutionEpoch==-1)return;
  long epoch=resolutionEpoch;resolutionEpoch=-1;
  if(!current(epoch))return;
  if(resultCode!=Activity.RESULT_OK||intent==null){complete(epoch,null,"Autorización Gmail cancelada");return;}
  try{onAuthorized(epoch,Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(intent));}
  catch(ApiException e){complete(epoch,null,"Gmail OAuth rechazado: "+e.getStatusCode());}
 }
 private void onAuthorized(long epoch,AuthorizationResult result){
  if(!current(epoch))return;
  if(result==null){complete(epoch,null,"No se recibió autorización Gmail");return;}
  if(!result.getGrantedScopes().contains(GMAIL_SCOPE)){
   complete(epoch,null,"No se concedió lectura de Gmail");return;
  }
  String token=result.getAccessToken();
  if(token==null||token.isEmpty()){complete(epoch,null,"No se recibió token de acceso Gmail");return;}
  final String query=pendingQuery;
  final String messageId=pendingMessageId;
  final int messageNumber=pendingMessageNumber;
  final String expectedAccount=session.account();
  requests.submit(ticket->{
   try{
    // Gmail's profile verifies the actual owner even when the identity result omits email.
    String verified=getJson("https://gmail.googleapis.com/gmail/v1/users/me/profile",token,ticket).optString("emailAddress","");
    if(!ticket.current()||!current(epoch))return;
    if(!session.authorize(epoch,verified,token)){
     main.post(()->{if(ticket.current())complete(epoch,null,"Google autorizó otra cuenta. Elegí la misma cuenta que Calendar.");});return;
    }
    if(messageId!=null){
     String text=fetchMessage(token,messageId,messageNumber,ticket);
     main.post(()->{if(ticket.current())complete(epoch,text,null);});
    }else{
     InboxResult inbox=fetchInbox(token,query,expectedAccount,epoch,ticket);
     main.post(()->{
      if(ticket.current()&&current(epoch)&&session.publish(epoch,inbox.ids))complete(epoch,inbox.text,null);
     });
    }
   }catch(Exception e){
    String error=e.getMessage()==null?"Error Gmail":e.getMessage();
    main.post(()->{if(ticket.current())complete(epoch,null,error);});
   }
  });
 }
 private static final class InboxResult {
  final String text;final List<String> ids;
  InboxResult(String text,List<String> ids){this.text=text;this.ids=ids;}
 }
 private InboxResult fetchInbox(String token,String query,String accountEmail,long epoch,HttpRequestScope.Ticket ticket)throws Exception{
  String url="https://gmail.googleapis.com/gmail/v1/users/me/messages?maxResults=10"+
    (query==null?"&labelIds=INBOX":"&q="+URLEncoder.encode(query,"UTF-8"));
  JSONObject response=getJson(url,token,ticket);
  JSONArray messages=response.optJSONArray("messages");
  if(messages==null||messages.length()==0){
   return new InboxResult(query==null?"Gmail: no hay mensajes en la bandeja de entrada.":
    "Gmail: no se encontraron correos para la búsqueda.",Collections.emptyList());
  }
  List<String> ids=new ArrayList<>();
  StringBuilder output=new StringBuilder(query==null?"Gmail · últimos mensajes":
    "Gmail · resultados de búsqueda");
  if(accountEmail!=null&&!accountEmail.isEmpty())output.append(" · ").append(accountEmail);
  for(int i=0;i<messages.length();i++){
   if(!session.current(epoch))throw new java.io.InterruptedIOException("Cuenta Google modificada");
   String id=messages.getJSONObject(i).optString("id","");
   if(id.isEmpty())continue;
   ids.add(id);
   JSONObject item=getJson("https://gmail.googleapis.com/gmail/v1/users/me/messages/"
     +URLEncoder.encode(id,"UTF-8")+"?format=metadata&metadataHeaders=From&metadataHeaders=Subject",token,ticket);
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
   output.append("\n").append(ids.size()).append(". ").append(limit(subject,120))
     .append("\n   ").append(limit(from,120));
  }
  return new InboxResult(output.toString(),ids);
 }
 /** Reads one previously listed email. Explicit user request is required. */
 public void readMessage(int number,Callback callback){
  if(callback==null)throw new IllegalArgumentException("callback");
  if(closed){callback.complete(null,"Gmail cerrado");return;}
  // Resolve the displayed message once. Authorization must never relist and change this ID.
  String id=session.messageId(number);
  if(id==null){callback.complete(null,"Ese número de correo no está en la lista consultada");return;}
  authorizeRead(callback,null,id,number);
 }
 private String fetchMessage(String token,String id,int number,HttpRequestScope.Ticket ticket)throws Exception{
  JSONObject item=getJson("https://gmail.googleapis.com/gmail/v1/users/me/messages/"
    +URLEncoder.encode(id,"UTF-8")+"?format=full",token,ticket);
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
  return "Correo "+number+" · "+limit(subject,150)+"\nDe: "+limit(from,150)
    +"\n\n"+limit(body,3200);
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
 private static JSONObject getJson(String url,String token,HttpRequestScope.Ticket ticket)throws Exception{
  if(!ticket.current())throw new java.util.concurrent.CancellationException();
  HttpURLConnection connection=(HttpURLConnection)new URL(url).openConnection();
  ticket.attach(connection);
  try{
   connection.setRequestMethod("GET");connection.setConnectTimeout(14000);
   connection.setReadTimeout(20000);
   connection.setRequestProperty("Authorization","Bearer "+token);
   connection.setRequestProperty("Accept","application/json");
   int status=connection.getResponseCode();
   InputStream source=status>=200&&status<300?connection.getInputStream():connection.getErrorStream();
   if(source==null)throw new IllegalStateException("Gmail HTTP "+status);
   byte[] data=readLimited(source,512*1024);
   if(status<200||status>=300)
    throw new IllegalStateException(status==401?"Token Gmail vencido; volver a autorizar":
       status==403?"Permiso Gmail rechazado, o API no habilitada": "Gmail HTTP "+status);
   return new JSONObject(new String(data,StandardCharsets.UTF_8));
  }finally{ticket.detach(connection);connection.disconnect();}
 }
 private static byte[] readLimited(InputStream input,int max)throws Exception{
  try(InputStream in=input;ByteArrayOutputStream output=new ByteArrayOutputStream()){
   byte[] bytes=new byte[4096];int n,total=0;
   while((n=in.read(bytes))!=-1){total+=n;if(total>max)throw new IllegalStateException("Respuesta Gmail demasiado grande");output.write(bytes,0,n);}
   return output.toByteArray();
  }
 }
 private static String limit(String text,int max){return text.length()<=max?text:text.substring(0,max);}
 private void complete(long epoch,String text,String error){
  if(!current(epoch)||pendingEpoch!=epoch)return;
  Callback cb=pending;pending=null;pendingQuery=null;pendingMessageId=null;pendingEpoch=-1;
  if(error!=null)session.clearAuthorization();
  if(cb!=null)cb.complete(text,error);
 }
 public void close(){
  closed=true;session.close();requests.close();pending=null;pendingQuery=null;pendingMessageId=null;pendingEpoch=-1;
  main.removeCallbacksAndMessages(null);
 }
}
