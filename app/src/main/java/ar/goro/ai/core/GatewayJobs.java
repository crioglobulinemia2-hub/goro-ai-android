package ar.goro.ai.core;

import android.os.Handler;
import android.os.Looper;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Owns network work and callbacks for exactly one gateway lifetime. */
final class GatewayJobs {
 private final ExecutorService executor=Executors.newSingleThreadExecutor();
 private final Handler main=new Handler(Looper.getMainLooper());
 private volatile boolean closed;
 private HttpURLConnection active;
 synchronized void submit(Runnable work){if(!closed)executor.execute(work);}
 synchronized void deliver(Runnable callback){
  if(!closed)main.post(()->{if(!closed)callback.run();});
 }
 synchronized HttpURLConnection open(String endpoint)throws Exception{
  if(closed)throw new InterruptedIOException("Servicio cerrado");
  active=(HttpURLConnection)new URL(endpoint).openConnection();
  return active;
 }
 synchronized void finished(HttpURLConnection connection){
  if(connection!=null)connection.disconnect();
  if(active==connection)active=null;
 }
 boolean isClosed(){return closed;}
 synchronized void close(){
  if(closed)return;
  closed=true;
  main.removeCallbacksAndMessages(null);
  executor.shutdownNow();
  if(active!=null){active.disconnect();active=null;}
 }
}
