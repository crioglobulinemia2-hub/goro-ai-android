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
  int commands,voice,link,calendar,unlink,weather,forecast,reminders,connect,lightOn,lightOff,lightRead,animation;
  public void onCommand(String value){commands++;}
  public void onVoiceSession(){voice++;}
  public void onLinkAccount(){link++;}
  public void onReadGmail(){}
  public void onCalendarPermission(){calendar++;}
  public void onUnlinkAccount(){unlink++;}
  public void onWeather(){weather++;}
  public void onForecast(){forecast++;}
  public void onReminders(){reminders++;}
  public void onConfigureNvidia(){}
  public void onTestNvidia(){}
  public void onConnectZn02(){connect++;}
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
 @Test public void exposesEightSectionsThreeThemesAndConnectedActions(){
  Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
  Calls calls=new Calls();
  GoroDashboardView dashboard=new GoroDashboardView(activity,calls,null,false);
  activity.setContentView(dashboard);
  assertEquals(8,dashboard.navigationItemCount());
  assertEquals(3,((ViewGroup)dashboard.getChildAt(2)).getChildCount());
  findButton(dashboard,"Micrófono · Conversación continua").performClick();
  findButton(dashboard,"Enviar").performClick();
  findButton(dashboard,"Consultar GPS").performClick();
  findButton(dashboard,"Pronóstico · 3 días").performClick();
  findButton(dashboard,"Vincular cuenta Google").performClick();
  findButton(dashboard,"Autorizar Google Calendar").performClick();
  findButton(dashboard,"Desvincular cuenta").performClick();
  findButton(dashboard,"Conectar ZN02 · GORO").performClick();
  findButton(dashboard,"Encender luz del ZN02").performClick();
  findButton(dashboard,"Apagar luz del ZN02").performClick();
  findButton(dashboard,"Consultar estado del ZN02").performClick();
  findButton(dashboard,"Enviar animación GORO al visor ZN02").performClick();
  assertEquals(1,calls.voice);
  assertEquals(1,calls.commands);
  assertEquals(1,calls.weather);
  assertEquals(1,calls.forecast);
  assertEquals(1,calls.link);
  assertEquals(1,calls.calendar);
  assertEquals(1,calls.unlink);
  assertEquals(1,calls.connect);
  assertEquals(1,calls.lightOn);
  assertEquals(1,calls.lightOff);
  assertEquals(1,calls.lightRead);
  assertEquals(1,calls.animation);
 }
 @Test public void buttonsHaveVisiblePressedStateAndRestoreOnCancel(){
  Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
  GoroDashboardView dashboard=new GoroDashboardView(activity,new Calls(),null,false);
  activity.setContentView(dashboard);
  Button button=findButton(dashboard,"Inicio");
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
}
