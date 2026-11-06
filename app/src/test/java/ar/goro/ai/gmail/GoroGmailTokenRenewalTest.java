package ar.goro.ai.gmail;

import android.app.Activity;
import android.os.Looper;
import android.util.Base64;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.json.JSONObject;
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
public class GoroGmailTokenRenewalTest {
 private static final String OWNER="owner@example.test";
 private static AuthorizationResult authorized(String token){
  return new AuthorizationResult(null,token,null,Collections.singletonList(GoroGmailGateway.GMAIL_SCOPE),null,null);
 }
 private static void await(BooleanSupplier done)throws Exception{
  long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(4);
  while(!done.getAsBoolean()&&System.nanoTime()<deadline){shadowOf(Looper.getMainLooper()).idle();Thread.sleep(5);}
  shadowOf(Looper.getMainLooper()).idle();assertTrue("Asynchronous Gmail operation did not complete",done.getAsBoolean());
 }
 private static void seedMessage(GoroGmailGateway gateway)throws Exception{
  gateway.setSelectedAccount(OWNER);
  Field field=GoroGmailGateway.class.getDeclaredField("session");field.setAccessible(true);
  GmailAccountState session=(GmailAccountState)field.get(gateway);
  long epoch=session.begin();assertTrue(session.authorize(epoch,OWNER,"old-list-token"));
  assertTrue(session.publish(epoch,Arrays.asList("first","chosen")));
 }
 @Test public void rejectedTokenIsClearedAndSameMessageIsReadOnceWithoutTruncation()throws Exception{
  AtomicInteger auth=new AtomicInteger(),clears=new AtomicInteger(),fullReads=new AtomicInteger();
  AtomicReference<String> result=new AtomicReference<>(),error=new AtomicReference<>();
  String body="Inicio "+String.join("",Collections.nCopies(5000,"x"))+" FIN";
  GoroGmailGateway gateway=new GoroGmailGateway(Robolectric.buildActivity(Activity.class).get(),request->{
   assertEquals(OWNER,request.getAccount().name);
   return Tasks.forResult(authorized(auth.incrementAndGet()==1?"rejected-token":"fresh-token"));
  },token->{assertEquals("rejected-token",token);clears.incrementAndGet();return Tasks.forResult(null);},(url,token,ticket)->{
   if(url.endsWith("/profile"))return new JSONObject().put("emailAddress",OWNER);
   if(!url.endsWith("/messages/chosen?format=full"))throw new IllegalStateException("Unexpected URL: "+url);
   fullReads.incrementAndGet();
   if(token.equals("rejected-token"))throw new GoroGmailGateway.GmailHttpException(401);
   return new JSONObject().put("payload",new JSONObject().put("mimeType","text/plain").put("body",
     new JSONObject().put("data",Base64.encodeToString(body.getBytes(StandardCharsets.UTF_8),Base64.URL_SAFE|Base64.NO_WRAP))));
  });
  try{
   seedMessage(gateway);gateway.readMessage(2,(text,failure)->{result.set(text);error.set(failure);});
   await(()->result.get()!=null||error.get()!=null);
   assertNull(error.get());assertTrue(result.get().endsWith(body));
   assertEquals(2,auth.get());assertEquals(1,clears.get());assertEquals(2,fullReads.get());
  }finally{gateway.close();}
 }
 @Test public void aSecond401StopsInsteadOfLoopingOrRepeatedlyOpeningConsent()throws Exception{
  AtomicInteger auth=new AtomicInteger(),clears=new AtomicInteger();AtomicReference<String> error=new AtomicReference<>();
  GoroGmailGateway gateway=new GoroGmailGateway(Robolectric.buildActivity(Activity.class).get(),request->{
   auth.incrementAndGet();return Tasks.forResult(authorized("still-rejected"));
  },token->{clears.incrementAndGet();return Tasks.forResult(null);},(url,token,ticket)->{throw new GoroGmailGateway.GmailHttpException(401);});
  try{
   gateway.setSelectedAccount(OWNER);gateway.readInbox((text,failure)->error.set(failure));await(()->error.get()!=null);
   assertEquals(2,auth.get());assertEquals(1,clears.get());assertTrue(error.get().contains("autorización"));
  }finally{gateway.close();}
 }
 @Test public void forbiddenDoesNotClearTokenOrRetryAsIfExpired()throws Exception{
  AtomicInteger auth=new AtomicInteger();AtomicReference<String> error=new AtomicReference<>();
  GoroGmailGateway gateway=new GoroGmailGateway(Robolectric.buildActivity(Activity.class).get(),request->{
   auth.incrementAndGet();return Tasks.forResult(authorized("valid-token"));
  },token->{throw new AssertionError("403 must not renew authentication");},(url,token,ticket)->{throw new GoroGmailGateway.GmailHttpException(403);});
  try{
   gateway.setSelectedAccount(OWNER);gateway.readInbox((text,failure)->error.set(failure));await(()->error.get()!=null);
   assertEquals(1,auth.get());assertTrue(error.get().contains("Permiso Gmail"));
  }finally{gateway.close();}
 }
 @Test public void accountChangeDuringClearCannotRestartTheOldRequest()throws Exception{
  AtomicInteger auth=new AtomicInteger(),clears=new AtomicInteger(),callbacks=new AtomicInteger();
  TaskCompletionSource<Void> clear=new TaskCompletionSource<>();
  GoroGmailGateway gateway=new GoroGmailGateway(Robolectric.buildActivity(Activity.class).get(),request->{
   auth.incrementAndGet();return Tasks.forResult(authorized("rejected-token"));
  },token->{clears.incrementAndGet();return clear.getTask();},(url,token,ticket)->{throw new GoroGmailGateway.GmailHttpException(401);});
  try{
   gateway.setSelectedAccount(OWNER);gateway.readInbox((text,error)->callbacks.incrementAndGet());await(()->clears.get()==1);
   gateway.setSelectedAccount("another@example.test");clear.setResult(null);shadowOf(Looper.getMainLooper()).idle();
   assertEquals(1,auth.get());assertEquals(0,callbacks.get());
  }finally{gateway.close();}
 }
 @Test public void canceledAuthorizationCompletesAndAllowsANewRequest()throws Exception{
  AtomicInteger callbacks=new AtomicInteger();CancellationTokenSource cancellation=new CancellationTokenSource();
  TaskCompletionSource<AuthorizationResult> source=new TaskCompletionSource<>(cancellation.getToken());
  GoroGmailGateway gateway=new GoroGmailGateway(Robolectric.buildActivity(Activity.class).get(),request->source.getTask());
  try{
   gateway.setSelectedAccount(OWNER);gateway.readInbox((text,error)->{assertTrue(error.contains("cancelada"));callbacks.incrementAndGet();});
   cancellation.cancel();shadowOf(Looper.getMainLooper()).idle();assertEquals(1,callbacks.get());
   gateway.readInbox((text,error)->{assertTrue(error.contains("cancelada"));callbacks.incrementAndGet();});
   shadowOf(Looper.getMainLooper()).idle();assertEquals(2,callbacks.get());
  }finally{gateway.close();}
 }
 @Test public void switchingAccountAfterFullResponsePreventsBodyAttachmentDownload()throws Exception{
  AtomicInteger fullReads=new AtomicInteger(),attachmentReads=new AtomicInteger(),callbacks=new AtomicInteger();
  AtomicReference<GoroGmailGateway> holder=new AtomicReference<>();
  GoroGmailGateway gateway=new GoroGmailGateway(Robolectric.buildActivity(Activity.class).get(),
    request->Tasks.forResult(authorized("valid-token")),token->{throw new AssertionError("No refresh expected");},
    (url,token,ticket)->{
     if(url.endsWith("/profile"))return new JSONObject().put("emailAddress",OWNER);
     if(url.endsWith("?format=full")){
      holder.get().setSelectedAccount("another@example.test");fullReads.incrementAndGet();
      return new JSONObject().put("payload",new JSONObject().put("mimeType","text/plain")
        .put("body",new JSONObject().put("attachmentId","private-body")));
     }
     attachmentReads.incrementAndGet();return new JSONObject();
    });
  holder.set(gateway);
  try{
   seedMessage(gateway);gateway.readMessage(2,(text,error)->callbacks.incrementAndGet());await(()->fullReads.get()==1);
   Field requestsField=GoroGmailGateway.class.getDeclaredField("requests");requestsField.setAccessible(true);
   Object requests=requestsField.get(gateway);
   Field executorField=requests.getClass().getDeclaredField("executor");executorField.setAccessible(true);
   ((java.util.concurrent.ExecutorService)executorField.get(requests)).submit(()->{}).get(2,java.util.concurrent.TimeUnit.SECONDS);
   shadowOf(Looper.getMainLooper()).idle();
   assertEquals(0,attachmentReads.get());assertEquals(0,callbacks.get());
  }finally{gateway.close();}
 }
 @Test public void synchronousAuthorizationFailureDoesNotStrandPendingOperation(){
  AtomicInteger callbacks=new AtomicInteger();
  GoroGmailGateway gateway=new GoroGmailGateway(Robolectric.buildActivity(Activity.class).get(),request->{throw new IllegalStateException("unavailable");});
  try{
   gateway.setSelectedAccount(OWNER);
   gateway.readInbox((text,error)->{assertTrue(error.contains("iniciar"));callbacks.incrementAndGet();});
   gateway.readInbox((text,error)->{assertTrue(error.contains("iniciar"));callbacks.incrementAndGet();});
   assertEquals(2,callbacks.get());
  }finally{gateway.close();}
 }
}
