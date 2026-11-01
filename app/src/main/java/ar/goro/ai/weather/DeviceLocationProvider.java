package ar.goro.ai.weather;
import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import androidx.core.content.ContextCompat;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.annotation.SuppressLint;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
/** One-shot foreground location: GPS first, network-based location as fallback. */
public final class DeviceLocationProvider {
 public interface Callback {void complete(Location location,String error);}
 private final Context context;private final LocationManager manager;private final Handler main=new Handler(Looper.getMainLooper());
 private final Executor mainExecutor=command->main.post(command);
 public DeviceLocationProvider(Context c){context=c.getApplicationContext();manager=(LocationManager)context.getSystemService(Context.LOCATION_SERVICE);}
 public boolean hasPermission(){return ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED||ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED;}
 @SuppressLint("MissingPermission")
 public void current(Callback callback){
  boolean fine=ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;
  if(!fine&&ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED){callback.complete(null,"GORO necesita permiso de ubicación para consultar el clima local.");return;}
  String first=fine?LocationManager.GPS_PROVIDER:LocationManager.NETWORK_PROVIDER;
  String fallback=LocationManager.NETWORK_PROVIDER.equals(first)?null:LocationManager.NETWORK_PROVIDER;
  if(manager==null){callback.complete(null,"El dispositivo no ofrece un servicio de ubicación.");return;}
  if(Build.VERSION.SDK_INT>=30)requestModern(first,fallback,callback);else requestLegacy(first,fallback,callback);
 }
 @SuppressLint("MissingPermission")
 private void requestModern(String provider,String fallback,Callback callback){
  if(!enabled(provider)){if(fallback!=null)requestModern(fallback,null,callback);else callback.complete(null,"Activá la ubicación del dispositivo para consultar el clima.");return;}
  AtomicBoolean settled=new AtomicBoolean(false);CancellationSignal signal=new CancellationSignal();
  Runnable timeout=()->{if(settled.compareAndSet(false,true)){signal.cancel();if(fallback!=null)requestModern(fallback,null,callback);else callback.complete(null,"No pude obtener una ubicación actual. Verificá el GPS y la señal.");}};
  main.postDelayed(timeout,15000);
  try{manager.getCurrentLocation(provider,signal,mainExecutor,location->{
   if(location!=null&&settled.compareAndSet(false,true)){main.removeCallbacks(timeout);callback.complete(location,null);}
   else if(location==null&&settled.compareAndSet(false,true)){main.removeCallbacks(timeout);if(fallback!=null)requestModern(fallback,null,callback);else callback.complete(null,"No pude obtener una ubicación actual. Verificá el GPS y la señal.");}
  });}catch(SecurityException e){main.removeCallbacks(timeout);if(settled.compareAndSet(false,true))callback.complete(null,"Android no concedió acceso a la ubicación.");}
 }
 @SuppressWarnings("deprecation")
 @SuppressLint("MissingPermission")
 private void requestLegacy(String provider,String fallback,Callback callback){
  if(!enabled(provider)){if(fallback!=null)requestLegacy(fallback,null,callback);else callback.complete(null,"Activá la ubicación del dispositivo para consultar el clima.");return;}
  AtomicBoolean settled=new AtomicBoolean(false);
  LocationListener listener=new LocationListener(){
   @Override public void onLocationChanged(Location location){if(settled.compareAndSet(false,true)){try{manager.removeUpdates(this);}catch(Exception ignored){}callback.complete(location,null);}}
   @Override public void onProviderEnabled(String p){}
   @Override public void onProviderDisabled(String p){}
   @Override public void onStatusChanged(String p,int status,android.os.Bundle extras){}
  };
  Runnable timeout=()->{if(settled.compareAndSet(false,true)){try{manager.removeUpdates(listener);}catch(Exception ignored){}if(fallback!=null)requestLegacy(fallback,null,callback);else callback.complete(null,"No pude obtener una ubicación actual. Verificá el GPS y la señal.");}};
  try{manager.requestSingleUpdate(provider,listener,Looper.getMainLooper());main.postDelayed(timeout,15000);}
  catch(Exception e){main.removeCallbacks(timeout);if(settled.compareAndSet(false,true)){if(fallback!=null)requestLegacy(fallback,null,callback);else callback.complete(null,"No pude iniciar la ubicación del dispositivo.");}}
 }
 private boolean enabled(String provider){try{return manager!=null&&manager.isProviderEnabled(provider);}catch(Exception e){return false;}}
}
