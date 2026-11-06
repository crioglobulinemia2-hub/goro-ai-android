package ar.goro.ai;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

/** Pairs the authenticated Z02 classic-audio endpoint before A2DP routing. */
@SuppressLint("MissingPermission")
public final class BluetoothBondSession {
 private final Context app;
 private final Handler main=new Handler(Looper.getMainLooper());
 private BroadcastReceiver receiver;
 private Runnable timeout;
 private long generation;
 public BluetoothBondSession(Context context){app=context.getApplicationContext();}
 public void start(BluetoothDevice device,Runnable connected,java.util.function.Consumer<String> report){
  if(Looper.myLooper()!=Looper.getMainLooper())throw new IllegalStateException("Bluetooth bond must run on main thread");
  close();
  final long id=generation;
  final String address=device.getAddress();
  Runnable done=()->{if(id!=generation)return;close();connected.run();};
  try{
   if(device.getBondState()==BluetoothDevice.BOND_BONDED){done.run();return;}
   BroadcastReceiver listener=new BroadcastReceiver(){
    @Override public void onReceive(Context context,Intent intent){
     if(intent==null||!BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(intent.getAction()))return;
     BluetoothDevice source;
     if(Build.VERSION.SDK_INT>=33)source=intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE,BluetoothDevice.class);
     else { @SuppressWarnings("deprecation") BluetoothDevice old=intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);source=old; }
     if(source==null||!address.equalsIgnoreCase(source.getAddress())||id!=generation)return;
     int state=intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE,BluetoothDevice.ERROR);
     if(state==BluetoothDevice.BOND_BONDED)done.run();
     else if(state==BluetoothDevice.BOND_NONE&&intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE,0)==BluetoothDevice.BOND_BONDING){
      close();report.accept("Android rechazó el emparejamiento de audio del dispositivo ZNP.");
     }
    }
   };
   IntentFilter filter=new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
   if(Build.VERSION.SDK_INT>=33)app.registerReceiver(listener,filter,Context.RECEIVER_EXPORTED);
   else { @SuppressWarnings("deprecation") int ignored=0;app.registerReceiver(listener,filter); }
   receiver=listener;
   timeout=()->{if(id==generation){close();report.accept("El emparejamiento de audio del Z02 no terminó en 45 segundos.");}};
   main.postDelayed(timeout,45000);
   int state=device.getBondState();
   if(state==BluetoothDevice.BOND_BONDED)done.run();
   else if(state==BluetoothDevice.BOND_BONDING)report.accept("Esperando el emparejamiento Bluetooth de audio del Z02…");
   else if(device.createBond())report.accept("Android está emparejando el perfil de audio del dispositivo ZNP…");
   else {close();report.accept("Android no pudo iniciar el emparejamiento de audio del Z02.");}
  }catch(Exception error){close();report.accept("No se pudo preparar el audio Bluetooth del Z02: "+error.getClass().getSimpleName());}
 }
 public void close(){
  if(Looper.myLooper()!=Looper.getMainLooper()){main.post(this::close);return;}
  ++generation;
  if(timeout!=null)main.removeCallbacks(timeout);
  timeout=null;
  if(receiver!=null){try{app.unregisterReceiver(receiver);}catch(Exception ignored){}}
  receiver=null;
 }
}
