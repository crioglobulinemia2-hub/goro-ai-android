package ar.goro.ai.calendar;
import org.junit.Test;
import static org.junit.Assert.*;
public class CalendarEventTest {
 @Test public void keepsTimezoneAndBounds(){CalendarEvent e=new CalendarEvent("id","Reunión","2026-11-03T10:00:00-03:00","2026-11-03T10:30:00-03:00","America/Argentina/Buenos_Aires","");assertEquals("America/Argentina/Buenos_Aires",e.timezone);assertEquals("Reunión",e.title);}
 @Test(expected=IllegalArgumentException.class) public void rejectsEmptyTitle(){new CalendarEvent(null," ","a","b","UTC","");}
 @Test(expected=IllegalArgumentException.class) public void rejectsMissingTimezone(){new CalendarEvent(null,"Test","a","b",null,"");}
}
