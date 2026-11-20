package ar.goro.ai.gmail;

import android.app.Activity;
import android.os.Looper;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.auth.api.identity.RevokeAccessRequest;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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
public class GmailAccessRevocationTest {
 private static final String OWNER="owner@example.test";
 private static final class Harness implements AutoCloseable {
  final AtomicInteger authorizations=new AtomicInteger(),callbacks=new AtomicInteger();
  final AtomicReference<RevokeAccessRequest> request=new AtomicReference<>();
  final AtomicReference<String> text=new AtomicReference<>(),error=new AtomicReference<>();
  final TaskCompletionSource<Void> completion;
  final GoroGmailGateway gateway;
  Harness(){this(new TaskCompletionSource<>(),false);}
  Harness(TaskCompletionSource<Void> completion,boolean throwOnRevoke){
   this.completion=completion;
   Activity activity=Robolectric.buildActivity(Activity.class).get();
   gateway=new GoroGmailGateway(activity,authorization->{
    authorizations.incrementAndGet();return new TaskCompletionSource<AuthorizationResult>().getTask();
   },token->Tasks.forResult(null),(url,token,ticket)->{throw new AssertionError("No HTTP allowed");},revocation->{
    request.set(revocation);
    if(throwOnRevoke)throw new SecurityException("unavailable");
    return completion.getTask();
   });
  }
  void unlink(){
   gateway.setSelectedAccount(OWNER);
   gateway.setSelectedAccount(null);
   gateway.revokeAuthorization(OWNER,(result,problem)->{text.set(result);error.set(problem);callbacks.incrementAndGet();});
  }
  void assertLocallyUnlinked(){
   AtomicReference<String> result=new AtomicReference<>();
   gateway.readInbox((text,error)->result.set(error));
   assertTrue(result.get().contains("Elegí tu cuenta Google"));
   assertEquals(0,authorizations.get());
  }
  @Override public void close(){gateway.close();}
 }
 @Test public void unlinkRevokesTheExactAccountAndGmailScopeAfterLocalAccessStops(){
  try(Harness h=new Harness()){
   h.unlink();h.assertLocallyUnlinked();
   assertEquals(OWNER,h.request.get().getAccount().name);
   assertEquals("com.google",h.request.get().getAccount().type);
   assertEquals(Collections.singletonList(new Scope(GoroGmailGateway.GMAIL_SCOPE)),h.request.get().getScopes());
   h.completion.setResult(null);shadowOf(Looper.getMainLooper()).idle();
   assertEquals(1,h.callbacks.get());assertNull(h.error.get());
   assertTrue(h.text.get().contains("acceso a Gmail revocado"));h.assertLocallyUnlinked();
  }
 }
 @Test public void remoteFailureKeepsLocalAccountUnlinkedAndReportsUnrevokedAccess(){
  try(Harness h=new Harness()){
   h.unlink();h.completion.setException(new IllegalStateException("network"));
   shadowOf(Looper.getMainLooper()).idle();
   assertEquals(1,h.callbacks.get());assertNull(h.text.get());
   assertTrue(h.error.get().contains("No pude revocar"));h.assertLocallyUnlinked();
  }
 }
 @Test public void canceledRevocationIsNotReportedAsSuccess(){
  CancellationTokenSource cancellation=new CancellationTokenSource();
  try(Harness h=new Harness(new TaskCompletionSource<>(cancellation.getToken()),false)){
   h.unlink();cancellation.cancel();shadowOf(Looper.getMainLooper()).idle();
   assertEquals(1,h.callbacks.get());assertNull(h.text.get());assertNotNull(h.error.get());h.assertLocallyUnlinked();
  }
 }
 @Test public void immediateRevocationFailureDoesNotRestoreTheAccount(){
  try(Harness h=new Harness(new TaskCompletionSource<>(),true)){
   h.unlink();assertEquals(1,h.callbacks.get());assertNull(h.text.get());assertNotNull(h.error.get());h.assertLocallyUnlinked();
  }
 }
 @Test public void aNewAccountDoesNotReceiveAnOldRevocationResult(){
  try(Harness h=new Harness()){
   h.unlink();h.gateway.setSelectedAccount("other@example.test");
   h.completion.setResult(null);shadowOf(Looper.getMainLooper()).idle();
   assertEquals(0,h.callbacks.get());
   h.gateway.readInbox((text,error)->{});assertEquals(1,h.authorizations.get());
  }
 }
 @Test public void sameAccountCannotAuthorizeUntilItsRevocationCompletes(){
  try(Harness h=new Harness()){
   h.unlink();h.gateway.setSelectedAccount(OWNER);
   AtomicReference<String> error=new AtomicReference<>();
   h.gateway.readInbox((text,problem)->error.set(problem));
   assertTrue(error.get().contains("termine la revocación"));assertEquals(0,h.authorizations.get());
   h.completion.setResult(null);shadowOf(Looper.getMainLooper()).idle();assertEquals(0,h.callbacks.get());
   h.gateway.readInbox((text,problem)->{});assertEquals(1,h.authorizations.get());
  }
 }
 @Test public void closedGatewayDoesNotDeliverRevocationCompletion(){
  try(Harness h=new Harness()){
   h.unlink();h.gateway.close();h.completion.setResult(null);shadowOf(Looper.getMainLooper()).idle();
   assertEquals(0,h.callbacks.get());
  }
 }
}
