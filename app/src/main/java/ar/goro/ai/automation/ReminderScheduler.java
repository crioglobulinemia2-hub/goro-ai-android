package ar.goro.ai.automation;
import android.content.Context;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import java.util.concurrent.TimeUnit;
public final class ReminderScheduler {
 private final WorkManager work;
 public ReminderScheduler(Context context){work=WorkManager.getInstance(context.getApplicationContext());}
 public void once(String id,String title,String body,long delay,TimeUnit unit){
  if(id==null||id.trim().isEmpty()||title==null||title.trim().isEmpty()||delay<0)throw new IllegalArgumentException("Invalid reminder");
  Data data=new Data.Builder().putString("title",title).putString("body",body==null?"":body).build();
  OneTimeWorkRequest request=new OneTimeWorkRequest.Builder(ReminderWorker.class).setInputData(data).setInitialDelay(delay,unit).build();
  work.enqueueUniqueWork("goro-reminder-"+id,ExistingWorkPolicy.REPLACE,request);
 }
 public void recurring(String id,String title,String body,long interval,TimeUnit unit){
  if(id==null||id.trim().isEmpty()||title==null||title.trim().isEmpty()||interval<15||unit!=TimeUnit.MINUTES)throw new IllegalArgumentException("Periodic reminders require at least 15 minutes");
  Data data=new Data.Builder().putString("title",title).putString("body",body==null?"":body).build();
  PeriodicWorkRequest request=new PeriodicWorkRequest.Builder(ReminderWorker.class,interval,unit).setInputData(data).build();
  work.enqueueUniquePeriodicWork("goro-periodic-"+id,ExistingPeriodicWorkPolicy.UPDATE,request);
 }
 public void cancel(String id){if(id!=null&&!id.trim().isEmpty()){work.cancelUniqueWork("goro-reminder-"+id);work.cancelUniqueWork("goro-periodic-"+id);}}
}
