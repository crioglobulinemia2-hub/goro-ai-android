package ar.goro.ai.automation;
import android.content.Context;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.testing.WorkManagerTestInitHelper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import java.util.List;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
public class ReminderSchedulerTest {
 private Context context;private ReminderScheduler scheduler;
 @Before public void setUp(){context=RuntimeEnvironment.getApplication();WorkManagerTestInitHelper.initializeTestWorkManager(context);scheduler=new ReminderScheduler(context);}
 @After public void tearDown(){WorkManagerTestInitHelper.closeWorkDatabase();}
 @Test public void enqueuesUniquePersistentOneTimeReminder()throws Exception{
  scheduler.once("test-id","Llamar","Recordatorio",30,TimeUnit.MINUTES);
  List<WorkInfo> work=WorkManager.getInstance(context).getWorkInfosForUniqueWork("goro-reminder-test-id").get();
  assertEquals(1,work.size());assertEquals(WorkInfo.State.ENQUEUED,work.get(0).getState());
 }
 @Test(expected=IllegalArgumentException.class) public void rejectsTooFrequentRecurringReminder(){scheduler.recurring("test","Title","Body",10,TimeUnit.MINUTES);}
 @Test public void recurringReminderRequiresFifteenMinutes(){scheduler.recurring("weekly","Resumen","Body",15,TimeUnit.MINUTES);assertFalse(WorkManager.getInstance(context).getWorkInfosForUniqueWork("goro-periodic-weekly").get().isEmpty());}
}
