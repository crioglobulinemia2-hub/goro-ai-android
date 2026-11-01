package ar.goro.ai.calendar;
import android.os.Handler;
import android.os.Looper;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
/** Direct Google Calendar v3 REST adapter. Tokens supplied by encrypted OAuth store. */
public final class GoogleCalendarRestGateway implements CalendarGateway {
 public interface Tokens {String validAccessToken() throws Exception;}
 private static final MediaType JSON=MediaType.get("application/json; charset=utf-8");
 private final OkHttpClient client=new OkHttpClient.Builder().build();private final Tokens tokens;private final String baseUrl;
 private final ExecutorService io=Executors.newSingleThreadExecutor();private final Handler main=new Handler(Looper.getMainLooper());
 public GoogleCalendarRestGateway(Tokens tokens){this(tokens,"https://www.googleapis.com/calendar/v3");}\n GoogleCalendarRestGateway(Tokens tokens,String baseUrl){if(tokens==null||baseUrl==null||!(baseUrl.startsWith("https://")||baseUrl.startsWith("http://127.0.0.1")||baseUrl.startsWith("http://localhost")))throw new IllegalArgumentException("Invalid calendar gateway configuration");this.tokens=tokens;this.baseUrl=baseUrl.endsWith("/")?baseUrl.substring(0,baseUrl.length()-1):baseUrl;}
 private JSONObject call(String method,String path,JSONObject body)throws Exception{
  String token=tokens.validAccessToken();if(token==null||token.isEmpty())throw new IllegalStateException("Google account not connected");
  Request.Builder b=new Request.Builder().url(baseUrl+path).header("Authorization","Bearer "+token).header("Accept","application/json");
  if(body!=null)b.method(method,RequestBody.create(body.toString(),JSON));else b.method(method,null);
  try(Response r=client.newCall(b.build()).execute()){
   if(r.code()==204)return new JSONObject().put("ok",true);
   String text=r.body()==null?"{}":r.body().string();
   if(!r.isSuccessful())throw new IOException("Google Calendar HTTP "+r.code());
   return new JSONObject(text);
  }
 }
 private JSONObject encode(CalendarEvent e)throws Exception{
  JSONObject start=new JSONObject().put("dateTime",e.start).put("timeZone",e.timezone);
  JSONObject end=new JSONObject().put("dateTime",e.end).put("timeZone",e.timezone);
  return new JSONObject().put("summary",e.title).put("start",start).put("end",end).put("description",e.description);
 }
 private CalendarEvent decode(JSONObject o)throws Exception{
  JSONObject s=o.getJSONObject("start"),e=o.getJSONObject("end");
  String st=s.optString("dateTime",s.optString("date")),en=e.optString("dateTime",e.optString("date"));
  return new CalendarEvent(o.optString("id"),o.optString("summary"),st,en,s.optString("timeZone","UTC"),o.optString("description"));
 }
 public void list(String q,String min,String max,Callback<List<CalendarEvent>> cb){io.execute(()->{try{
  String path="/calendars/primary/events?singleEvents=true&orderBy=startTime&timeMin="+URLEncoder.encode(min,"UTF-8")+"&timeMax="+URLEncoder.encode(max,"UTF-8")+(q==null||q.isEmpty()?"":"&q="+URLEncoder.encode(q,"UTF-8"));
  JSONArray a=call("GET",path,null).optJSONArray("items");List<CalendarEvent> out=new ArrayList<>();if(a!=null)for(int i=0;i<a.length();i++)out.add(decode(a.getJSONObject(i)));main.post(()->cb.complete(out,null));
 }catch(Exception e){main.post(()->cb.complete(null,"Google Calendar no disponible: "+e.getMessage()));}});}
 public void create(CalendarEvent e,Callback<CalendarEvent> cb){io.execute(()->{try{CalendarEvent result=decode(call("POST","/calendars/primary/events",encode(e)));main.post(()->cb.complete(result,null));}catch(Exception x){main.post(()->cb.complete(null,"No se pudo crear el evento: "+x.getMessage()));}});}
 public void update(CalendarEvent e,Callback<CalendarEvent> cb){if(e.id==null||e.id.isEmpty()){cb.complete(null,"Falta ID del evento.");return;}io.execute(()->{try{CalendarEvent result=decode(call("PATCH","/calendars/primary/events/"+URLEncoder.encode(e.id,"UTF-8"),encode(e)));main.post(()->cb.complete(result,null));}catch(Exception x){main.post(()->cb.complete(null,"No se pudo actualizar el evento."));}});}
 public void delete(String id,Callback<Boolean> cb){if(id==null||id.isEmpty()){cb.complete(false,"Falta ID del evento.");return;}io.execute(()->{try{call("DELETE","/calendars/primary/events/"+URLEncoder.encode(id,"UTF-8"),null);main.post(()->cb.complete(true,null));}catch(Exception x){main.post(()->cb.complete(false,"No se pudo eliminar el evento."));}});}
 public void close(){io.shutdownNow();}
}
