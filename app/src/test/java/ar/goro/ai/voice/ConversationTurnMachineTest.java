package ar.goro.ai.voice;
import org.junit.Test;
import static org.junit.Assert.*;
public class ConversationTurnMachineTest {
 @Test public void normalTurn(){ConversationTurnMachine m=new ConversationTurnMachine();assertEquals(ConversationState.LISTENING,m.listening());assertEquals(ConversationState.PROCESSING,m.processing());assertEquals(ConversationState.SPEAKING,m.speaking());assertEquals(ConversationState.LISTENING,m.listening());}
 @Test public void userCanInterruptSpeech(){ConversationTurnMachine m=new ConversationTurnMachine();m.speaking();assertEquals(ConversationState.INTERRUPTED,m.interrupt());assertTrue(m.acceptsSpeech());}
 @Test public void idleDoesNotAcceptSpeech(){ConversationTurnMachine m=new ConversationTurnMachine();assertFalse(m.acceptsSpeech());}
}
