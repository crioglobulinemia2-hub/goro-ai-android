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
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class GoroDashboardViewTest {
 private static final class Calls implements GoroDashboardView.Actions {
  int commands,link,gmail,calendar,unlink,weather,forecast,reminders,connect,inspect,lightOn,lightOff,lightRead,animation,nvidia,toggle,disconnect,importNvidia; String lastCommand; java.util.List<String> models=new java.util.ArrayList<>(); java.util.List<String> animationModels=new java.util.ArrayList<>();
  public void onCommand(String value){commands++;lastCommand=value;}
  public void onToggleListening(){toggle++;}
  public void onLinkAccount(){link++;}
  public void onReadGmail(){gmail++;}
  public void onCalendarPermission(){calendar++;}
  public void onUnlinkAccount(){unlink++;}
  public void onWeather(){weather++;}
  public void onForecast(){forecast++;}
  public void onReminders(){reminders++;}
  public void onConfigureNvidia(){nvidia++;}
  public void onTestNvidia(){nvidia++;}
  public void onImportNvidiaConfig(){importNvidia++;}
  public void onConnectZn02(){connect++;}
  public void onConnectZnpModel(String model){models.add(model);}
  public void onDisconnectZnp(){disconnect++;}
  public void onInspectZn02(){inspect++;}
  public void onZn02LightOn(){lightOn++;}
  public void onZn02LightOff(){lightOff++;}
  public void onZn02ReadLight(){lightRead++;}
  public void onZn02AudioOn(){}
  public void onZn02AudioOff(){}
  public void onInstallGoroAnimation(){animation++;}
  public void onInstallGoroAnimation(String model){animationModels.add(model);}
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
 private android.widget.EditText findEditText(View root){
  if(root instanceof android.widget.EditText)return (android.widget.EditText)root;
  if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++){android.widget.EditText found=findEditText(group.getChildAt(i));if(found!=null)return found;}}
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
 @Test public void listeningControlStaysVisibleAcrossSectionsAndCallsRealToggle(){
  Activity activity=Robolectric.buildActivity(Activity.class).setup().get();Calls calls=new Calls();
  GoroDashboardView dashboard=new GoroDashboardView(activity,calls,null,false);activity.setContentView(dashboard);
  dashboard.setListeningPaused(true);Button control=findButton(dashboard,"Activar escucha");assertNotNull(control);assertTrue(control.isShown());
  control.performClick();assertEquals(1,calls.toggle);
  dashboard.setListeningPaused(false);
  for(String section:new String[]{"Ajustes","Conversar","Dispositivos","Conversar","Gmail"}){
   findButton(dashboard,section).performClick();Button pause=findButton(dashboard,"Pausar escucha");assertNotNull(pause);assertTrue(pause.isShown());
  }
  findButton(dashboard,"Pausar escucha").performClick();assertEquals(2,calls.toggle);
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
  findButton(dashboard,"Conectar AZ02 / Z02").performClick();
  assertNull(findButton(dashboard,"Hablar con GORO"));
  assertNotNull(findText(dashboard,"Activá o pausá la escucha con el botón superior. La pausa se mantiene al reconectar."));
  assertEquals(java.util.Arrays.asList("AZ02"),calls.models);
  findButton(dashboard,"Ajustes").performClick();
  findButton(dashboard,"Vincular cuenta Google").performClick();
  findButton(dashboard,"Autorizar Calendar").performClick();
  findButton(dashboard,"Autorizar Gmail").performClick();
  findButton(dashboard,"Configurar NVIDIA").performClick();
  findButton(dashboard,"Importar NVIDIA desde GORO 1.2.4").performClick();
  findButton(dashboard,"Verificar NVIDIA").performClick();
  assertEquals(1,calls.importNvidia);
  assertEquals(1,calls.link);
  assertEquals(1,calls.calendar);
  assertEquals(1,calls.gmail);
  assertEquals(2,calls.nvidia);
 }
 @Test public void allThreeZnpModelsHaveSeparateConnectAndAnimationActions(){
  Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
  Calls calls=new Calls();
  GoroDashboardView dashboard=new GoroDashboardView(activity,calls,null,false);
  activity.setContentView(dashboard);
  findButton(dashboard,"Dispositivos").performClick();
  for(String model:new String[]{"AN02","AZ02","AZ01"}){
   findButton(dashboard,"Conectar "+model+" / "+(model.equals("AN02")?"N02":model.equals("AZ02")?"Z02":"Z01")).performClick();
   String animationLabel="Instalar animación GORO en "+model+" / "+(model.equals("AN02")?"N02":model.equals("AZ02")?"Z02":"Z01");

   findButton(dashboard,animationLabel).performClick();
  }
  findButton(dashboard,"Desconectar dispositivo ZNP").performClick();
  assertEquals(java.util.Arrays.asList("AN02","AZ02","AZ01"),calls.models);
  assertEquals(java.util.Arrays.asList("AN02","AZ02","AZ01"),calls.animationModels);
  assertEquals(1,calls.disconnect);
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
 @Test public void homeOverviewDisplaysActualUpdatesAndKeepsActionsAvailable(){
  Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
  Calls calls=new Calls();
  GoroDashboardView dashboard=new GoroDashboardView(activity,calls,null,false);
  activity.setContentView(dashboard);
  String agenda="14:30 Reunión\n16:00 Llamada";
  dashboard.showAgenda(agenda);
  dashboard.showWeather("18 °C · Parcialmente nublado");
  dashboard.showDeviceStatus("Z02 desconectado");
  assertNotNull(findVisibleText(dashboard,agenda));
  android.widget.TextView climate=findVisibleText(dashboard,"18 °C · Parcialmente nublado");
  assertNotNull(climate);
  assertNotNull(findVisibleText(dashboard,"Z02 desconectado"));
  ((View)climate.getParent()).performClick();
  assertEquals(1,calls.weather);
  ((View)findVisibleText(dashboard,agenda).getParent()).performClick();
  assertEquals(1,calls.commands);
  assertNotNull(findVisibleText(dashboard,agenda));
 }
 @Test public void replyFromSubpageActionIsVisibleWithoutReturningHome(){
  Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
  GoroDashboardView dashboard=new GoroDashboardView(activity,new Calls(),null,false);
  activity.setContentView(dashboard);
  findButton(dashboard,"Proyectos").performClick();
  findButton(dashboard,"Consultar memoria").performClick();
  String reply="Recordás que tu próxima reunión es el jueves a las 9.";
  dashboard.output.setText(reply);
  assertTrue(dashboard.output.isShown());
  assertNotNull(findVisibleText(dashboard,reply));
  assertTrue(dashboard.output.isTextSelectable());
 }
 @Test public void internetSearchIsReachableFromExplore(){
  Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
  Calls calls=new Calls();
  GoroDashboardView dashboard=new GoroDashboardView(activity,calls,null,false);
  activity.setContentView(dashboard);
  findButton(dashboard,"Explorar").performClick();
  Button search=findButton(dashboard,"Buscar en internet");
  assertNotNull(search);
  search.performClick();
  assertNotNull(org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog());
 }

 @Test public void deviceSummaryDoesNotTurnUnverifiedTransportIntoConnectedState(){
  Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
  GoroDashboardView dashboard=new GoroDashboardView(activity,new Calls(),null,false);
  activity.setContentView(dashboard);
  String connecting="Enlace BLE ZNP Z02 abierto. Verificando autenticación RCSP…";
  dashboard.showDeviceStatus(connecting);
  android.widget.TextView summary=findVisibleText(dashboard,"Z02 · verificando conexión…");
  assertNotNull(summary);
  assertEquals(connecting,summary.getContentDescription());
  assertNull(findVisibleText(dashboard,"Z02 · conexión verificada"));
  findButton(dashboard,"Dispositivos").performClick();
  assertNotNull(findVisibleText(dashboard,connecting));
  String failure="El SDK no completó la autenticación de ZNP Z02 en 20 segundos. Volvé a conectar el dispositivo.";
  dashboard.showDeviceStatus(failure);
  findButton(dashboard,"Conversar").performClick();
  assertNotNull(findVisibleText(dashboard,failure));
 }
 @Test @Config(qualifiers="w640dp-h360dp-land-mdpi")
 public void shortLandscapeKeepsSceneAndActionsReachableByScrolling(){
  Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
  GoroDashboardView dashboard=new GoroDashboardView(activity,new Calls(),null,false);
  activity.setContentView(dashboard);
  dashboard.measure(View.MeasureSpec.makeMeasureSpec(640,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY));
  dashboard.layout(0,0,640,360);
  View scene=(View)dashboard.avatarStage.getParent();
  assertTrue(scene.getParent() instanceof android.widget.ScrollView);
  android.widget.ScrollView viewport=(android.widget.ScrollView)scene.getParent();
  assertTrue(scene.getHeight()>=460);
  assertTrue(dashboard.avatarStage.getHeight()>=250);
  assertTrue(scene.getHeight()>viewport.getHeight());
  viewport.scrollTo(0,scene.getHeight());
  assertTrue(viewport.getScrollY()>0);
  android.graphics.Rect visible=new android.graphics.Rect();
  assertTrue(findButton(dashboard,"Gmail").getLocalVisibleRect(visible));
 }
 @Test @Config(qualifiers="w400dp-h800dp-port-mdpi")
 public void tallPortraitFillsViewportWithoutLettingRoomImagePushControlsBelowIt(){
  Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
  GoroDashboardView dashboard=new GoroDashboardView(activity,new Calls(),null,false);
  activity.setContentView(dashboard);
  dashboard.measure(View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(800,View.MeasureSpec.EXACTLY));
  dashboard.layout(0,0,400,800);
  View scene=(View)dashboard.avatarStage.getParent();
  android.widget.ScrollView viewport=(android.widget.ScrollView)scene.getParent();
  assertEquals("Scene should fill a tall viewport without unnecessary scrolling",viewport.getHeight(),scene.getHeight());
  assertEquals(0,viewport.getScrollY());
  android.graphics.Rect visible=new android.graphics.Rect();
  assertTrue("Connection status must be visible without scrolling",dashboard.state.getLocalVisibleRect(visible));
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
