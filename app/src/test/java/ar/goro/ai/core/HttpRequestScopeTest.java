package ar.goro.ai.core;
import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class HttpRequestScopeTest {
 private static class Connection extends HttpURLConnection {
  final AtomicBoolean disconnected=new AtomicBoolean();
  Connection()throws Exception{super(new URL("https://example.invalid"));}
  public void disconnect(){disconnected.set(true);}
  public boolean usingProxy(){return false;}
  public void connect(){}
 }
 @Test public void cancelDisconnectsAndInvalidatesCallback()throws Exception{
  HttpRequestScope scope=new HttpRequestScope();
  Connection connection=new Connection();
  AtomicReference<HttpRequestScope.Ticket> ticket=new AtomicReference<>();
  CountDownLatch started=new CountDownLatch(1);
  try{
   scope.submit(t->{ticket.set(t);t.attach(connection);started.countDown();});
   assertTrue(started.await(2,TimeUnit.SECONDS));
   scope.cancel();
   assertTrue(connection.disconnected.get());assertFalse(ticket.get().current());
  }finally{scope.close();}
 }
 @Test public void replacingRequestSuppressesOldCompletion()throws Exception{
  HttpRequestScope scope=new HttpRequestScope();
  AtomicReference<HttpRequestScope.Ticket> old=new AtomicReference<>();
  CountDownLatch ready=new CountDownLatch(1),next=new CountDownLatch(1);
  try{
   scope.submit(t->{old.set(t);ready.countDown();});
   assertTrue(ready.await(2,TimeUnit.SECONDS));
   scope.submit(t->{assertTrue(t.current());next.countDown();});
   assertTrue(next.await(2,TimeUnit.SECONDS));assertFalse(old.get().current());
  }finally{scope.close();}
 }
 @Test public void closedScopeRejectsWork(){
  HttpRequestScope scope=new HttpRequestScope();scope.close();
  scope.submit(t->{throw new AssertionError("Closed work executed");});
 }
}
