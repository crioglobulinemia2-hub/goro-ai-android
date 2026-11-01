package ar.goro.ai.automation;
import org.junit.Test;
import static org.junit.Assert.*;
public class ReminderValidationTest {
 @Test public void channelIdIsStable(){assertEquals("goro_reminders",ReminderWorker.CHANNEL);}
}
