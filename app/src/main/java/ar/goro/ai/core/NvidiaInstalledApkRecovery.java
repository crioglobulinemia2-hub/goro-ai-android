package ar.goro.ai.core;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import java.io.File;
import java.io.FileInputStream;

/**
 * A user's previous GORO installation may contain its opt-in provisioning asset.
 * Android's app sandbox prohibits reading another package's encrypted preferences,
 * but installed APK code paths are normally readable after package visibility is granted.
 * Never copies credentials into the public APK, logs them, or bypasses keystore protection.
 */
public final class NvidiaInstalledApkRecovery {
 private static final String[] PREVIOUS_GORO_PACKAGES={
  "ar.goro.ai","ar.goro.ai.personal","ar.goro.ai.preview","ar.goro.ai.recovery",
  "ar.goro.ai.parallel","ar.goro.ai.safe20261108"
 };
private NvidiaInstalledApkRecovery(){}
 public static String recover(Context context){
  if(context==null)return null;
  PackageManager pm=context.getPackageManager();
  for(String name:PREVIOUS_GORO_PACKAGES){
   try{
    ApplicationInfo info=pm.getApplicationInfo(name,0);
    String path=info.sourceDir;
    if(path==null||!new File(path).isFile())continue;
    try(FileInputStream stream=new FileInputStream(path)){
     return NvidiaSetupImporter.readKey(stream);
    }
   }catch(Exception absentOrNoProvisioning){
    // A separate APK can never read the older app's Keystore-protected prefs.
    // Missing packages and provisioning assets are expected, not fatal.
   }
  }
  return null;
 }
}
