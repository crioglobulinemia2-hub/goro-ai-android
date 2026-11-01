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
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
/** Uses Android's already-synced calendar accounts. No OAuth client, server, or embedded secrets. */
public final class DeviceCalendarGateway implements CalendarGateway {
 private final Context context;private final ExecutorService io=Executors.newSingleThreadExecutor();private final Handler main=new Handler(Looper.getMainLooper());
 private final android.content.SharedPreferences prefs;
 public DeviceCalendarGateway(Context c){context=c.getApplicationContext();prefs=context.getSharedPreferences("goro_calendar_link",Context.MODE_PRIVATE);}
 public void setSelectedGoogleAccount(String email){if(email==null||!email.trim().contains("@"))throw new IllegalArgumentException("Elegí una cuenta Google válida.");prefs.edit().putString("account_name",email.trim()).putString("account_type","com.google").apply();}
 public String selectedGoogleAccount(){return prefs.getString("account_name",null);}
 public void unlinkGoogleAccount(){prefs.edit().clear().apply();}
 private java.util.Set<Long> selectedCalendarIds(boolean writable){
  String email=selectedGoogleAccount();java.util.Set<Long> ids=new java.util.HashSet<>();if(email==null||email.isEmpty())return ids;
  String where=CalendarContract.Calendars.ACCOUNT_NAME+"=? AND "+CalendarContract.Calendars.ACCOUNT_TYPE+"=? AND "+CalendarContract.Calendars.VISIBLE+"=1 AND "+CalendarContract.Calendars.SYNC_EVENTS+"=1";
  if(writable)where+=" AND "+CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL+">=?";
  String[] args=writable?new String[]{email,"com.google",String.valueOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR)}:new String[]{email,"com.google"};
  try(Cursor c=context.getContentResolver().query(CalendarContract.Calendars.CONTENT_URI,new String[]{CalendarContract.Calendars._ID},where,args,CalendarContract.Calendars.IS_PRIMARY+" DESC")){if(c!=null)while(c.moveToNext())ids.add(c.getLong(0));}
  return ids;
 }
 private Long writableCalendar(){
  String email=selectedGoogleAccount();if(email==null)return null;
  String where=CalendarContract.Calendars.ACCOUNT_NAME+"=? AND "+CalendarContract.Calendars.ACCOUNT_TYPE+"=? AND "+CalendarContract.Calendars.VISIBLE+"=1 AND "+CalendarContract.Calendars.SYNC_EVENTS+"=1 AND "+CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL+">=?";
  String[] args={email,"com.google",String.valueOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR)};
  try(Cursor c=context.getContentResolver().query(CalendarContract.Calendars.CONTENT_URI,new String[]{CalendarContract.Calendars._ID},where,args,CalendarContract.Calendars.IS_PRIMARY+" DESC")){if(c!=null&&c.moveToFirst())return c.getLong(0);}
  return null;
 }
 private boolean has(String p){return context.checkSelfPermission(p)==PackageManager.PERMISSION_GRANTED;}
 private long millis(String value){try{return OffsetDateTime.parse(value).toInstant().toEpochMilli();}catch(Exception e){return Instant.parse(value).toEpochMilli();}}
 @Override public void list(String query,String min,String max,Callback<List<CalendarEvent>> cb){
  if(!has(Manifest.permission.READ_CALENDAR)){cb.complete(null,"GORO necesita permiso para leer Google Calendar sincronizado.");return;}
  if(selectedGoogleAccount()==null){cb.complete(null,"Primero vinculá la cuenta Gmail que usará GORO.");return;}
  io.execute(()->{
   List<CalendarEvent> out=new ArrayList<>();
   try{
    long begin=millis(min),end=millis(max);
    if(begin>=end)throw new IllegalArgumentException("Rango de agenda inválido.");
    java.util.Set<Long> googleCalendarIds=selectedCalendarIds(false);
    if(googleCalendarIds.isEmpty())throw new IllegalStateException("La cuenta Gmail vinculada no tiene calendarios visibles y sincronizados en este dispositivo.");
    String[] cols={CalendarContract.Instances.EVENT_ID,CalendarContract.Instances.TITLE,CalendarContract.Instances.BEGIN,CalendarContract.Instances.END,CalendarContract.Instances.EVENT_TIMEZONE,CalendarContract.Instances.DESCRIPTION,CalendarContract.Instances.CALENDAR_ID};
    try(Cursor c=CalendarContract.Instances.query(context.getContentResolver(),cols,begin,end)){
     if(c!=null)while(c.moveToNext()){
      String title=c.getString(1);
      if(!googleCalendarIds.contains(c.getLong(6))||title==null||title.trim().isEmpty())continue;
      if(query!=null&&!query.trim().isEmpty()&&!title.toLowerCase(java.util.Locale.ROOT).contains(query.trim().toLowerCase(java.util.Locale.ROOT)))continue;
      String timezone=c.getString(4),desc=c.getString(5);
      ZoneId z;
      try{z=ZoneId.of(timezone==null||timezone.isEmpty()?ZoneId.systemDefault().getId():timezone);}catch(Exception invalid){z=ZoneId.systemDefault();}
      long startMs=c.getLong(2),endMs=c.getLong(3);
      out.add(new CalendarEvent(String.valueOf(c.getLong(0)),title,Instant.ofEpochMilli(startMs).atZone(z).toOffsetDateTime().toString(),Instant.ofEpochMilli(endMs).atZone(z).toOffsetDateTime().toString(),z.getId(),desc));
     }
    }
    main.post(()->cb.complete(out,null));
   }catch(Exception e){String message=e.getMessage()==null?"No pude leer Google Calendar sincronizado.":e.getMessage();main.post(()->cb.complete(null,message));}
  });
 }
 @Override public void create(CalendarEvent e,Callback<CalendarEvent> cb){
  if(!has(Manifest.permission.READ_CALENDAR)||!has(Manifest.permission.WRITE_CALENDAR)){cb.complete(null,"GORO necesita permisos de lectura y escritura para modificar el calendario.");return;}
  if(selectedGoogleAccount()==null){cb.complete(null,"Primero vinculá la cuenta Gmail que usará GORO.");return;}
  io.execute(()->{try{Long cal=writableCalendar();if(cal==null)throw new IllegalStateException("La cuenta Gmail vinculada no tiene un calendario visible, sincronizado y editable.");ContentValues v=new ContentValues();v.put(CalendarContract.Events.CALENDAR_ID,cal);v.put(CalendarContract.Events.TITLE,e.title);v.put(CalendarContract.Events.DTSTART,millis(e.start));v.put(CalendarContract.Events.DTEND,millis(e.end));v.put(CalendarContract.Events.EVENT_TIMEZONE,e.timezone);v.put(CalendarContract.Events.DESCRIPTION,e.description);v.put(CalendarContract.Events.HAS_ALARM,0);android.net.Uri uri=context.getContentResolver().insert(CalendarContract.Events.CONTENT_URI,v);if(uri==null)throw new IllegalStateException("El proveedor no devolvió un evento.");String id=String.valueOf(ContentUris.parseId(uri));CalendarEvent created=new CalendarEvent(id,e.title,e.start,e.end,e.timezone,e.description);main.post(()->cb.complete(created,null));}catch(Exception x){main.post(()->cb.complete(null,x.getMessage()==null?"No se pudo crear el evento.":x.getMessage()));}});
 }
 @Override public void update(CalendarEvent e,Callback<CalendarEvent> cb){
  if(!has(Manifest.permission.WRITE_CALENDAR)){cb.complete(null,"GORO necesita permiso para modificar el calendario.");return;}
  if(e.id==null||e.id.isEmpty()){cb.complete(null,"Falta el ID del evento.");return;}
  io.execute(()->{try{long id=Long.parseLong(e.id);String[] p={CalendarContract.Events._ID,CalendarContract.Events.CALENDAR_ID,CalendarContract.Events.RRULE};try(Cursor c=context.getContentResolver().query(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI,id),p,null,null,null)){if(c==null||!c.moveToFirst())throw new IllegalStateException("Evento inexistente.");if(!selectedCalendarIds(false).contains(c.getLong(1)))throw new IllegalStateException("Ese evento no pertenece a la cuenta Gmail vinculada.");if(c.getString(2)!=null&&!c.getString(2).isEmpty())throw new IllegalStateException("No modifico series recurrentes automáticamente.");}ContentValues v=new ContentValues();v.put(CalendarContract.Events.TITLE,e.title);v.put(CalendarContract.Events.DTSTART,millis(e.start));v.put(CalendarContract.Events.DTEND,millis(e.end));v.put(CalendarContract.Events.EVENT_TIMEZONE,e.timezone);v.put(CalendarContract.Events.DESCRIPTION,e.description);int n=context.getContentResolver().update(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI,id),v,null,null);if(n!=1)throw new IllegalStateException("El calendario no confirmó la actualización.");main.post(()->cb.complete(e,null));}catch(Exception x){main.post(()->cb.complete(null,x.getMessage()==null?"No se pudo actualizar el evento.":x.getMessage()));}});
 }
 @Override public void delete(String id,Callback<Boolean> cb){
  if(!has(Manifest.permission.READ_CALENDAR)||!has(Manifest.permission.WRITE_CALENDAR)){cb.complete(false,"GORO necesita permisos de lectura y escritura para modificar el calendario.");return;}
  io.execute(()->{try{long eventId=Long.parseLong(id);String[] p={CalendarContract.Events._ID,CalendarContract.Events.CALENDAR_ID,CalendarContract.Events.RRULE};try(Cursor c=context.getContentResolver().query(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI,eventId),p,null,null,null)){if(c==null||!c.moveToFirst())throw new IllegalStateException("Evento inexistente.");if(!selectedCalendarIds(false).contains(c.getLong(1)))throw new IllegalStateException("Ese evento no pertenece a la cuenta Gmail vinculada.");if(c.getString(2)!=null&&!c.getString(2).isEmpty())throw new IllegalStateException("No elimino series recurrentes automáticamente.");}int n=context.getContentResolver().delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI,eventId),null,null);main.post(()->cb.complete(n==1,n==1?null:"El calendario no confirmó la eliminación."));}catch(Exception x){main.post(()->cb.complete(false,x.getMessage()==null?"No se pudo eliminar el evento.":x.getMessage()));}});
 }
 public void close(){io.shutdownNow();}
}
