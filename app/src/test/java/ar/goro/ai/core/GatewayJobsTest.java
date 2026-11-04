package ar.goro.ai.core;
import android.os.Looper;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.LooperMode;
import static org.robolectric.Shadows.shadowOf;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class GatewayJobsTest {
 @Test public void closeDropsAlreadyQueuedCallbacks(){
  GatewayJobs jobs=new GatewayJobs();AtomicBoolean called=new AtomicBoolean();
  jobs.deliver(()->called.set(true));jobs.close();shadowOf(Looper.getMainLooper()).idle();
  assertFalse(called.get());
 }
 @Test public void closeDropsLateNetworkCompletionAndRejectsNewWork()throws Exception{
  GatewayJobs jobs=new GatewayJobs();CountDownLatch started=new CountDownLatch(1),finished=new CountDownLatch(1);
  AtomicBoolean called=new AtomicBoolean(),newWork=new AtomicBoolean();
  jobs.submit(()->{
   started.countDown();
   try{new CountDownLatch(1).await();}catch(InterruptedException expected){Thread.currentThread().interrupt();}
   jobs.deliver(()->called.set(true));finished.countDown();
  });
  assertTrue(started.await(3,TimeUnit.SECONDS));jobs.close();
  jobs.submit(()->newWork.set(true));assertTrue(finished.await(3,TimeUnit.SECONDS));
  shadowOf(Looper.getMainLooper()).idle();assertFalse(called.get());assertFalse(newWork.get());
 }
 @Test public void liveCallbackStillArrives(){
  GatewayJobs jobs=new GatewayJobs();AtomicBoolean called=new AtomicBoolean();
  try{jobs.deliver(()->called.set(true));shadowOf(Looper.getMainLooper()).idle();assertTrue(called.get());}
  finally{jobs.close();}
 }
}
