package ar.goro.ai.weather;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
public final class WeatherService {
 public static final class Current {public final double temperature,wind;public final int code;public final String time,units;Current(double t,double w,int c,String time,String units){temperature=t;wind=w;code=c;this.time=time;this.units=units;}}
 public interface Callback{void complete(Current value,String error);}
 private final ExecutorService io=Executors.newSingleThreadExecutor();private final Handler main=new Handler(Looper.getMainLooper());
 public void current(double lat,double lon,Callback cb){
  if(!Double.isFinite(lat)||!Double.isFinite(lon)||lat < -90||lat>90||lon < -180||lon>180){cb.complete(null,"Coordenadas inválidas.");return;}
  io.execute(()->{HttpURLConnection c=null;try{
   String u="https://api.open-meteo.com/v1/forecast?latitude="+lat+"&longitude="+lon+"&current=temperature_2m,wind_speed_10m,weather_code&timezone=auto";
   c=(HttpURLConnection)new URL(u).openConnection();c.setConnectTimeout(10000);c.setReadTimeout(15000);
   if(c.getResponseCode()!=200)throw new IllegalStateException("weather status");
   byte[] b=new byte[16384];int n=c.getInputStream().read(b);JSONObject j=new JSONObject(new String(b,0,n,StandardCharsets.UTF_8));JSONObject cur=j.getJSONObject("current");JSONObject units=j.getJSONObject("current_units");
   Current v=new Current(cur.getDouble("temperature_2m"),cur.getDouble("wind_speed_10m"),cur.getInt("weather_code"),cur.getString("time"),units.optString("temperature_2m","°C"));
   main.post(()->cb.complete(v,null));
  }catch(Exception e){main.post(()->cb.complete(null,"No pude obtener el pronóstico meteorológico."));}finally{if(c!=null)c.disconnect();}});
 }
 public static final class Place {public final String name,country;public final double latitude,longitude;Place(String n,String c,double lat,double lon){name=n;country=c;latitude=lat;longitude=lon;}}\n public interface PlacesCallback{void complete(java.util.List<Place> places,String error);}\n public void geocode(String place,PlacesCallback cb){\n  if(place==null||place.trim().isEmpty()){cb.complete(java.util.Collections.emptyList(),"Indicá una localidad.");return;}\n  io.execute(()->{HttpURLConnection c=null;try{String q=java.net.URLEncoder.encode(place.trim(),"UTF-8");c=(HttpURLConnection)new URL("https://geocoding-api.open-meteo.com/v1/search?name="+q+"&count=5&language=es&format=json").openConnection();c.setConnectTimeout(10000);c.setReadTimeout(15000);if(c.getResponseCode()!=200)throw new IllegalStateException();byte[] b=new byte[32768];int n=c.getInputStream().read(b);JSONObject j=new JSONObject(new String(b,0,n,StandardCharsets.UTF_8));org.json.JSONArray a=j.optJSONArray("results");java.util.List<Place> out=new java.util.ArrayList<>();if(a!=null)for(int i=0;i<a.length();i++){JSONObject p=a.getJSONObject(i);out.add(new Place(p.optString("name"),p.optString("country"),p.getDouble("latitude"),p.getDouble("longitude")));}main.post(()->cb.complete(out,null));}catch(Exception e){main.post(()->cb.complete(null,"No pude localizar esa ciudad."));}finally{if(c!=null)c.disconnect();}});\n }\n public void close(){io.shutdownNow();}
}
