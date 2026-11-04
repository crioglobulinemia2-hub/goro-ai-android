package ar.goro.ai;

import android.Manifest;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
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
    assertTrue(findText(root,"AGENDA DEL DÍA").isShown());
    assertTrue(findText(root,"CLIMA LOCAL").isShown());
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
