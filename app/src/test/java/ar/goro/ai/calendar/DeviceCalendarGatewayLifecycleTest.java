package ar.goro.ai.calendar;

import android.Manifest;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Looper;
import android.provider.CalendarContract;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowContentResolver;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
@LooperMode(LooperMode.Mode.PAUSED)
public class DeviceCalendarGatewayLifecycleTest {
 private static final String ONE="one@example.test",TWO="two@example.test";
 private static final String START="2026-11-04T10:00:00Z",END="2026-11-04T11:00:00Z";
 private Context context;private ManualExecutor executor;private DeviceCalendarGateway gateway;private Provider provider;
 private static final class ManualExecutor extends AbstractExecutorService {
  final Queue<Runnable> work=new ArrayDeque<>();boolean shutdown;
  public void execute(Runnable task){if(shutdown)throw new java.util.concurrent.RejectedExecutionException();work.add(task);}
  void runAll(){while(!work.isEmpty())work.remove().run();}
  public void shutdown(){shutdown=true;}
  public List<Runnable> shutdownNow(){shutdown=true;List<Runnable> result=new ArrayList<>(work);work.clear();return result;}
  public boolean isShutdown(){return shutdown;}
  public boolean isTerminated(){return shutdown&&work.isEmpty();}
  public boolean awaitTermination(long timeout,TimeUnit unit){return isTerminated();}
 }
 private static final class Provider extends ContentProvider {
  int queries,inserts,updates,deletes;String queriedAccount;Runnable afterCalendarQuery;
  int eventAllDay;String recurrenceRule,recurrenceDates;ContentValues lastUpdate;
  public boolean onCreate(){return true;}
  public String getType(Uri uri){return null;}
  public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){
   queries++;MatrixCursor cursor=new MatrixCursor(projection);
   String path=uri.getPath();
   if(path.startsWith("/calendars")){
    queriedAccount=args[0];cursor.addRow(new Object[]{ONE.equals(args[0])?1L:2L});
    Runnable hook=afterCalendarQuery;afterCalendarQuery=null;if(hook!=null)hook.run();
   }else if(path.startsWith("/instances")){
    cursor.addRow(new Object[]{11L,"Cuenta uno",1000L,2000L,"UTC","",1L});
    cursor.addRow(new Object[]{22L,"Cuenta dos",1000L,2000L,"UTC","",2L});
   }else if(path.startsWith("/events")){
    cursor.addRow(new Object[]{11L,1L,recurrenceRule,recurrenceDates});
   }
   return cursor;
  }
  public Uri insert(Uri uri,ContentValues values){inserts++;return Uri.withAppendedPath(uri,"99");}
  public int update(Uri uri,ContentValues values,String selection,String[] args){
   updates++;lastUpdate=new ContentValues(values);
   if(values.containsKey(CalendarContract.Events.ALL_DAY))eventAllDay=values.getAsInteger(CalendarContract.Events.ALL_DAY);
   return 1;
  }
  public int delete(Uri uri,String selection,String[] args){deletes++;return 1;}
 }
 @Before public void setup(){
  context=RuntimeEnvironment.getApplication();
  context.getSharedPreferences("goro_calendar_link",Context.MODE_PRIVATE).edit().clear().commit();
  shadowOf((Application)context).grantPermissions(Manifest.permission.READ_CALENDAR,Manifest.permission.WRITE_CALENDAR);
  provider=new Provider();ProviderInfo info=new ProviderInfo();info.authority=CalendarContract.AUTHORITY;
  provider.attachInfo(context,info);ShadowContentResolver.registerProviderInternal(CalendarContract.AUTHORITY,provider);
  executor=new ManualExecutor();gateway=new DeviceCalendarGateway(context,executor);gateway.setSelectedGoogleAccount(ONE);
 }
 @After public void teardown(){gateway.close();shadowOf(Looper.getMainLooper()).idle();}
 private CalendarEvent event(){return new CalendarEvent("11","Reunión",START,END,"UTC","");}
 @Test public void queuedReadIsCancelledEvenWhenAccountChangesAwayAndBack(){
  AtomicBoolean callback=new AtomicBoolean();gateway.list("",START,END,(events,error)->callback.set(true));
  gateway.setSelectedGoogleAccount(TWO);gateway.setSelectedGoogleAccount(ONE);executor.runAll();
  shadowOf(Looper.getMainLooper()).idle();assertEquals(0,provider.queries);assertFalse(callback.get());
 }
 @Test public void unlinkCancelsAllQueuedWrites(){
  AtomicBoolean callback=new AtomicBoolean();
  gateway.create(event(),(result,error)->callback.set(true));
  gateway.update(event(),(result,error)->callback.set(true));
  gateway.delete("11",(result,error)->callback.set(true));
  gateway.unlinkGoogleAccount();executor.runAll();shadowOf(Looper.getMainLooper()).idle();
  assertEquals(0,provider.queries);assertEquals(0,provider.inserts+provider.updates+provider.deletes);assertFalse(callback.get());
 }
 @Test public void accountChangeSuppressesAlreadyPostedReadResult(){
  AtomicBoolean callback=new AtomicBoolean();gateway.list("",START,END,(events,error)->callback.set(true));
  executor.runAll();assertTrue(provider.queries>0);assertFalse(callback.get());
  gateway.setSelectedGoogleAccount(TWO);shadowOf(Looper.getMainLooper()).idle();assertFalse(callback.get());
 }
 @Test public void unchangedAccountReceivesOnlyItsOwnEvents(){
  AtomicReference<List<CalendarEvent>> received=new AtomicReference<>();
  gateway.list("",START,END,(events,error)->{assertNull(error);received.set(events);});
  executor.runAll();shadowOf(Looper.getMainLooper()).idle();
  assertEquals(ONE,provider.queriedAccount);assertNotNull(received.get());
  assertEquals(1,received.get().size());assertEquals("Cuenta uno",received.get().get(0).title);
 }
 @Test public void switchDuringCalendarLookupPreventsFollowingInsert(){
  AtomicBoolean callback=new AtomicBoolean();provider.afterCalendarQuery=()->gateway.setSelectedGoogleAccount(TWO);
  gateway.create(event(),(result,error)->callback.set(true));executor.runAll();shadowOf(Looper.getMainLooper()).idle();
  assertEquals(ONE,provider.queriedAccount);assertEquals(0,provider.inserts);assertFalse(callback.get());
 }
 @Test public void unlinkFromAnotherGatewayInvalidatesPostedResult(){
  AtomicBoolean callback=new AtomicBoolean();gateway.list("",START,END,(events,error)->callback.set(true));executor.runAll();
  DeviceCalendarGateway other=new DeviceCalendarGateway(context,new ManualExecutor());
  try{other.unlinkGoogleAccount();shadowOf(Looper.getMainLooper()).idle();assertFalse(callback.get());}
  finally{other.close();}
 }
 @Test public void completedLocalWriteIsNotUndoneWhenItsOldCallbackIsSuppressed(){
  AtomicBoolean callback=new AtomicBoolean();
  gateway.create(event(),(result,error)->callback.set(true));
  executor.runAll();assertEquals(1,provider.inserts);assertFalse(callback.get());
  gateway.setSelectedGoogleAccount(TWO);shadowOf(Looper.getMainLooper()).idle();
  assertEquals(1,provider.inserts);assertEquals(0,provider.deletes);assertFalse(callback.get());
 }
 @Test public void closeSuppressesAlreadyPostedReadResult(){
  AtomicBoolean callback=new AtomicBoolean();gateway.list("",START,END,(events,error)->callback.set(true));
  executor.runAll();gateway.close();shadowOf(Looper.getMainLooper()).idle();assertFalse(callback.get());
 }
 @Test public void invalidEventBoundsCannotReachProviderWrites(){
  AtomicInteger callbacks=new AtomicInteger();
  CalendarEvent backward=new CalendarEvent(null,"Reunión",END,START,"UTC","");
  CalendarEvent empty=new CalendarEvent("11","Reunión",START,START,"UTC","");
  gateway.create(backward,(result,error)->{assertNull(result);assertTrue(error.contains("después"));callbacks.incrementAndGet();});
  gateway.update(empty,(result,error)->{assertNull(result);assertTrue(error.contains("después"));callbacks.incrementAndGet();});
  executor.runAll();shadowOf(Looper.getMainLooper()).idle();
  assertEquals(2,callbacks.get());assertEquals(0,provider.inserts+provider.updates);
 }
 @Test public void nonexistentTimezoneCannotReachProviderWrites(){
  AtomicReference<String> failure=new AtomicReference<>();
  CalendarEvent invalid=new CalendarEvent(null,"Reunión",START,END,"Invalid/Timezone","");
  gateway.create(invalid,(result,error)->{assertNull(result);failure.set(error);});
  executor.runAll();shadowOf(Looper.getMainLooper()).idle();
  assertNotNull(failure.get());assertTrue(failure.get().contains("zona horaria"));assertEquals(0,provider.inserts);
 }
 @Test public void eventBoundsCompareInstantsAcrossDifferentOffsets(){
  AtomicReference<CalendarEvent> received=new AtomicReference<>();
  CalendarEvent valid=new CalendarEvent(null,"Reunión","2026-11-04T10:00:00+02:00","2026-11-04T09:00:00Z","UTC","");
  gateway.create(valid,(result,error)->{assertNull(error);received.set(result);});
  executor.runAll();shadowOf(Looper.getMainLooper()).idle();
  assertNotNull(received.get());assertEquals(1,provider.inserts);
 }
 @Test public void reprogrammingAnAllDayEventPersistsTheRequestedTime(){
  provider.eventAllDay=1;AtomicReference<CalendarEvent> received=new AtomicReference<>();
  CalendarEvent timed=new CalendarEvent("11","Consulta","2026-11-05T15:30:00-03:00",
    "2026-11-05T16:30:00-03:00","America/Argentina/Buenos_Aires","");
  gateway.update(timed,(result,error)->{assertNull(error);received.set(result);});
  executor.runAll();shadowOf(Looper.getMainLooper()).idle();
  assertNotNull(received.get());assertEquals(1,provider.updates);assertEquals(0,provider.eventAllDay);
  assertEquals(java.time.Instant.parse("2026-11-05T18:30:00Z").toEpochMilli(),
    provider.lastUpdate.getAsLong(CalendarContract.Events.DTSTART).longValue());
  assertEquals("America/Argentina/Buenos_Aires",provider.lastUpdate.getAsString(CalendarContract.Events.EVENT_TIMEZONE));
 }
 private void assertRecurringEventIsNotModified(){
  AtomicInteger callbacks=new AtomicInteger();
  gateway.update(event(),(result,error)->{assertNull(result);assertTrue(error.contains("series recurrentes"));callbacks.incrementAndGet();});
  gateway.delete("11",(result,error)->{assertFalse(result);assertTrue(error.contains("series recurrentes"));callbacks.incrementAndGet();});
  executor.runAll();shadowOf(Looper.getMainLooper()).idle();
  assertEquals(2,callbacks.get());assertEquals(0,provider.updates+provider.deletes);
 }
 @Test public void recurrenceDatesCannotBeModifiedAsASingleOccurrence(){
  provider.recurrenceDates="20261105T100000Z,20261106T100000Z";
  assertRecurringEventIsNotModified();
 }
 @Test public void recurrenceRuleRemainsProtected(){
  provider.recurrenceRule="FREQ=DAILY;COUNT=2";
  assertRecurringEventIsNotModified();
 }
}
