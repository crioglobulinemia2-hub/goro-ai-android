package ar.goro.ai.tools;
import org.junit.Test;
import static org.junit.Assert.*;
public class AssistantCommandParserTest {
 @Test public void parsesAlarmWithMinutes(){AssistantCommandParser.Alarm a=AssistantCommandParser.alarm("poné una alarma a las 07:35");assertNotNull(a);assertEquals(7,a.hour);assertEquals(35,a.minute);}
 @Test public void parsesAlarmWithoutMinutes(){AssistantCommandParser.Alarm a=AssistantCommandParser.alarm("despertame para las 8");assertNotNull(a);assertEquals(8,a.hour);assertEquals(0,a.minute);}
 @Test public void rejectsInvalidAlarm(){assertNull(AssistantCommandParser.alarm("alarma a las 27:90"));}
 @Test public void parsesTimerUnits(){assertEquals(600,AssistantCommandParser.timer("temporizador 10 minutos").seconds);assertEquals(90,AssistantCommandParser.timer("temporizador 90 segundos").seconds);assertEquals(7200,AssistantCommandParser.timer("temporizador 2 horas").seconds);}
 @Test public void rejectsInvalidTimer(){assertNull(AssistantCommandParser.timer("temporizador 0 segundos"));assertNull(AssistantCommandParser.timer("temporizador 30 días"));}
}
