package ar.goro.ai.tools;
import org.junit.Test;
import static org.junit.Assert.*;
public class AssistantCommandParserBoundaryTest {
 @Test public void hourSuffixes(){assertEquals(7200,AssistantCommandParser.timer("temporizador 2 h").seconds);assertEquals(7200,AssistantCommandParser.timer("temporizador 2 hs").seconds);}
 @Test public void hugeDurationRejected(){assertNull(AssistantCommandParser.timer("temporizador 999999999999999999999 horas"));}
 @Test public void invalidMinutesRejected(){assertNull(AssistantCommandParser.alarm("alarma a las 08:77"));}
 @Test public void boundaryValuesAccepted(){assertEquals(0,AssistantCommandParser.alarm("alarma a las 00:00").hour);assertEquals(86400,AssistantCommandParser.timer("temporizador 24 horas").seconds);}
}
