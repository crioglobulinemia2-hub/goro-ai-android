package ar.goro.ai.voice;
/** Small deterministic state machine for hands-free conversational turn-taking. */
public final class ConversationTurnMachine {
 private ConversationState state=ConversationState.IDLE;
 public ConversationState state(){return state;}
 public ConversationState listening(){return move(ConversationState.LISTENING);}
 public ConversationState processing(){return move(ConversationState.PROCESSING);}
 public ConversationState speaking(){return move(ConversationState.SPEAKING);}
 public ConversationState interrupt(){return move(ConversationState.INTERRUPTED);}
 public ConversationState idle(){return move(ConversationState.IDLE);}
 public ConversationState error(){return move(ConversationState.ERROR);}
 private ConversationState move(ConversationState next){state=next;return state;}
 public boolean acceptsSpeech(){return state==ConversationState.LISTENING||state==ConversationState.SPEAKING;}
}
