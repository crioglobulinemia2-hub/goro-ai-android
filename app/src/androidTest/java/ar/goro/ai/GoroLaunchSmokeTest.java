package ar.goro.ai;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.core.app.ActivityScenario;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(AndroidJUnit4.class)
public class GoroLaunchSmokeTest {
 @Test public void appLaunchesAndPrimaryVoiceControlsAreVisible(){
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   scenario.onActivity(activity->{
    View root=activity.findViewById(android.R.id.content);
    assertNotNull(root);
    assertNotNull(findText(root,"GORO   ASISTENTE PERSONAL"));
    assertNotNull(findText(root,"Micrófono · Conversación continua"));
    assertNotNull(findText(root,"Enviar"));
    assertNotNull(findText(root,"Conectar ZN02 · GORO"));
   });
  }
 }
 @Test public void navigationChangesTheVisibleSection(){
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   scenario.onActivity(activity->{
    View root=activity.findViewById(android.R.id.content);
    TextView agenda=findText(root,"Agenda");
    assertNotNull(agenda);
    agenda.performClick();
    TextView output=findText(root,"Agenda. Consultá tus eventos, o pedile a GORO que los cree, cambie o elimine, con confirmación.");
    assertNotNull(output);
    assertEquals("Agenda. Consultá tus eventos, o pedile a GORO que los cree, cambie o elimine, con confirmación.",output.getText().toString());
   });
  }
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
