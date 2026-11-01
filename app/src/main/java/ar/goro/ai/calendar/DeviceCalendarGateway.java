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
 public DeviceCalendarGateway(Context c){context=c.getApplicationContext();}
 private boolean has(String p){return context.checkSelfPermission(p)==PackageManager.PERMISSION_GRANTED;}
 private long millis(String value){try{return OffsetDateTime.parse(value).toInstant().toEpochMilli();}catch(Exception e){return Instant.parse(value).toEpochMilli();}}
 private CalendarEvent read(Cursor c){long id=c.getLong(c.getColumnIndexOrThrow(CalendarContract.Events._ID));String title=c.getString(c.getColumnIndexOrThrow(CalendarContract.Events.TITLE));long begin=c.getLong(c.getColumnIndexOrThrow(CalendarContract.Events.DTSTART));long end=c.getLong(c.getColumnIndexOrThrow(CalendarContract.Events.DTEND));String zone=c.getString(c.getColumnIndex(CalendarContract.Events.EVENT_TIMEZONE));String desc=c.getString(c.getColumnIndex(CalendarContract.Events.DESCRIPTION));ZoneId z=ZoneId.of(zone==null?ZoneId.systemDefault().getId():zone);return new CalendarEvent(String.valueOf(id),title,Instant.ofEpochMilli(begin).atZone(z).toOffsetDateTime().toString(),Instant.ofEpochMilli(end).atZone(z).toOffsetDateTime().toString(),z.getId(),desc);}
 private Long writableCalendar(){String[] p={CalendarContract.Calendars._ID,CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,CalendarContract.Calendars.VISIBLE,CalendarContract.Calendars.SYNC_EVENTS};try(Cursor c=context.getContentResolver().query(CalendarContract.Calendars.CONTENT_URI,p,CalendarContract.Calendars.VISIBLE+"=1 AND "+CalendarContract.Calendars.SYNC_EVENTS+"=1 AND "+CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL+">=?",new String[]{String.valueOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR)},CalendarContract.Calendars.IS_PRIMARY+" DESC")){if(c!=null&&c.moveToFirst())return c.getLong(0);}return null;}
 @Override public void list(String query,String min,String max,Callback<List<CalendarEvent>> cb){
  if(!has(Manifest.permission.READ_CALENDAR)){cb.complete(null,"GORO necesita permiso para leer los calendarios del dispositivo.");return;}
  io.execute(()->{List<CalendarEvent> out=new ArrayList<>();try{String selection=CalendarContract.Events.DTSTART+">=? AND "+CalendarContract.Events.DTSTART+"< ? AND "+CalendarContract.Events.DELETED+"=0 AND "+CalendarContract.Events.RRULE+" IS NULL";List<String> args=new ArrayList<>();args.add(String.valueOf(millis(min)));args.add(String.valueOf(millis(max)));if(query!=null&&!query.trim().isEmpty()){selection+=" AND "+CalendarContract.Events.TITLE+" LIKE ?";args.add("%"+query.trim()+"%");}String[] cols={CalendarContract.Events._ID,CalendarContract.Events.TITLE,CalendarContract.Events.DTSTART,CalendarContract.Events.DTEND,CalendarContract.Events.EVENT_TIMEZONE,CalendarContract.Events.DESCRIPTION};try(Cursor c=context.getContentResolver().query(CalendarContract.Events.CONTENT_URI,cols,selection,args.toArray(new String[0]),CalendarContract.Events.DTSTART+" ASC")){if(c!=null)while(c.moveToNext())out.add(read(c));}main.post(()->cb.complete(out,null));}catch(Exception e){main.post(()->cb.complete(null,"No pude leer los calendarios del dispositivo."));}});
 }
 @Override public void create(CalendarEvent e,Callback<CalendarEvent> cb){
  if(!has(Manifest.permission.WRITE_CALENDAR)){cb.complete(null,"GORO necesita permiso para modificar el calendario.");return;}
  io.execute(()->{try{Long cal=writableCalendar();if(cal==null)throw new IllegalStateException("No hay un calendario visible y editable sincronizado.");ContentValues v=new ContentValues();v.put(CalendarContract.Events.CALENDAR_ID,cal);v.put(CalendarContract.Events.TITLE,e.title);v.put(CalendarContract.Events.DTSTART,millis(e.start));v.put(CalendarContract.Events.DTEND,millis(e.end));v.put(CalendarContract.Events.EVENT_TIMEZONE,e.timezone);v.put(CalendarContract.Events.DESCRIPTION,e.description);v.put(CalendarContract.Events.HAS_ALARM,0);android.net.Uri uri=context.getContentResolver().insert(CalendarContract.Events.CONTENT_URI,v);if(uri==null)throw new IllegalStateException("El proveedor no devolvió un evento.");String id=String.valueOf(ContentUris.parseId(uri));CalendarEvent created=new CalendarEvent(id,e.title,e.start,e.end,e.timezone,e.description);main.post(()->cb.complete(created,null));}catch(Exception x){main.post(()->cb.complete(null,x.getMessage()==null?"No se pudo crear el evento.":x.getMessage()));}});
 }
 @Override public void update(CalendarEvent e,Callback<CalendarEvent> cb){
  if(!has(Manifest.permission.WRITE_CALENDAR)){cb.complete(null,"GORO necesita permiso para modificar el calendario.");return;}
  if(e.id==null||e.id.isEmpty()){cb.complete(null,"Falta el ID del evento.");return;}
  io.execute(()->{try{long id=Long.parseLong(e.id);String[] p={CalendarContract.Events._ID,CalendarContract.Events.RRULE};try(Cursor c=context.getContentResolver().query(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI,id),p,null,null,null)){if(c==null||!c.moveToFirst())throw new IllegalStateException("Evento inexistente.");if(c.getString(1)!=null&&!c.getString(1).isEmpty())throw new IllegalStateException("No modifico series recurrentes automáticamente.");}ContentValues v=new ContentValues();v.put(CalendarContract.Events.TITLE,e.title);v.put(CalendarContract.Events.DTSTART,millis(e.start));v.put(CalendarContract.Events.DTEND,millis(e.end));v.put(CalendarContract.Events.EVENT_TIMEZONE,e.timezone);v.put(CalendarContract.Events.DESCRIPTION,e.description);int n=context.getContentResolver().update(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI,id),v,null,null);if(n!=1)throw new IllegalStateException("El calendario no confirmó la actualización.");main.post(()->cb.complete(e,null));}catch(Exception x){main.post(()->cb.complete(null,x.getMessage()==null?"No se pudo actualizar el evento.":x.getMessage()));}});
 }
 @Override public void delete(String id,Callback<Boolean> cb){
  if(!has(Manifest.permission.WRITE_CALENDAR)){cb.complete(false,"GORO necesita permiso para modificar el calendario.");return;}
  io.execute(()->{try{long eventId=Long.parseLong(id);String[] p={CalendarContract.Events._ID,CalendarContract.Events.RRULE};try(Cursor c=context.getContentResolver().query(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI,eventId),p,null,null,null)){if(c==null||!c.moveToFirst())throw new IllegalStateException("Evento inexistente.");if(c.getString(1)!=null&&!c.getString(1).isEmpty())throw new IllegalStateException("No elimino series recurrentes automáticamente.");}int n=context.getContentResolver().delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI,eventId),null,null);main.post(()->cb.complete(n==1,n==1?null:"El calendario no confirmó la eliminación."));}catch(Exception x){main.post(()->cb.complete(false,x.getMessage()==null?"No se pudo eliminar el evento.":x.getMessage()));}});
 }
 public void close(){io.shutdownNow();}
}
