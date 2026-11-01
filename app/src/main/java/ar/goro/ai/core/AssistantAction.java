package ar.goro.ai.core;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
public final class AssistantAction {
 public enum Kind { CALCULATE, ALARM, TIMER, CALENDAR_READ, CALENDAR_WRITE, WEATHER, MEMORY_READ, MEMORY_WRITE, UNKNOWN }
 public final Kind kind;
 public final Map<String,String> arguments;
 public AssistantAction(Kind kind,Map<String,String> arguments) {
  if(kind==null)throw new IllegalArgumentException("kind required");
  this.kind=kind;
  this.arguments=Collections.unmodifiableMap(new HashMap<>(arguments==null?Collections.<String,String>emptyMap():arguments));
 }
 public boolean requiresConfirmation(){return kind==Kind.CALENDAR_WRITE || kind==Kind.MEMORY_WRITE;}
}
