package ar.goro.ai.ui;

import android.app.Activity;
import android.view.MotionEvent;
import android.graphics.drawable.RippleDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class GoroDashboardViewTest {
 private static final class Calls implements GoroDashboardView.Actions {
  int commands,link,gmail,calendar,unlink,weather,forecast,reminders,connect,inspect,lightOn,lightOff,lightRead,animation,nvidia;
  public void onCommand(String value){commands++;}
  public void onLinkAccount(){link++;}
  public void onReadGmail(){gmail++;}
  public void onCalendarPermission(){calendar++;}
  public void onUnlinkAccount(){unlink++;}
  public void onWeather(){weather++;}
  public void onForecast(){forecast++;}
  public void onReminders(){reminders++;}
  public void onConfigureNvidia(){nvidia++;}
  public void onTestNvidia(){nvidia++;}
  public void onConnectZn02(){connect++;}
  public void onInspectZn02(){inspect++;}
  public void onZn02LightOn(){lightOn++;}
  public void onZn02LightOff(){lightOff++;}
  public void onZn02ReadLight(){lightRead++;}
  public void onZn02AudioOn(){}
  public void onZn02AudioOff(){}
  public void onInstallGoroAnimation(){animation++;}
 }
 private Button findButton(View root,String label){
  if(root instanceof Button && label.contentEquals(((Button)root).getText()))return (Button)root;
  if(root instanceof ViewGroup){
   ViewGroup group=(ViewGroup)root;
   for(int i=0;i<group.getChildCount();i++){
    Button found=findButton(group.getChildAt(i),label);
    if(found!=null)return found;
   }
  }
  return null;
 }
 private android.widget.TextView findText(View root,String label){
  if(root instanceof android.widget.TextView && label.contentEquals(((android.widget.TextView)root).getText()))return (android.widget.TextView)root;
  if(root instanceof ViewGroup){
   ViewGroup group=(ViewGroup)root;
   for(int i=0;i<group.getChildCount();i++){
    android.widget.TextView found=findText(group.getChildAt(i),label);
    if(found!=null)return found;
   }
  }
  return null;
 }
 @Test public void homeHasFourSectionsAndOnlyQuickActions(){
  Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
  Calls calls=new Calls();
  GoroDashboardView dashboard=new GoroDashboardView(activity,calls,null,false);
  activity.setContentView(dashboard);
  assertEquals(4,dashboard.navigationItemCount());
  assertNotNull(findButton(dashboard,"Conversar"));
  assertNotNull(findButton(dashboard,"Explorar"));
  assertNotNull(findButton(dashboard,"Automatizar"));
  assertNotNull(findButton(dashboard,"Ajustes"));
  findButton(dashboard,"Agenda").performClick();
  findButton(dashboard,"Gmail").performClick();
  findButton(dashboard,"Dispositivos").performClick();
  findButton(dashboard,"Proyectos").performClick();
  assertEquals(1,calls.commands);
  assertEquals(1,calls.gmail);
  assertNull(findButton(dashboard,"Encender luz del ZN02"));
  assertNull(findButton(dashboard,"Examinar BLE ZN02 · solo lectura"));
  assertNull(findButton(dashboard,"Enviar animación GORO al visor ZN02"));
 }
 @Test public void deviceAndSettingsActionsAreSeparatedFromHome(){
  Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
  Calls calls=new Calls();
  GoroDashboardView dashboard=new GoroDashboardView(activity,calls,null,false);
  activity.setContentView(dashboard);
  findButton(dashboard,"Dispositivos").performClick();
  findButton(dashboard,"Conectar ZNP Z02").performClick();
  assertNull(findButton(dashboard,"Hablar con GORO"));
  assertNotNull(findText(dashboard,"Conversación manos libres automática cuando Z02 y NVIDIA estén conectados"));
  assertEquals(1,calls.connect);
  findButton(dashboard,"Ajustes").performClick();
  findButton(dashboard,"Vincular cuenta Google").performClick();
  findButton(dashboard,"Autorizar Calendar").performClick();
  findButton(dashboard,"Autorizar Gmail").performClick();
  findButton(dashboard,"Configurar NVIDIA").performClick();
  findButton(dashboard,"Verificar NVIDIA").performClick();
  assertEquals(1,calls.link);
  assertEquals(1,calls.calendar);
  assertEquals(1,calls.gmail);
  assertEquals(2,calls.nvidia);
 }
 @Test public void fullReplySurvivesNavigationAndRemainsReadable(){
  Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
  GoroDashboardView dashboard=new GoroDashboardView(activity,new Calls(),null,false);
  activity.setContentView(dashboard);
  String reply="Una respuesta larga que debe seguir disponible al volver de Ajustes.";
  dashboard.output.setText(reply);
  findButton(dashboard,"Ajustes").performClick();
  findButton(dashboard,"Conversar").performClick();
  assertEquals(reply,dashboard.output.getText().toString());
  android.widget.TextView bubble=findText(dashboard,reply);
  // The output is first in the page tree only after the home bubble.
  assertNotNull(bubble);
  assertEquals("Abrir respuesta completa de Goro",bubble.getContentDescription());
  bubble.performClick();
  assertEquals(View.VISIBLE,dashboard.output.getVisibility());
  assertTrue(dashboard.output.isTextSelectable());
 }
 @Test public void buttonsHaveVisiblePressedStateAndRestoreOnCancel(){
  Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
  GoroDashboardView dashboard=new GoroDashboardView(activity,new Calls(),null,false);
  activity.setContentView(dashboard);
  Button button=findButton(dashboard,"Conversar");
  assertNotNull(button);
  assertTrue(button.getElevation()>0f);
  assertTrue(button.getBackground() instanceof RippleDrawable);
  MotionEvent down=MotionEvent.obtain(0,0,MotionEvent.ACTION_DOWN,2f,2f,0);
  button.dispatchTouchEvent(down);down.recycle();
  assertEquals(0.965f,button.getScaleX(),0.001f);
  assertEquals(0.965f,button.getScaleY(),0.001f);
  MotionEvent cancel=MotionEvent.obtain(0,10,MotionEvent.ACTION_CANCEL,2f,2f,0);
  button.dispatchTouchEvent(cancel);cancel.recycle();
  assertTrue(button.isClickable());
  assertTrue(button.isFocusable());
 }
 @Test public void recognitionFailureRemainsVisibleAcrossMicrophoneAndNavigationChanges(){
  Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
  GoroDashboardView dashboard=new GoroDashboardView(activity,new Calls(),null,false);
  activity.setContentView(dashboard);
  String failure="Parakeet no completó la comprobación: UNAVAILABLE";
  dashboard.showVoiceStatus(failure);
  dashboard.setVoiceActive(false);
  findButton(dashboard,"Ajustes").performClick();
  assertEquals(failure,dashboard.state.getText().toString());
  assertEquals(failure,dashboard.state.getContentDescription().toString());
  assertNotNull(findVisibleText(dashboard,failure));
  dashboard.setVoiceActive(true);
  assertNotNull(findVisibleText(dashboard,failure));
  findButton(dashboard,"Conversar").performClick();
  assertTrue(dashboard.state.isShown());
  assertEquals(failure,dashboard.state.getText().toString());
 }
 @Test public void completedSpeechCheckDoesNotReturnToCheckingOrDisappearAfterCapture(){
  Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
  GoroDashboardView dashboard=new GoroDashboardView(activity,new Calls(),null,false);
  activity.setContentView(dashboard);
  String success="Diego y Parakeet verificados con una frase de diagnóstico.";
  dashboard.showVoiceStatus(success);
  dashboard.setVoiceActive(true);
  dashboard.setVoiceActive(false);
  assertEquals(success,dashboard.state.getText().toString());
  findButton(dashboard,"Ajustes").performClick();
  assertNotNull(findVisibleText(dashboard,success));
 }
 private android.widget.TextView findVisibleText(View root,String label){
  if(!root.isShown())return null;
  if(root instanceof android.widget.TextView && label.contentEquals(((android.widget.TextView)root).getText()))return (android.widget.TextView)root;
  if(root instanceof ViewGroup){
   ViewGroup group=(ViewGroup)root;
   for(int i=0;i<group.getChildCount();i++){
    android.widget.TextView found=findVisibleText(group.getChildAt(i),label);
    if(found!=null)return found;
   }
  }
  return null;
 }
}
