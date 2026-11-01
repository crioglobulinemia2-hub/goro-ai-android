package ar.goro.ai.auth;
import android.content.Context;
import android.net.Uri;
import net.openid.appauth.AuthorizationService;
import net.openid.appauth.AuthorizationServiceConfiguration;
import net.openid.appauth.TokenRequest;
import net.openid.appauth.TokenResponse;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
/** Synchronous token supplier used only from CalendarGateway's background executor. */
public final class GoogleCalendarTokenProvider {
 private final AuthorizationService service;private final GoogleTokenStore store;private final String clientId;
 public GoogleCalendarTokenProvider(Context context,GoogleTokenStore store,String clientId){this.service=new AuthorizationService(context);this.store=store;this.clientId=clientId;}
 public String validAccessToken()throws Exception{
  String access=store.access();if(access!=null&&System.currentTimeMillis()+60000<store.expiresAt())return access;
  String refresh=store.refresh();if(refresh==null||refresh.isEmpty())throw new IllegalStateException("Google account requires authorization");
  AuthorizationServiceConfiguration config=new AuthorizationServiceConfiguration(Uri.parse("https://accounts.google.com/o/oauth2/v2/auth"),Uri.parse("https://oauth2.googleapis.com/token"));
  TokenRequest request=new TokenRequest.Builder(config,clientId).setRefreshToken(refresh).build();
  CountDownLatch latch=new CountDownLatch(1);AtomicReference<TokenResponse> result=new AtomicReference<>();AtomicReference<Exception> failure=new AtomicReference<>();
  service.performTokenRequest(request,(response,error)->{if(response!=null)result.set(response);else failure.set(new IllegalStateException(error==null?"Refresh failed":error.error));latch.countDown();});
  if(!latch.await(25,TimeUnit.SECONDS))throw new IllegalStateException("Google token refresh timed out");
  if(failure.get()!=null){store.clear();throw failure.get();}
  TokenResponse response=result.get();String nextRefresh=response.refreshToken==null?refresh:response.refreshToken;
  long expiry=response.accessTokenExpirationTime==null?System.currentTimeMillis()+3600000:response.accessTokenExpirationTime;
  store.save(response.accessToken,nextRefresh,expiry);return response.accessToken;
 }
 public void close(){service.dispose();}
}
