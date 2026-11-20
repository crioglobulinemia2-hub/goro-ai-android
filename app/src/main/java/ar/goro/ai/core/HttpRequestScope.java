package ar.goro.ai.core;

import java.net.HttpURLConnection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** One cancellable request at a time; stale callbacks cannot escape a replaced request. */
public final class HttpRequestScope {
 public interface Work { void run(Ticket ticket); }
 public final class Ticket {
  private final long id;
  private Ticket(long id){this.id=id;}
  public boolean current(){synchronized(HttpRequestScope.this){return !closed&&id==generation;}}
  public void attach(HttpURLConnection connection){
   synchronized(HttpRequestScope.this){
    if(!current()){connection.disconnect();throw new java.util.concurrent.CancellationException();}
    active=connection;
   }
  }
  public void detach(HttpURLConnection connection){
   synchronized(HttpRequestScope.this){if(active==connection)active=null;}
  }
 }
 private final ExecutorService executor=Executors.newSingleThreadExecutor();
 private long generation;
 private boolean closed;
 private Future<?> running;
 private HttpURLConnection active;
 public synchronized void submit(Work work){
  if(closed)return;
  cancel();
  Ticket ticket=new Ticket(generation);
  running=executor.submit(()->{if(ticket.current())work.run(ticket);});
 }
 public synchronized void cancel(){
  generation++;
  if(running!=null)running.cancel(true);
  running=null;
  if(active!=null){active.disconnect();active=null;}
 }
 public synchronized void close(){closed=true;cancel();executor.shutdownNow();}
}
