package ar.goro.ai.gmail;

import android.content.Context;
import android.os.Looper;
import android.widget.TextView;
import ar.goro.ai.MainActivity;
import ar.goro.ai.calendar.DeviceCalendarGateway;
import ar.goro.ai.voice.Z02Player;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
@LooperMode(LooperMode.Mode.PAUSED)
public class GmailUnlinkConversationTest {
 private static Field field(Class<?> type,String name)throws Exception{
  Field f=type.getDeclaredField(name);f.setAccessible(true);return f;
 }
 private static byte[] wav(){
  ByteBuffer b=ByteBuffer.allocate(364).order(ByteOrder.LITTLE_ENDIAN);
  b.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(356);
  b.put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16);
  b.putShort((short)1).putShort((short)1).putInt(16000).putInt(32000).putShort((short)2).putShort((short)16);
  b.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(320);return b.array();
 }
 @Test public void unlinkImmediatelyStopsPendingMailPlaybackWhileRemoteRevocationIsUnresolved()throws Exception{
  // Exercise the action handler without onCreate: no renderer, credentials,
  // Bluetooth connection, real Google consent, or network request is started.
  MainActivity activity=Robolectric.buildActivity(MainActivity.class).get();
  Context context=RuntimeEnvironment.getApplication();
  context.getSharedPreferences("goro_calendar_link",Context.MODE_PRIVATE).edit().clear().commit();
  DeviceCalendarGateway calendar=new DeviceCalendarGateway(context);
  calendar.setSelectedGoogleAccount("owner@example.test");
  TaskCompletionSource<Void> remote=new TaskCompletionSource<>();
  AtomicInteger revocations=new AtomicInteger(),playbackErrors=new AtomicInteger();
  GoroGmailGateway gmail=new GoroGmailGateway(activity,request->new TaskCompletionSource<AuthorizationResult>().getTask(),
    token->Tasks.forResult(null),(url,token,ticket)->{throw new AssertionError("No HTTP allowed");},
    request->{revocations.incrementAndGet();return remote.getTask();});
  gmail.setSelectedAccount("owner@example.test");
  Z02Player player=new Z02Player(context,new Z02Player.Listener(){
   public void onState(String status){}public void onError(String error){playbackErrors.incrementAndGet();}public void onCompleted(){}
  });
  try{
   TextView output=new TextView(activity);output.setText("Contenido privado anterior");
   field(MainActivity.class,"output").set(activity,output);
   field(MainActivity.class,"state").set(activity,new TextView(activity));
   field(MainActivity.class,"calendarAccountLabel").set(activity,new TextView(activity));
   field(MainActivity.class,"calendarGateway").set(activity,calendar);
   field(MainActivity.class,"gmailGateway").set(activity,gmail);
   field(MainActivity.class,"z02Player").set(activity,player);
   field(MainActivity.class,"foreground").setBoolean(activity,true);
   field(MainActivity.class,"voiceActive").setBoolean(activity,true);
   field(MainActivity.class,"speechInProgress").setBoolean(activity,true);
   field(MainActivity.class,"pendingSpeechWav").set(activity,wav());
   Deque<String> chunks=(Deque<String>)field(MainActivity.class,"speechChunks").get(activity);chunks.add("Resto del correo privado");
   player.playWav(wav());assertNotNull(field(Z02Player.class,"pendingStart").get(player));
   Method unlink=MainActivity.class.getDeclaredMethod("unlinkGoogleAccount");unlink.setAccessible(true);unlink.invoke(activity);
   assertEquals(1,revocations.get());assertFalse(remote.getTask().isComplete());
   assertNull(calendar.selectedGoogleAccount());
   assertFalse(field(MainActivity.class,"voiceActive").getBoolean(activity));
   assertFalse(field(MainActivity.class,"speechInProgress").getBoolean(activity));
   assertTrue(chunks.isEmpty());assertNull(field(MainActivity.class,"pendingSpeechWav").get(activity));
   assertNull(field(Z02Player.class,"pendingStart").get(player));
   assertFalse(output.getText().toString().contains("Contenido privado"));
   shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(16));
   assertEquals(0,playbackErrors.get());assertFalse(remote.getTask().isComplete());
  }finally{player.close();gmail.close();calendar.close();}
 }
}
