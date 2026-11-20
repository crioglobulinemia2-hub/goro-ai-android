package ar.goro.ai.gmail;

import android.app.Activity;
import android.os.Looper;
import ar.goro.ai.core.HttpRequestScope;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.tasks.TaskCompletionSource;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class GoroGmailGatewayLifecycleTest {
 private static Object field(Object target,String name)throws Exception{
  Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);
 }
 private static GmailAccountState seed(GoroGmailGateway gateway)throws Exception{
  gateway.setSelectedAccount("one@example.test");
  GmailAccountState state=(GmailAccountState)field(gateway,"session");
  long epoch=state.begin();assertTrue(state.authorize(epoch,"one@example.test","old-token"));
  assertTrue(state.publish(epoch,Arrays.asList("first","chosen")));return state;
 }
 @Test public void readingListedMessageRequestsFreshAuthorizationForSameId()throws Exception{
  List<TaskCompletionSource<AuthorizationResult>> authorizations=new ArrayList<>();
  GoroGmailGateway gateway=new GoroGmailGateway(Robolectric.buildActivity(Activity.class).get(),request->{
   TaskCompletionSource<AuthorizationResult> task=new TaskCompletionSource<>();authorizations.add(task);return task.getTask();
  });
  try{
   GmailAccountState state=seed(gateway);
   long inbox=state.generation();
   gateway.readMessage(2,(result,error)->fail("No authorization result yet"));
   assertEquals(1,authorizations.size());
   assertNull(state.token());assertFalse(state.current(inbox));
   assertEquals("chosen",field(gateway,"pendingMessageId"));
   assertEquals("chosen",state.messageId(2));
   assertNull(field(gateway,"pendingQuery"));
  }finally{gateway.close();}
 }
 @Test public void lateAuthorizationFailureCannotCompleteNewAccountRequest()throws Exception{
  List<TaskCompletionSource<AuthorizationResult>> authorizations=new ArrayList<>();
  GoroGmailGateway gateway=new GoroGmailGateway(Robolectric.buildActivity(Activity.class).get(),request->{
   TaskCompletionSource<AuthorizationResult> task=new TaskCompletionSource<>();authorizations.add(task);return task.getTask();
  });
  AtomicBoolean oldCallback=new AtomicBoolean(),newCallback=new AtomicBoolean();
  try{
   seed(gateway);gateway.readMessage(2,(result,error)->oldCallback.set(true));
   gateway.setSelectedAccount("two@example.test");
   gateway.readInbox((result,error)->newCallback.set(true));
   assertEquals(2,authorizations.size());
   authorizations.get(0).setException(new IllegalStateException("late old authorization"));
   shadowOf(Looper.getMainLooper()).idle();
   assertFalse(oldCallback.get());assertFalse(newCallback.get());
   assertNotNull(field(gateway,"pending"));
   assertNull(((GmailAccountState)field(gateway,"session")).messageId(2));
  }finally{gateway.close();}
 }
 private static final class Connection extends HttpURLConnection {
  final AtomicBoolean disconnected=new AtomicBoolean();
  Connection()throws Exception{super(new URL("https://example.invalid"));}
  public void disconnect(){disconnected.set(true);}
  public boolean usingProxy(){return false;}
  public void connect(){}
 }
 private static HttpRequestScope.Ticket attach(GoroGmailGateway gateway,Connection connection)throws Exception{
  HttpRequestScope requests=(HttpRequestScope)field(gateway,"requests");
  AtomicReference<HttpRequestScope.Ticket> ticket=new AtomicReference<>();
  CountDownLatch attached=new CountDownLatch(1);
  requests.submit(t->{ticket.set(t);t.attach(connection);attached.countDown();});
  assertTrue(attached.await(2,TimeUnit.SECONDS));return ticket.get();
 }
 @Test public void unlinkDisconnectsActiveGmailRequestAndInvalidatesItsTicket()throws Exception{
  GoroGmailGateway gateway=new GoroGmailGateway(Robolectric.buildActivity(Activity.class).get(),request->{
   throw new AssertionError("No authorization expected");
  });
  try{
   seed(gateway);Connection connection=new Connection();
   HttpRequestScope.Ticket ticket=attach(gateway,connection);
   gateway.setSelectedAccount(null);
   assertTrue(connection.disconnected.get());assertFalse(ticket.current());
   assertNull(((GmailAccountState)field(gateway,"session")).messageId(1));
  }finally{gateway.close();}
 }
 @Test public void closingDisconnectsActiveGmailRequestAndInvalidatesItsTicket()throws Exception{
  GoroGmailGateway gateway=new GoroGmailGateway(Robolectric.buildActivity(Activity.class).get(),request->{
   throw new AssertionError("No authorization expected");
  });
  try{
   seed(gateway);Connection connection=new Connection();
   HttpRequestScope.Ticket ticket=attach(gateway,connection);
   gateway.close();
   assertTrue(connection.disconnected.get());assertFalse(ticket.current());
  }finally{gateway.close();}
 }
}
