package ar.goro.ai.automation;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
public final class ReminderWorker extends Worker {
 public static final String CHANNEL="goro_reminders";
 public ReminderWorker(Context c,WorkerParameters p){super(c,p);}
 @Override public Result doWork(){
  Context c=getApplicationContext();String title=getInputData().getString("title");String body=getInputData().getString("body");
  if(title==null||title.trim().isEmpty())return Result.failure();
  NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
  if(nm==null)return Result.failure();
  if(Build.VERSION.SDK_INT>=26){NotificationChannel channel=new NotificationChannel(CHANNEL,"Recordatorios GORO",NotificationManager.IMPORTANCE_DEFAULT);nm.createNotificationChannel(channel);}
  NotificationCompat.Builder b=new NotificationCompat.Builder(c,CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(body==null?"":body).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_DEFAULT);
  try{nm.notify((int)(System.currentTimeMillis()&0x7fffffff),b.build());return Result.success();}catch(SecurityException e){return Result.failure();}
 }
}
