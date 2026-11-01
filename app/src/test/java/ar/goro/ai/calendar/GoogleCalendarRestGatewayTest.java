package ar.goro.ai.calendar;
import android.os.Looper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.LooperMode;
import org.robolectric.Shadows;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class GoogleCalendarRestGatewayTest {
 private MockWebServer server;private GoogleCalendarRestGateway gateway;
 @Before public void setUp()throws Exception{
  server=new MockWebServer();server.start();
  gateway=new GoogleCalendarRestGateway(()->"unit-test-token",server.url("/calendar/v3").toString());
 }
 @After public void tearDown()throws Exception{if(gateway!=null)gateway.close();if(server!=null)server.shutdown();}
 private <T> T await(CountDownLatch latch,AtomicReference<T> value)throws Exception{
  long end=System.currentTimeMillis()+5000;
  while(latch.getCount()>0&&System.currentTimeMillis()<end){Shadows.shadowOf(Looper.getMainLooper()).idle();Thread.sleep(10);}
  assertTrue("callback timed out",latch.await(100,TimeUnit.MILLISECONDS));return value.get();
 }
 private static final String EVENT="{\"id\":\"evt-1\",\"summary\":\"Reunión\",\"start\":{\"dateTime\":\"2026-11-03T10:00:00-03:00\",\"timeZone\":\"America/Argentina/Buenos_Aires\"},\"end\":{\"dateTime\":\"2026-11-03T10:30:00-03:00\",\"timeZone\":\"America/Argentina/Buenos_Aires\"}}";
 @Test public void listsEventsAndUsesBearerToken()throws Exception{
  server.enqueue(new MockResponse().setResponseCode(200).setHeader("Content-Type","application/json").setBody("{\"items\":["+EVENT+"]}"));
  CountDownLatch latch=new CountDownLatch(1);AtomicReference<List<CalendarEvent>> value=new AtomicReference<>();AtomicReference<String> error=new AtomicReference<>();
  gateway.list("", "2026-11-03T00:00:00-03:00","2026-11-04T00:00:00-03:00",(v,e)->{value.set(v);error.set(e);latch.countDown();});
  List<CalendarEvent> result=await(latch,value);assertNull(error.get());assertEquals(1,result.size());assertEquals("evt-1",result.get(0).id);
  assertEquals("Bearer unit-test-token",server.takeRequest().getHeader("Authorization"));
 }
 @Test public void createsAndUpdatesWithConfirmedServerResponses()throws Exception{
  server.enqueue(new MockResponse().setResponseCode(200).setHeader("Content-Type","application/json").setBody(EVENT));
  CalendarEvent event=new CalendarEvent(null,"Reunión","2026-11-03T10:00:00-03:00","2026-11-03T10:30:00-03:00","America/Argentina/Buenos_Aires","");
  CountDownLatch createLatch=new CountDownLatch(1);AtomicReference<CalendarEvent> created=new AtomicReference<>();AtomicReference<String> createError=new AtomicReference<>();
  gateway.create(event,(v,e)->{created.set(v);createError.set(e);createLatch.countDown();});
  assertEquals("evt-1",await(createLatch,created).id);assertNull(createError.get());assertEquals("POST",server.takeRequest().getMethod());
  server.enqueue(new MockResponse().setResponseCode(200).setHeader("Content-Type","application/json").setBody(EVENT));
  CalendarEvent update=new CalendarEvent("evt-1","Reunión actualizada",event.start,event.end,event.timezone,"");
  CountDownLatch updateLatch=new CountDownLatch(1);AtomicReference<CalendarEvent> updated=new AtomicReference<>();AtomicReference<String> updateError=new AtomicReference<>();
  gateway.update(update,(v,e)->{updated.set(v);updateError.set(e);updateLatch.countDown();});
  assertNotNull(await(updateLatch,updated));assertNull(updateError.get());assertEquals("PATCH",server.takeRequest().getMethod());
 }
 @Test public void deletesOnlyAfterHttp204()throws Exception{
  server.enqueue(new MockResponse().setResponseCode(204));
  CountDownLatch latch=new CountDownLatch(1);AtomicReference<Boolean> result=new AtomicReference<>();AtomicReference<String> error=new AtomicReference<>();
  gateway.delete("evt-1",(v,e)->{result.set(v);error.set(e);latch.countDown();});
  assertEquals(Boolean.TRUE,await(latch,result));assertNull(error.get());assertEquals("DELETE",server.takeRequest().getMethod());
 }
 @Test public void reportsAuthorizationFailureInsteadOfSuccess()throws Exception{
  server.enqueue(new MockResponse().setResponseCode(401).setBody("{\"error\":\"unauthorized\"}"));
  CountDownLatch latch=new CountDownLatch(1);AtomicReference<String> error=new AtomicReference<>();
  gateway.list("", "2026-11-03T00:00:00Z","2026-11-04T00:00:00Z",(v,e)->{error.set(e);latch.countDown();});
  await(latch,error);assertNotNull(error.get());
 }
}
