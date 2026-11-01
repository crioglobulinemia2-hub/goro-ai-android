package ar.goro.ai.calendar;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Google Calendar adapter contract through a first-party HTTPS OAuth proxy.
 * The proxy must hold OAuth refresh tokens server-side and enforce user identity/scopes.
 */
public final class SecureCalendarProxy implements CalendarGateway {
 private final String endpoint; private final String accessToken;
 private final ExecutorService io=Executors.newSingleThreadExecutor();
 private final Handler main=new Handler(Looper.getMainLooper());
 public SecureCalendarProxy(String endpoint,String shortLivedAccessToken){
  if(endpoint==null||!endpoint.startsWith("https://")||shortLivedAccessToken==null||shortLivedAccessToken.isEmpty())throw new IllegalArgumentException("Secure OAuth proxy configuration required");
  this.endpoint=endpoint;this.accessToken=shortLivedAccessToken;
 }
 private JSONObject request(String method,String path,JSONObject body)throws Exception{
  HttpURLConnection c=(HttpURLConnection)new URL(endpoint+path).openConnection();
  try{
   c.setRequestMethod(method);c.setConnectTimeout(10000);c.setReadTimeout(20000);
   c.setRequestProperty("Authorization","Bearer "+accessToken);c.setRequestProperty("Accept","application/json");
   if(body!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");try(OutputStream o=c.getOutputStream()){o.write(body.toString().getBytes(StandardCharsets.UTF_8));}}
   int status=c.getResponseCode();InputStream in=status>=200&&status<300?c.getInputStream():c.getErrorStream();
   if(status==204)return new JSONObject().put("ok",true);
   byte[] buf=new byte[32768];int n=0,k;while(in!=null&&(k=in.read(buf,n,buf.length-n))>0){n+=k;if(n==buf.length)break;}
   if(status<200||status>=300)throw new IllegalStateException("Calendar proxy HTTP "+status);
   return new JSONObject(new String(buf,0,n,StandardCharsets.UTF_8));
  }finally{c.disconnect();}
 }
 private JSONObject json(CalendarEvent e)throws Exception{return new JSONObject().put("id",e.id==null?"":e.id).put("summary",e.title).put("start",new JSONObject().put("dateTime",e.start).put("timeZone",e.timezone)).put("end",new JSONObject().put("dateTime",e.end).put("timeZone",e.timezone)).put("description",e.description);}
 private CalendarEvent parse(JSONObject o)throws Exception{
  return new CalendarEvent(o.optString("id"),o.optString("summary"),o.getJSONObject("start").getString("dateTime"),o.getJSONObject("end").getString("dateTime"),o.getJSONObject("start").optString("timeZone","UTC"),o.optString("description"));
 }
 @Override public void list(String query,String min,String max,Callback<List<CalendarEvent>> cb){io.execute(()->{try{String p="/calendar/events?timeMin="+URLEncoder.encode(min,"UTF-8")+"&timeMax="+URLEncoder.encode(max,"UTF-8")+"&q="+URLEncoder.encode(query==null?"":query,"UTF-8");JSONArray a=request("GET",p,null).optJSONArray("items");List<CalendarEvent> out=new ArrayList<>();if(a!=null)for(int i=0;i<a.length();i++)out.add(parse(a.getJSONObject(i)));main.post(()->cb.complete(out,null));}catch(Exception e){main.post(()->cb.complete(null,"Google Calendar no disponible o autorización vencida."));}});}
 @Override public void create(CalendarEvent e,Callback<CalendarEvent> cb){io.execute(()->{try{CalendarEvent result=parse(request("POST","/calendar/events",json(e)));main.post(()->cb.complete(result,null));}catch(Exception x){main.post(()->cb.complete(null,"No se pudo crear el evento en Google Calendar."));}});}
 @Override public void update(CalendarEvent e,Callback<CalendarEvent> cb){if(e.id==null||e.id.isEmpty()){cb.complete(null,"Falta el ID del evento.");return;}io.execute(()->{try{CalendarEvent result=parse(request("PATCH","/calendar/events/"+URLEncoder.encode(e.id,"UTF-8"),json(e)));main.post(()->cb.complete(result,null));}catch(Exception x){main.post(()->cb.complete(null,"No se pudo actualizar el evento."));}});}
 @Override public void delete(String id,Callback<Boolean> cb){if(id==null||id.isEmpty()){cb.complete(false,"Falta el ID del evento.");return;}io.execute(()->{try{request("DELETE","/calendar/events/"+URLEncoder.encode(id,"UTF-8"),null);main.post(()->cb.complete(true,null));}catch(Exception x){main.post(()->cb.complete(false,"No se pudo eliminar el evento."));}});}
 public void close(){io.shutdownNow();}
}
