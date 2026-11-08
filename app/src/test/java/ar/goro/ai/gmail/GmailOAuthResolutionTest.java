package ar.goro.ai.gmail;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Looper;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Status;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
@LooperMode(LooperMode.Mode.PAUSED)
public class GmailOAuthResolutionTest {
 public static class FailingConsentActivity extends Activity {
  RuntimeException failure;
  @Override public void startIntentSenderForResult(IntentSender sender,int requestCode,Intent fillIn,
    int flagsMask,int flagsValues,int extraFlags)throws IntentSender.SendIntentException {
   throw failure;
  }
 }
 private static void assertConsentFailureIsRecoverable(RuntimeException failure){
  FailingConsentActivity activity=Robolectric.buildActivity(FailingConsentActivity.class).get();
  activity.failure=failure;
  PendingIntent consent=PendingIntent.getActivity(activity,0,new Intent(activity,Activity.class),PendingIntent.FLAG_IMMUTABLE);
  AuthorizationResult resolution=new AuthorizationResult(null,null,null,Collections.emptyList(),null,consent);
  AtomicInteger authorizations=new AtomicInteger(),callbacks=new AtomicInteger();
  GoroGmailGateway gateway=new GoroGmailGateway(activity,request->{
   authorizations.incrementAndGet();return Tasks.forResult(resolution);
  });
  try{
   gateway.setSelectedAccount("owner@example.test");
   for(int attempt=0;attempt<2;attempt++){
    gateway.readInbox((text,error)->{
     assertNull(text);assertEquals("No se pudo abrir el consentimiento Google",error);callbacks.incrementAndGet();
    });
    shadowOf(Looper.getMainLooper()).idle();
   }
   assertEquals(2,authorizations.get());assertEquals(2,callbacks.get());
  }finally{gateway.close();}
 }
 @Test public void rejectedConsentLaunchReturnsErrorAndDoesNotStrandTheNextRequest(){
  assertConsentFailureIsRecoverable(new SecurityException("Consent intent denied"));
 }
 @Test public void missingConsentActivityReturnsErrorAndAllowsRetry(){
  assertConsentFailureIsRecoverable(new ActivityNotFoundException("Consent activity unavailable"));
 }
 @Test public void oauthDeveloperErrorIdentifiesMissingRegisteredAndroidClient(){
  String detail=GoroGmailGateway.authorizationFailure(new ApiException(new Status(10)));
  assertTrue(detail.contains("OAuth 10"));
  assertTrue(detail.contains("SHA-1"));
 }
 @Test public void cancelledOAuthConsentIsNotMisreportedAsMissingInternet(){
  String detail=GoroGmailGateway.authorizationFailure(new ApiException(new Status(12501)));
  assertTrue(detail.contains("cancelada"));
  assertFalse(detail.toLowerCase().contains("internet"));
 }

}
