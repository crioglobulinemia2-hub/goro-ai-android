package ar.goro.ai.calendar;

import android.Manifest;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.CalendarContract;
import android.os.Handler;
import android.os.Looper;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Uses the selected Google account's Android calendar provider; cloud sync belongs to Android. */
public final class DeviceCalendarGateway implements CalendarGateway {
 private static final Object ACCOUNT_LOCK=new Object();
 private final Context context;
 private final ExecutorService io;
 private final Handler main=new Handler(Looper.getMainLooper());
 private final android.content.SharedPreferences prefs;
 private boolean closed;
 private static final class Ticket {
  final String account;final long generation;
  Ticket(String account,long generation){this.account=account;this.generation=generation;}
 }
 public DeviceCalendarGateway(Context context){this(context,Executors.newSingleThreadExecutor());}
 DeviceCalendarGateway(Context c,ExecutorService executor){
  context=c.getApplicationContext();io=executor;
  prefs=context.getSharedPreferences("goro_calendar_link",Context.MODE_PRIVATE);
 }
 public void setSelectedGoogleAccount(String email){
  if(email==null||!email.trim().contains("@"))throw new IllegalArgumentException("Elegí una cuenta Google válida.");
  select(email.trim());
 }
 public String selectedGoogleAccount(){return prefs.getString("account_name",null);}
 public void unlinkGoogleAccount(){select(null);}
 private void select(String account){
  synchronized(ACCOUNT_LOCK){
   if(Objects.equals(selectedGoogleAccount(),account))return;
   android.content.SharedPreferences.Editor editor=prefs.edit()
     .putLong("account_generation",prefs.getLong("account_generation",0)+1);
   if(account==null)editor.remove("account_name").remove("account_type");
   else editor.putString("account_name",account).putString("account_type","com.google");
   editor.apply();
  }
 }
 private Ticket snapshot(){
  synchronized(ACCOUNT_LOCK){
   String account=selectedGoogleAccount();
   return closed||account==null?null:new Ticket(account,prefs.getLong("account_generation",0));
  }
 }
 private boolean current(Ticket ticket){
  synchronized(ACCOUNT_LOCK){
   return !closed&&ticket!=null&&ticket.generation==prefs.getLong("account_generation",0)
     &&Objects.equals(ticket.account,selectedGoogleAccount());
  }
 }
 private void requireCurrent(Ticket ticket)throws java.io.InterruptedIOException{
  if(!current(ticket))throw new java.io.InterruptedIOException("La cuenta del calendario cambió.");
 }
 private interface Mutation<T>{T run()throws Exception;}
 private <T> T mutate(Ticket ticket,Mutation<T> mutation)throws Exception{
  // A local provider write and its final account check are indivisible with account selection.
  // An already completed local write cannot be undone by subsequently changing the account.
  synchronized(ACCOUNT_LOCK){requireCurrent(ticket);return mutation.run();}
 }
 private <T> void complete(Ticket ticket,Callback<T> callback,T value,String error){
  main.post(()->{if(current(ticket))callback.complete(value,error);});
 }
 private boolean has(String permission){return context.checkSelfPermission(permission)==PackageManager.PERMISSION_GRANTED;}
 private boolean canWrite(){return has(Manifest.permission.READ_CALENDAR)&&has(Manifest.permission.WRITE_CALENDAR);}
 private long millis(String value){
  try{return OffsetDateTime.parse(value).toInstant().toEpochMilli();}
  catch(Exception e){return Instant.parse(value).toEpochMilli();}
 }
 private Set<Long> selectedCalendarIds(Ticket ticket,boolean writable)throws Exception{
  requireCurrent(ticket);
  Set<Long> ids=new HashSet<>();
  String where=CalendarContract.Calendars.ACCOUNT_NAME+"=? AND "+CalendarContract.Calendars.ACCOUNT_TYPE+"=? AND "+CalendarContract.Calendars.VISIBLE+"=1 AND "+CalendarContract.Calendars.SYNC_EVENTS+"=1";
  if(writable)where+=" AND "+CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL+">=?";
  String[] args=writable?new String[]{ticket.account,"com.google",String.valueOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR)}:new String[]{ticket.account,"com.google"};
  try(Cursor c=context.getContentResolver().query(CalendarContract.Calendars.CONTENT_URI,
    new String[]{CalendarContract.Calendars._ID},where,args,CalendarContract.Calendars.IS_PRIMARY+" DESC")){
   if(c!=null)while(c.moveToNext())ids.add(c.getLong(0));
  }
  requireCurrent(ticket);return ids;
 }
 private Long writableCalendar(Ticket ticket)throws Exception{
  requireCurrent(ticket);
  String where=CalendarContract.Calendars.ACCOUNT_NAME+"=? AND "+CalendarContract.Calendars.ACCOUNT_TYPE+"=? AND "+CalendarContract.Calendars.VISIBLE+"=1 AND "+CalendarContract.Calendars.SYNC_EVENTS+"=1 AND "+CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL+">=?";
  String[] args={ticket.account,"com.google",String.valueOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR)};
  try(Cursor c=context.getContentResolver().query(CalendarContract.Calendars.CONTENT_URI,
    new String[]{CalendarContract.Calendars._ID},where,args,CalendarContract.Calendars.IS_PRIMARY+" DESC")){
   requireCurrent(ticket);return c!=null&&c.moveToFirst()?c.getLong(0):null;
  }
 }
 @Override public void list(String query,String min,String max,Callback<List<CalendarEvent>> cb){
  if(!has(Manifest.permission.READ_CALENDAR)){cb.complete(null,"GORO necesita permiso para leer el calendario de Android.");return;}
  Ticket ticket=snapshot();
  if(ticket==null){cb.complete(null,"Primero seleccioná una cuenta Google para el calendario.");return;}
  io.execute(()->{
   try{
    requireCurrent(ticket);
    long begin=millis(min),end=millis(max);
    if(begin>=end)throw new IllegalArgumentException("Rango de agenda inválido.");
    Set<Long> ids=selectedCalendarIds(ticket,false);
    if(ids.isEmpty())throw new IllegalStateException("La cuenta seleccionada no tiene calendarios visibles con sincronización habilitada en este dispositivo.");
    List<CalendarEvent> out=new ArrayList<>();
    String[] cols={CalendarContract.Instances.EVENT_ID,CalendarContract.Instances.TITLE,CalendarContract.Instances.BEGIN,CalendarContract.Instances.END,CalendarContract.Instances.EVENT_TIMEZONE,CalendarContract.Instances.DESCRIPTION,CalendarContract.Instances.CALENDAR_ID};
    try(Cursor c=CalendarContract.Instances.query(context.getContentResolver(),cols,begin,end)){
     if(c!=null)while(c.moveToNext()){
      requireCurrent(ticket);
      String title=c.getString(1);
      if(!ids.contains(c.getLong(6))||title==null||title.trim().isEmpty())continue;
      if(query!=null&&!query.trim().isEmpty()&&!title.toLowerCase(java.util.Locale.ROOT).contains(query.trim().toLowerCase(java.util.Locale.ROOT)))continue;
      String timezone=c.getString(4),desc=c.getString(5);ZoneId zone;
      try{zone=ZoneId.of(timezone==null||timezone.isEmpty()?ZoneId.systemDefault().getId():timezone);}
      catch(Exception e){zone=ZoneId.systemDefault();}
      out.add(new CalendarEvent(String.valueOf(c.getLong(0)),title,
        Instant.ofEpochMilli(c.getLong(2)).atZone(zone).toOffsetDateTime().toString(),
        Instant.ofEpochMilli(c.getLong(3)).atZone(zone).toOffsetDateTime().toString(),zone.getId(),desc));
     }
    }
    complete(ticket,cb,out,null);
   }catch(Exception e){complete(ticket,cb,null,e.getMessage()==null?"No pude leer el calendario del dispositivo.":e.getMessage());}
  });
 }
 @Override public void create(CalendarEvent event,Callback<CalendarEvent> cb){
  if(!canWrite()){cb.complete(null,"GORO necesita permisos de lectura y escritura para modificar el calendario.");return;}
  Ticket ticket=snapshot();
  if(ticket==null){cb.complete(null,"Primero seleccioná una cuenta Google para el calendario.");return;}
  io.execute(()->{
   try{
    Long calendar=writableCalendar(ticket);
    if(calendar==null)throw new IllegalStateException("La cuenta seleccionada no tiene un calendario visible y editable con sincronización habilitada.");
    ContentValues values=values(event);values.put(CalendarContract.Events.CALENDAR_ID,calendar);values.put(CalendarContract.Events.HAS_ALARM,0);
    android.net.Uri uri=mutate(ticket,()->context.getContentResolver().insert(CalendarContract.Events.CONTENT_URI,values));
    if(uri==null)throw new IllegalStateException("El proveedor local no devolvió un evento.");
    CalendarEvent created=new CalendarEvent(String.valueOf(ContentUris.parseId(uri)),event.title,event.start,event.end,event.timezone,event.description);
    complete(ticket,cb,created,null);
   }catch(Exception e){complete(ticket,cb,null,e.getMessage()==null?"No se pudo guardar el evento localmente.":e.getMessage());}
  });
 }
 private ContentValues values(CalendarEvent event){
  long start=millis(event.start),end=millis(event.end);
  if(end<=start)throw new IllegalArgumentException("El evento debe terminar después de su inicio.");
  try{ZoneId.of(event.timezone);}
  catch(java.time.DateTimeException e){throw new IllegalArgumentException("La zona horaria del evento no es válida.");}
  ContentValues values=new ContentValues();
  values.put(CalendarContract.Events.TITLE,event.title);values.put(CalendarContract.Events.DTSTART,start);
  values.put(CalendarContract.Events.DTEND,end);values.put(CalendarContract.Events.EVENT_TIMEZONE,event.timezone);
  values.put(CalendarContract.Events.DESCRIPTION,event.description);return values;
 }
 private void verifyEditableEvent(Ticket ticket,long id)throws Exception{
  Set<Long> ids=selectedCalendarIds(ticket,true);
  String[] columns={CalendarContract.Events._ID,CalendarContract.Events.CALENDAR_ID,CalendarContract.Events.RRULE};
  try(Cursor c=context.getContentResolver().query(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI,id),columns,null,null,null)){
   requireCurrent(ticket);
   if(c==null||!c.moveToFirst())throw new IllegalStateException("Evento inexistente.");
   if(!ids.contains(c.getLong(1)))throw new IllegalStateException("Ese evento no pertenece a un calendario editable de la cuenta seleccionada.");
   if(c.getString(2)!=null&&!c.getString(2).isEmpty())throw new IllegalStateException("No modifico series recurrentes automáticamente.");
  }
 }
 @Override public void update(CalendarEvent event,Callback<CalendarEvent> cb){
  if(!canWrite()){cb.complete(null,"GORO necesita permisos de lectura y escritura para modificar el calendario.");return;}
  if(event.id==null||event.id.isEmpty()){cb.complete(null,"Falta el ID del evento.");return;}
  Ticket ticket=snapshot();
  if(ticket==null){cb.complete(null,"Primero seleccioná una cuenta Google para el calendario.");return;}
  io.execute(()->{
   try{
    long id=Long.parseLong(event.id);verifyEditableEvent(ticket,id);
    ContentValues values=values(event);
    int count=mutate(ticket,()->context.getContentResolver().update(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI,id),values,null,null));
    if(count!=1)throw new IllegalStateException("El proveedor local no confirmó la actualización.");
    complete(ticket,cb,event,null);
   }catch(Exception e){complete(ticket,cb,null,e.getMessage()==null?"No se pudo actualizar el evento localmente.":e.getMessage());}
  });
 }
 @Override public void delete(String id,Callback<Boolean> cb){
  if(!canWrite()){cb.complete(false,"GORO necesita permisos de lectura y escritura para modificar el calendario.");return;}
  Ticket ticket=snapshot();
  if(ticket==null){cb.complete(false,"Primero seleccioná una cuenta Google para el calendario.");return;}
  io.execute(()->{
   try{
    long eventId=Long.parseLong(id);verifyEditableEvent(ticket,eventId);
    int count=mutate(ticket,()->context.getContentResolver().delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI,eventId),null,null));
    complete(ticket,cb,count==1,count==1?null:"El proveedor local no confirmó la eliminación.");
   }catch(Exception e){complete(ticket,cb,false,e.getMessage()==null?"No se pudo eliminar el evento localmente.":e.getMessage());}
  });
 }
 public void close(){
  synchronized(ACCOUNT_LOCK){closed=true;}
  io.shutdownNow();main.removeCallbacksAndMessages(null);
 }
}
