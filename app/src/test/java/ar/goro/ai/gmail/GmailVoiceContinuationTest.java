package ar.goro.ai.gmail;

import android.app.AlertDialog;
import android.os.Looper;
import android.widget.TextView;
import ar.goro.ai.MainActivity;
import ar.goro.ai.GoroZn02Controller;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.tasks.TaskCompletionSource;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
@LooperMode(LooperMode.Mode.PAUSED)
public class GmailVoiceContinuationTest {
 private MainActivity activity;
 private GoroGmailGateway gateway;
 private TextView output;
 private static Field field(Class<?> type,String name)throws Exception{
  Field f=type.getDeclaredField(name);f.setAccessible(true);return f;
 }
 private Object get(String name)throws Exception{return field(MainActivity.class,name).get(activity);}
 private void set(String name,Object value)throws Exception{field(MainActivity.class,name).set(activity,value);}
 private void call(String name,Class<?>[] types,Object... values)throws Exception{
  Method m=MainActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(activity,values);
 }
 @Before public void setup()throws Exception{
  // Do not run onCreate: no renderer, Bluetooth SDK startup, network or credentials.
  activity=Robolectric.buildActivity(MainActivity.class).get();
  output=new TextView(activity);activity.setContentView(output);
  set("output",output);set("state",new TextView(activity));
  set("foreground",true);set("voiceActive",true);
  set("zn02Controller",new GoroZn02Controller(RuntimeEnvironment.getApplication(),status->{}));
  gateway=new GoroGmailGateway(activity,request->new TaskCompletionSource<AuthorizationResult>().getTask());
  gateway.setSelectedAccount("owner@example.test");set("gmailGateway",gateway);
 }
 @After public void cleanup()throws Exception{call("onDestroy",new Class<?>[0]);}
 private void complete(String result,String error)throws Exception{
  long epoch=field(GoroGmailGateway.class,"pendingEpoch").getLong(gateway);
  assertTrue("The real Gmail operation must be waiting for a result",epoch>=0);
  Method m=GoroGmailGateway.class.getDeclaredMethod("complete",long.class,String.class,String.class);
  m.setAccessible(true);m.invoke(gateway,epoch,result,error);
 }
 private void seedMessage()throws Exception{
  GmailAccountState session=(GmailAccountState)field(GoroGmailGateway.class,"session").get(gateway);
  long epoch=session.begin();assertTrue(session.authorize(epoch,"owner@example.test","synthetic-token"));
  assertTrue(session.publish(epoch,Arrays.asList("selected-message")));
 }
 @Test public void inboxResultResumesListeningWithoutSendingMailToSpeech()throws Exception{
  call("runCommand",new Class<?>[]{String.class,boolean.class},"mis correos",true);assertNull(get("pendingVoiceRetry"));
  complete("Gmail · lista privada",null);
  assertNotNull(get("pendingVoiceRetry"));assertEquals("Gmail · lista privada",output.getText().toString());
  assertFalse((Boolean)get("speechInProgress"));assertNull(get("speechGateway"));
 }
 @Test public void searchErrorResumesListening()throws Exception{
  call("runCommand",new Class<?>[]{String.class,boolean.class},"buscá gmail reunión",true);
  complete(null,"No se concedió lectura de Gmail");
  assertNotNull(get("pendingVoiceRetry"));
  assertTrue(output.getText().toString().contains("No se concedió lectura"));
  assertFalse((Boolean)get("speechInProgress"));
 }
 @Test public void screenInitiatedInboxDoesNotInterfereWithAnUnrelatedVoiceTurn()throws Exception{
  call("readGmail",new Class<?>[0]);
  complete("Gmail · resultado del botón",null);
  assertNull(get("pendingVoiceRetry"));assertTrue((Boolean)get("voiceActive"));
 }
 @Test public void screenOnlyMessageAndDeclinedSpeechResumeTheSameConversation()throws Exception{
  seedMessage();call("runCommand",new Class<?>[]{String.class,boolean.class},"mostrame correo 1",true);
  complete("Contenido privado",null);assertNotNull(get("pendingVoiceRetry"));
  call("cancelVoiceRetry",new Class<?>[0]);
  seedMessage();call("runCommand",new Class<?>[]{String.class,boolean.class},"leeme correo 1",true);
  complete("Otro contenido privado",null);
  assertNull(get("pendingVoiceRetry"));assertFalse((Boolean)get("speechInProgress"));
  AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();assertNotNull(dialog);
  dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();shadowOf(Looper.getMainLooper()).idle();
  assertNotNull(get("pendingVoiceRetry"));assertFalse((Boolean)get("speechInProgress"));
 }
 @Test public void delayedGmailResultCannotRestartANewerVoiceSession()throws Exception{
  call("runCommand",new Class<?>[]{String.class,boolean.class},"mis correos",true);call("onStop",new Class<?>[0]);
  set("foreground",true);set("voiceActive",true);
  complete("Resultado anterior",null);
  assertNull(get("pendingVoiceRetry"));assertTrue((Boolean)get("voiceActive"));
 }
 @Test public void explicitSpeechConsentKeepsTheWholeMessageForChunkedSynthesis()throws Exception{
  seedMessage();call("runCommand",new Class<?>[]{String.class,boolean.class},"leeme correo 1",true);
  String message="Contenido "+String.join("",java.util.Collections.nCopies(1700,"x"))+" FIN";
  complete(message,null);
  AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();assertNotNull(dialog);
  dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();shadowOf(Looper.getMainLooper()).idle();
  // No gateway is installed in this test. say() displays the entire message and
  // reports missing speech instead of making any external request.
  assertTrue(output.getText().toString().startsWith(message));
 }
}
