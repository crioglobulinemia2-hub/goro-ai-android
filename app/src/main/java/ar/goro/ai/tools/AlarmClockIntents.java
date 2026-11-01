package ar.goro.ai.tools;
import android.content.Intent;
import android.provider.AlarmClock;
/** Builds native Android clock intents; caller launches them and never claims user confirmation. */
public final class AlarmClockIntents {
 private AlarmClockIntents(){}
 public static Intent setAlarm(AssistantCommandParser.Alarm a){
  if(a==null)throw new IllegalArgumentException("alarm required");
  Intent i=new Intent(AlarmClock.ACTION_SET_ALARM);
  i.putExtra(AlarmClock.EXTRA_HOUR,a.hour);i.putExtra(AlarmClock.EXTRA_MINUTES,a.minute);
  i.putExtra(AlarmClock.EXTRA_MESSAGE,"GORO AI");i.putExtra(AlarmClock.EXTRA_SKIP_UI,false);return i;
 }
 public static Intent setTimer(AssistantCommandParser.Timer t){
  if(t==null)throw new IllegalArgumentException("timer required");
  Intent i=new Intent(AlarmClock.ACTION_SET_TIMER);
  i.putExtra(AlarmClock.EXTRA_LENGTH,t.seconds);i.putExtra(AlarmClock.EXTRA_MESSAGE,"GORO AI");i.putExtra(AlarmClock.EXTRA_SKIP_UI,false);return i;
 }
 public static Intent showAlarms(){return new Intent(AlarmClock.ACTION_SHOW_ALARMS);}
 public static Intent showTimers(){return new Intent(AlarmClock.ACTION_SHOW_TIMERS);}
}
