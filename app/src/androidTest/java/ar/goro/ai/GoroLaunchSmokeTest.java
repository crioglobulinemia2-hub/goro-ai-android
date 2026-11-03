package ar.goro.ai;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.core.app.ActivityScenario;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class GoroLaunchSmokeTest {
 @Test public void appLaunchesInConversationModeWithoutTechnicalButtonWall(){
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   scenario.onActivity(activity->{
    View root=activity.findViewById(android.R.id.content);
    assertNotNull(root);
    assertNotNull(findText(root,"GORO   ASISTENTE PERSONAL"));
    assertNotNull(findText(root,"Micrófono · Conversación continua"));
    assertNotNull(findText(root,"Enviar"));
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
