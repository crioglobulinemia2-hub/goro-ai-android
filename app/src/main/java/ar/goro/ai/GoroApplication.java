package ar.goro.ai;

import android.app.Application;
import com.jieli.bluetooth.bean.BluetoothOption;
import com.jieli.bluetooth.constant.BluetoothConstant;
import com.jieli.bluetooth.impl.rcsp.RCSPController;

/** Initializes the official JieLi RCSP stack once for the Android process. */
public final class GoroApplication extends Application {
 @Override public void onCreate(){
  super.onCreate();
  if(!RCSPController.isInit()){
   BluetoothOption option=BluetoothOption.createDefaultOption()
     .setPriority(BluetoothOption.PREFER_BLE)
     .setUseMultiDevice(true)
     .setMandatoryUseBLE(true)
     .setBleScanStrategy(BluetoothConstant.NONE_FILTER);
   RCSPController.init(this,option);
  }
 }
}
