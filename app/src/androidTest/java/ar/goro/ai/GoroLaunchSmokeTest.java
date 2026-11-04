package ar.goro.ai;

import android.Manifest;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.SystemClock;
import ar.goro.ai.avatar.GoroAvatarView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.core.app.ActivityScenario;
import org.junit.Test;
import org.junit.Before;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class GoroLaunchSmokeTest {
 @Before public void grantRuntimePermissions(){
  android.app.UiAutomation automation=InstrumentationRegistry.getInstrumentation().getUiAutomation();
  String packageName="ar.goro.ai.personal";
  for(String permission:new String[]{
    Manifest.permission.BLUETOOTH_SCAN,
    Manifest.permission.BLUETOOTH_CONNECT,
    Manifest.permission.RECORD_AUDIO,
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION
  }){
   try{automation.grantRuntimePermission(packageName,permission);}catch(Exception ignored){}
  }
 }
 @Test public void appLaunchesInConversationModeWithoutTechnicalButtonWall(){
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   scenario.onActivity(activity->{
    View root=activity.findViewById(android.R.id.content);
    assertNotNull(root);
    assertNotNull(findText(root,"GORO IA"));
    assertNotNull(findText(root,"GORO IA · Conversación manos libres por ZNP Z02. La conexión se inicia automáticamente."));
    assertNotNull(findText(root,"Z02 · esperando conexión automática"));
    assertNotNull(findText(root,"Conversar"));
    assertNotNull(findText(root,"Explorar"));
    assertNotNull(findText(root,"Automatizar"));
    assertNotNull(findText(root,"Ajustes"));
    assertNotNull(findText(root,"Dispositivos"));
    assertEquals(4,countVisibleNavigation(root));
   });
  }
 }
 @Test public void navigationSeparatesDeviceAndSettingsControls(){
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   scenario.onActivity(activity->{
    View root=activity.findViewById(android.R.id.content);
    TextView device=findText(root,"Dispositivos");
    assertNotNull(device);
    device.performClick();
    TextView connect=findText(root,"Conectar ZNP Z02");
    assertNotNull(connect);
    assertTrue(connect.isShown());
    TextView settings=findText(root,"Ajustes");
    assertNotNull(settings);
    settings.performClick();
    TextView nvidia=findText(root,"Configurar NVIDIA");
    assertNotNull(nvidia);
    assertTrue(nvidia.isShown());
    assertFalse(connect.isShown());
   });
  }
 }
 @Test public void nativeAvatarRendersWithTransparentEdgesOverTheRoom(){
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   final GoroAvatarView[] avatar=new GoroAvatarView[1];
   final int[] bounds=new int[4];
   scenario.onActivity(activity->{
    avatar[0]=findAvatar(activity.findViewById(android.R.id.content));
    assertNotNull("The actual native avatar must be present",avatar[0]);
   });
   SystemClock.sleep(1600);
   scenario.onActivity(activity->{
    int[] position=new int[2];avatar[0].getLocationOnScreen(position);
    bounds[0]=position[0];bounds[1]=position[1];
    bounds[2]=avatar[0].getWidth();bounds[3]=avatar[0].getHeight();
    assertTrue(bounds[2]>40&&bounds[3]>40);
    avatar[0].setVisibility(View.INVISIBLE);
   });
   SystemClock.sleep(200);
   Bitmap room=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
   assertNotNull(room);
   scenario.onActivity(activity->avatar[0].setVisibility(View.VISIBLE));
   SystemClock.sleep(1000);
   Bitmap rendered=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
   assertNotNull(rendered);
   // The left edge is outside the full-height model and the right-hand cards.
   // It must retain the underlying room, not an opaque Vulkan clear rectangle.
   for(float fraction:new float[]{.35f,.45f,.55f}){
    int y=bounds[1]+Math.round(bounds[3]*fraction);
    for(int offset:new int[]{3,6,9}){
     int x=bounds[0]+offset;
     assertTrue("Native surface must preserve room pixels at "+x+","+y,
       colorDistance(room.getPixel(x,y),rendered.getPixel(x,y))<=24);
    }
   }
   int changed=0;
   for(int y=bounds[1];y<bounds[1]+bounds[3];y+=6){
    for(int x=bounds[0];x<bounds[0]+bounds[2];x+=6){
     if(colorDistance(room.getPixel(x,y),rendered.getPixel(x,y))>45)changed++;
    }
   }
   assertTrue("Transparent surface must also draw the actual robot",changed>100);
   room.recycle();rendered.recycle();
  }
 }
 private static int colorDistance(int a,int b){
  return Math.abs(Color.red(a)-Color.red(b))+Math.abs(Color.green(a)-Color.green(b))+Math.abs(Color.blue(a)-Color.blue(b));
 }
 private static GoroAvatarView findAvatar(View view){
  if(view instanceof GoroAvatarView)return (GoroAvatarView)view;
  if(view instanceof ViewGroup){
   ViewGroup group=(ViewGroup)view;
   for(int i=0;i<group.getChildCount();i++){
    GoroAvatarView found=findAvatar(group.getChildAt(i));if(found!=null)return found;
   }
  }
  return null;
 }
 private int countVisibleNavigation(View root){
  int count=0;
  for(String label:new String[]{"Conversar","Explorar","Automatizar","Ajustes"}){
   TextView value=findText(root,label);
   if(value!=null&&value.isShown())count++;
  }
  return count;
 }
 private static TextView findText(View view,String expected){
  if(view instanceof TextView){
   CharSequence text=((TextView)view).getText();
   if(text!=null&&expected.contentEquals(text))return (TextView)view;
  }
  if(view instanceof ViewGroup){
   ViewGroup group=(ViewGroup)view;
   for(int i=0;i<group.getChildCount();i++){
    TextView found=findText(group.getChildAt(i),expected);
    if(found!=null)return found;
   }
  }
  return null;
 }
}
