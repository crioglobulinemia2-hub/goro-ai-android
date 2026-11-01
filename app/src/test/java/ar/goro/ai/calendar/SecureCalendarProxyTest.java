package ar.goro.ai.calendar;
import org.junit.Test;
import static org.junit.Assert.*;
public class SecureCalendarProxyTest {
 @Test(expected=IllegalArgumentException.class) public void rejectsInsecureEndpoint(){new SecureCalendarProxy("http://localhost","token");}
 @Test(expected=IllegalArgumentException.class) public void rejectsMissingToken(){new SecureCalendarProxy("https://example.invalid","");}
 @Test public void eventRequiresExplicitTimezone(){CalendarEvent e=new CalendarEvent("e","Meeting","2026-11-04T10:00:00-03:00","2026-11-04T11:00:00-03:00","America/Argentina/Buenos_Aires","");assertEquals("America/Argentina/Buenos_Aires",e.timezone);}
}
