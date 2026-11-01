package ar.goro.ai.weather;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
public final class WeatherService {
 public static final class Current {
  public final double temperature,wind;public final int code;public final String time,temperatureUnit,windUnit;
  Current(double t,double w,int c,String time,String tu,String wu){temperature=t;wind=w;code=c;this.time=time;temperatureUnit=tu;windUnit=wu;}
 }
 public static final class ForecastDay {
  public final String date;public final double min,max;public final int precipitationChance,code;
  ForecastDay(String date,double min,double max,int rain,int code){this.date=date;this.min=min;this.max=max;precipitationChance=rain;this.code=code;}
 }
 public static final class Place {
  public final String name,country;public final double latitude,longitude;
  Place(String n,String c,double lat,double lon){name=n;country=c;latitude=lat;longitude=lon;}
 }
 public interface Callback{void complete(Current value,String error);}
 public interface PlacesCallback{void complete(List<Place> places,String error);}
 public interface ForecastCallback{void complete(List<ForecastDay> days,String error);}
 private final ExecutorService io=Executors.newSingleThreadExecutor();private final Handler main=new Handler(Looper.getMainLooper());
 private String get(String url)throws Exception{
  HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
  try{c.setConnectTimeout(10000);c.setReadTimeout(15000);c.setRequestProperty("Accept","application/json");int status=c.getResponseCode();if(status!=200)throw new IllegalStateException("Weather HTTP "+status);
   try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[4096];int n,total=0;while((n=in.read(buffer))!=-1){total+=n;if(total>262144)throw new IllegalStateException("Weather response too large");out.write(buffer,0,n);}return out.toString(StandardCharsets.UTF_8.name());}
  }finally{c.disconnect();}
 }
 public void current(double lat,double lon,Callback cb){
  if(!validCoordinates(lat,lon)){cb.complete(null,"Coordenadas inválidas.");return;}
  io.execute(()->{try{String url="https://api.open-meteo.com/v1/forecast?latitude="+lat+"&longitude="+lon+"&current=temperature_2m,wind_speed_10m,weather_code&timezone=auto";
   JSONObject j=new JSONObject(get(url)),cur=j.getJSONObject("current"),units=j.getJSONObject("current_units");
   Current v=new Current(cur.getDouble("temperature_2m"),cur.getDouble("wind_speed_10m"),cur.getInt("weather_code"),cur.getString("time"),units.optString("temperature_2m","°C"),units.optString("wind_speed_10m","km/h"));main.post(()->cb.complete(v,null));
  }catch(Exception e){main.post(()->cb.complete(null,"No pude obtener el clima actual."));}});
 }
 public void forecast(double lat,double lon,int days,ForecastCallback cb){
  if(!validCoordinates(lat,lon)||days<1||days>7){cb.complete(Collections.emptyList(),"Localidad o cantidad de días inválida.");return;}
  io.execute(()->{try{String url="https://api.open-meteo.com/v1/forecast?latitude="+lat+"&longitude="+lon+"&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max,weather_code&forecast_days="+days+"&timezone=auto";
   JSONObject daily=new JSONObject(get(url)).getJSONObject("daily");JSONArray dates=daily.getJSONArray("time"),max=daily.getJSONArray("temperature_2m_max"),min=daily.getJSONArray("temperature_2m_min"),rain=daily.getJSONArray("precipitation_probability_max"),codes=daily.getJSONArray("weather_code");
   List<ForecastDay> out=new ArrayList<>();for(int i=0;i<dates.length();i++)out.add(new ForecastDay(dates.getString(i),min.getDouble(i),max.getDouble(i),rain.isNull(i)?0:rain.getInt(i),codes.getInt(i)));
   main.post(()->cb.complete(out,null));
  }catch(Exception e){main.post(()->cb.complete(null,"No pude obtener el pronóstico extendido."));}});
 }
 public void geocode(String place,PlacesCallback cb){
  if(place==null||place.trim().isEmpty()){cb.complete(Collections.emptyList(),"Indicá una localidad.");return;}
  io.execute(()->{try{String q=URLEncoder.encode(place.trim(),"UTF-8");JSONObject j=new JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?name="+q+"&count=5&language=es&format=json"));JSONArray a=j.optJSONArray("results");List<Place> out=new ArrayList<>();if(a!=null)for(int i=0;i<a.length();i++){JSONObject p=a.getJSONObject(i);out.add(new Place(p.optString("name"),p.optString("country"),p.getDouble("latitude"),p.getDouble("longitude")));}main.post(()->cb.complete(out,null));}
   catch(Exception e){main.post(()->cb.complete(null,"No pude localizar esa ciudad."));}});
 }
 private boolean validCoordinates(double lat,double lon){return Double.isFinite(lat)&&Double.isFinite(lon)&&lat>=-90&&lat<=90&&lon>=-180&&lon<=180;}
 public void close(){io.shutdownNow();}
}
