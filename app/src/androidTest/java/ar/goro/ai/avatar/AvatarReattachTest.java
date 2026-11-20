package ar.goro.ai.avatar;

import android.Manifest;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.SystemClock;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import ar.goro.ai.MainActivity;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Exercises the native renderer, which is unavailable in JVM/Robolectric. */
@RunWith(AndroidJUnit4.class)
public class AvatarReattachTest {
 @Before public void grantRuntimePermissions(){
  android.app.Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
  String packageName=instrumentation.getTargetContext().getPackageName();
  for(String permission:new String[]{Manifest.permission.BLUETOOTH_SCAN,
    Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.RECORD_AUDIO,
    Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION}){
   instrumentation.getUiAutomation().grantRuntimePermission(packageName,permission);
  }
 }
 @Test public void sameAvatarRendersAfterRepeatedRemovalAndReattachment(){
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   GoroAvatarView[] avatar=new GoroAvatarView[1];
   scenario.onActivity(activity->{
    avatar[0]=find(activity.findViewById(android.R.id.content),GoroAvatarView.class);
    assertNotNull(avatar[0]);
   });
   for(int cycle=0;cycle<2;cycle++){
    // Let the initial surface and the preceding replacement finish attaching.
    SystemClock.sleep(1200);
    scenario.onActivity(activity->{
     ViewGroup parent=(ViewGroup)avatar[0].getParent();
     int index=parent.indexOfChild(avatar[0]);
     ViewGroup.LayoutParams layout=avatar[0].getLayoutParams();
     parent.removeView(avatar[0]);
     parent.addView(avatar[0],index,layout);
    });
    SystemClock.sleep(1600);
    scenario.onActivity(activity->{
     TextureView surface=find(avatar[0],TextureView.class);
     assertNotNull("Reattached avatar must have a render surface",surface);
     assertTrue("Reattached surface must become available",surface.isAvailable());
     Bitmap pixels=surface.getBitmap(96,96);
     assertNotNull(pixels);
     try{
      int painted=0;
      for(int y=0;y<pixels.getHeight();y++)for(int x=0;x<pixels.getWidth();x++){
       int color=pixels.getPixel(x,y);
       if(Color.alpha(color)>64&&Color.red(color)+Color.green(color)+Color.blue(color)>60)painted++;
      }
      assertTrue("Robot must render again after its previous engine was destroyed",painted>100);
     }finally{pixels.recycle();}
    });
   }
  }
 }

 @Test public void rendererCanBeReleasedBeforeDetachWithoutDestroyingItsEngineTwice(){
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   scenario.onActivity(activity->{
    android.widget.FrameLayout root=activity.findViewById(android.R.id.content);
    TextureView surface=new TextureView(activity);
    root.addView(surface,new android.widget.FrameLayout.LayoutParams(64,64));
    com.google.android.filament.Engine engine=com.google.android.filament.Engine.create(
        com.google.android.filament.Engine.Backend.VULKAN);
    com.google.android.filament.android.UiHelper helper=new com.google.android.filament.android.UiHelper(
        com.google.android.filament.android.UiHelper.ContextErrorPolicy.DONT_CHECK);
    com.google.android.filament.utils.GoroModelViewer renderer=
        new com.google.android.filament.utils.GoroModelViewer(surface,engine,helper,null);
    renderer.destroy();
    renderer.destroy();
    renderer.render(System.nanoTime());
    // Detaching dispatches the original cleanup listener after explicit cleanup,
    // matching a model-load failure followed by leaving the activity.
    root.removeView(surface);
    assertNull(renderer.getAsset());
   });
  }
 }

 private static <T extends View>T find(View view,Class<T> type){
  if(type.isInstance(view))return type.cast(view);
  if(view instanceof ViewGroup){
   ViewGroup group=(ViewGroup)view;
   for(int i=0;i<group.getChildCount();i++){
    T value=find(group.getChildAt(i),type);
    if(value!=null)return value;
   }
  }
  return null;
 }
}
