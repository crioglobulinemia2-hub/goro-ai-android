package ar.goro.ai.auth;
import android.app.Activity;
import android.content.Intent;
import android.app.PendingIntent;
import android.net.Uri;
import net.openid.appauth.AuthorizationRequest;
import net.openid.appauth.AuthorizationService;
import net.openid.appauth.AuthorizationServiceConfiguration;
import net.openid.appauth.AuthorizationException;
import net.openid.appauth.AuthorizationResponse;
import net.openid.appauth.TokenRequest;
import net.openid.appauth.TokenResponse;
import net.openid.appauth.ResponseTypeValues;
import net.openid.appauth.CodeVerifierUtil;
import java.util.concurrent.TimeUnit;
/** Public-client OAuth authorization-code + PKCE for Google Calendar. */
public final class GoogleOAuthManager {
 public static String redirectUri(String clientId){
  if(clientId==null||!clientId.endsWith(".apps.googleusercontent.com"))throw new IllegalArgumentException("Invalid Google OAuth client ID");
  String reverse="com.googleusercontent.apps."+clientId.substring(0,clientId.indexOf(".apps.googleusercontent.com"));
  return reverse+":/oauth2redirect";
 }
 public static final String SCOPE="https://www.googleapis.com/auth/calendar.events";
 public interface Callback{void complete(boolean success,String error);}
 private final AuthorizationService service;private final GoogleTokenStore store;private final String clientId;
 public GoogleOAuthManager(Activity activity,GoogleTokenStore store,String clientId){this.service=new AuthorizationService(activity);this.store=store;this.clientId=clientId;}
 public void performAuthorizationRequest(Activity activity,PendingIntent completed,PendingIntent cancelled){
  if(clientId==null||clientId.trim().isEmpty())throw new IllegalStateException("Google OAuth Android client ID is not configured");
  AuthorizationServiceConfiguration config=new AuthorizationServiceConfiguration(Uri.parse("https://accounts.google.com/o/oauth2/v2/auth"),Uri.parse("https://oauth2.googleapis.com/token"));
  String verifier=CodeVerifierUtil.generateRandomCodeVerifier();
  java.util.Map<String,String> parameters=new java.util.HashMap<>();parameters.put("access_type","offline");parameters.put("prompt","consent");
  AuthorizationRequest request=new AuthorizationRequest.Builder(config,clientId,ResponseTypeValues.CODE,Uri.parse(redirectUri(clientId))).setScope("openid email "+SCOPE).setCodeVerifier(verifier,CodeVerifierUtil.deriveCodeVerifierChallenge(verifier),CodeVerifierUtil.getCodeVerifierChallengeMethod()).setAdditionalParameters(parameters).build();
  service.performAuthorizationRequest(activity,request,completed,cancelled);
 }
 public void handleAuthorizationResult(Intent data,Callback callback){
  AuthorizationResponse response=data==null?null:AuthorizationResponse.fromIntent(data);
  AuthorizationException error=data==null?null:AuthorizationException.fromIntent(data);
  if(response==null){callback.complete(false,error==null?"Google authorization cancelled.":error.error);return;}
  service.performTokenRequest(response.createTokenExchangeRequest(),(token,ex)->{
   if(token==null){callback.complete(false,ex==null?"Token exchange failed.":ex.error);return;}
   long expiry=token.accessTokenExpirationTime==null?System.currentTimeMillis()+TimeUnit.HOURS.toMillis(1):token.accessTokenExpirationTime;
   store.save(token.accessToken,token.refreshToken,expiry);callback.complete(true,null);
  });
 }
 public void close(){service.dispose();}
}
