package ar.goro.ai.auth;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import net.openid.appauth.AuthorizationRequest;
import net.openid.appauth.AuthorizationService;
import net.openid.appauth.AuthorizationServiceConfiguration;
import net.openid.appauth.AuthorizationException;
import net.openid.appauth.AuthorizationResponse;
import net.openid.appauth.TokenRequest;
import net.openid.appauth.TokenResponse;
import net.openid.appauth.ResponseTypeValues;
import java.util.concurrent.TimeUnit;
/** Public-client OAuth authorization-code + PKCE for Google Calendar. */
public final class GoogleOAuthManager {
 public static final String REDIRECT_URI="ar.goro.ai:/oauth2redirect";
 public static final String SCOPE="https://www.googleapis.com/auth/calendar.events";
 public interface Callback{void complete(boolean success,String error);}
 private final AuthorizationService service;private final GoogleTokenStore store;private final String clientId;
 public GoogleOAuthManager(Activity activity,GoogleTokenStore store,String clientId){this.service=new AuthorizationService(activity);this.store=store;this.clientId=clientId;}
 public Intent authorizationIntent(){
  if(clientId==null||clientId.trim().isEmpty())throw new IllegalStateException("Google OAuth Android client ID is not configured");
  AuthorizationServiceConfiguration config=new AuthorizationServiceConfiguration(Uri.parse("https://accounts.google.com/o/oauth2/v2/auth"),Uri.parse("https://oauth2.googleapis.com/token"));
  AuthorizationRequest request=new AuthorizationRequest.Builder(config,clientId,ResponseTypeValues.CODE,Uri.parse(REDIRECT_URI)).setScope("openid email "+SCOPE).setCodeVerifier(null,null,null).setAdditionalParameters(java.util.Collections.singletonMap("access_type","offline")).build();
  return service.getAuthorizationRequestIntent(request);
 }
 public void handleAuthorizationResult(Intent data,Callback callback){
  AuthorizationResponse response=data==null?null:AuthorizationResponse.fromIntent(data);
  AuthorizationException error=data==null?AuthorizationException.fromIntent(data):AuthorizationException.fromIntent(data);
  if(response==null){callback.complete(false,error==null?"Google authorization cancelled.":error.error);return;}
  service.performTokenRequest(response.createTokenExchangeRequest(),(token,ex)->{
   if(token==null){callback.complete(false,ex==null?"Token exchange failed.":ex.error);return;}
   long expiry=token.accessTokenExpirationTime==null?System.currentTimeMillis()+TimeUnit.HOURS.toMillis(1):token.accessTokenExpirationTime;
   store.save(token.accessToken,token.refreshToken,expiry);callback.complete(true,null);
  });
 }
 public void close(){service.dispose();}
}
