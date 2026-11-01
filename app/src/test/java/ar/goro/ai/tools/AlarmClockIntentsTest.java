package ar.goro.ai.tools;
import android.content.Intent;
import android.provider.AlarmClock;
import org.junit.Test;
import static org.junit.Assert.*;
public class AlarmClockIntentsTest {
 @Test public void createsAlarmIntent(){
  Intent i=AlarmClockIntents.setAlarm(AssistantCommandParser.alarm("alarma a las 07:35"));
  assertEquals(AlarmClock.ACTION_SET_ALARM,i.getAction());
  assertEquals(7,i.getIntExtra(AlarmClock.EXTRA_HOUR,-1));
  assertEquals(35,i.getIntExtra(AlarmClock.EXTRA_MINUTES,-1));
  assertFalse(i.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI,true));
 }
 @Test public void createsTimerIntent(){
  Intent i=AlarmClockIntents.setTimer(AssistantCommandParser.timer("temporizador 10 minutos"));
  assertEquals(AlarmClock.ACTION_SET_TIMER,i.getAction());
  assertEquals(600,i.getIntExtra(AlarmClock.EXTRA_LENGTH,-1));
 }
 @Test public void exposesNativeLists(){
  assertEquals(AlarmClock.ACTION_SHOW_ALARMS,AlarmClockIntents.showAlarms().getAction());
  assertEquals(AlarmClock.ACTION_SHOW_TIMERS,AlarmClockIntents.showTimers().getAction());
 }
 @Test(expected=IllegalArgumentException.class) public void rejectsNullAlarm(){AlarmClockIntents.setAlarm(null);}
 @Test(expected=IllegalArgumentException.class) public void rejectsNullTimer(){AlarmClockIntents.setTimer(null);}
}
