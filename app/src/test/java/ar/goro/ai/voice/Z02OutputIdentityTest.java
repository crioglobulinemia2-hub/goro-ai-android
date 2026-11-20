package ar.goro.ai.voice;

import android.content.Context;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.AudioDeviceInfoBuilder;
import org.robolectric.util.ReflectionHelpers;
import static org.robolectric.Shadows.shadowOf;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class Z02OutputIdentityTest {
 private static final String ADDRESS="12:34:56:78:90:AB";
 private Z02Player player;
 private AudioManager audio;
 @Before public void setup(){
  Context context=RuntimeEnvironment.getApplication();
  audio=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
  player=new Z02Player(context,new Z02Player.Listener(){
   public void onState(String state){}public void onError(String error){}public void onCompleted(){}
  });
  player.setTarget(ADDRESS,"Z02");
 }
 @After public void cleanup(){player.close();}
 private AudioDeviceInfo device(int id,String address,String name){
  AudioDeviceInfo result=AudioDeviceInfoBuilder.newBuilder().setType(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP).build();
  Object port=ReflectionHelpers.getField(result,"mPort");
  ReflectionHelpers.setField(port,"mAddress",address);
  ReflectionHelpers.setField(port,"mName",name);
  Object handle=ReflectionHelpers.getField(port,"mHandle");
  ReflectionHelpers.setField(handle,"mId",id);
  return result;
 }
 @Test public void anotherZ02WithKnownDifferentAddressCannotReceiveSpeech(){
  shadowOf(audio).setOutputDevices(Collections.singletonList(device(4,"AA:BB:CC:DD:EE:FF","Z02")));
  assertTrue(player.outputs().isEmpty());
 }
 @Test public void exactAddressWinsOverOtherSameNameOutputs(){
  AudioDeviceInfo selected=device(8,ADDRESS,"Z02"),other=device(4,"AA:BB:CC:DD:EE:FF","Z02");
  shadowOf(audio).setOutputDevices(Arrays.asList(other,selected));
  assertEquals(Collections.singletonList(selected),player.outputs());
 }
 @Test public void oneUnreportedAddressCanStillUseTheUniqueName(){
  AudioDeviceInfo selected=device(8,"","Z02");
  shadowOf(audio).setOutputDevices(Collections.singletonList(selected));
  assertEquals(Collections.singletonList(selected),player.outputs());
 }
 @Test public void ambiguousUnreportedAddressesRemainUnavailable(){
  shadowOf(audio).setOutputDevices(Arrays.asList(device(8,"","Z02"),device(9,"","Z02")));
  assertTrue(player.outputs().isEmpty());
 }
 @Test public void removingAnotherDeviceKeepsSpeechButRemovingTargetCancelsIt()throws Exception{
  Class<?> playback=Class.forName("ar.goro.ai.voice.Z02Player$Playback");
  Constructor<?> constructor=playback.getDeclaredConstructor(long.class,int.class);constructor.setAccessible(true);
  Object session=constructor.newInstance(1L,8);ReflectionHelpers.setField(player,"active",session);
  AtomicBoolean running=ReflectionHelpers.getField(session,"running");
  AudioDeviceCallback callback=ReflectionHelpers.getField(player,"deviceCallback");
  callback.onAudioDevicesRemoved(new AudioDeviceInfo[]{device(9,"","Other")});
  assertTrue(running.get());
  callback.onAudioDevicesRemoved(new AudioDeviceInfo[]{device(8,ADDRESS,"Z02")});
  assertFalse(running.get());
 }
}
