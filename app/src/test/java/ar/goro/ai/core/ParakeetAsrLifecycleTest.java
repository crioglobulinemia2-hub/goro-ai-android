package ar.goro.ai.core;

import io.grpc.Context;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class ParakeetAsrLifecycleTest {
 @Test public void closedRecognizerDoesNotDeliverAnInvalidInputCallback(){
  ParakeetAsrGateway gateway=new ParakeetAsrGateway("nvapi-test");
  gateway.close();
  AtomicInteger calls=new AtomicInteger();
  gateway.transcribe(null,16000,(text,error)->calls.incrementAndGet());
  assertEquals(0,calls.get());
 }
 @Test public void invalidReplacementCancelsThePreviousGrpcRecognition()throws Exception{
  ParakeetAsrGateway gateway=new ParakeetAsrGateway("nvapi-test");
  Context.CancellableContext previous=Context.current().withCancellation();
  try{
   Field field=ParakeetAsrGateway.class.getDeclaredField("activeContext");
   field.setAccessible(true);field.set(gateway,previous);
   AtomicInteger calls=new AtomicInteger();
   gateway.transcribe(new byte[4],16000,(text,error)->{
    assertNull(text);assertNotNull(error);calls.incrementAndGet();
   });
   assertTrue(previous.isCancelled());
   assertEquals(1,calls.get());
  }finally{gateway.close();previous.close();}
 }
}
