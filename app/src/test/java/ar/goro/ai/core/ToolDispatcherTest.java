package ar.goro.ai.core;
import org.junit.Test;
import java.util.Collections;
import static org.junit.Assert.*;
public class ToolDispatcherTest {
 @Test public void requiresConfirmation() {
  AssistantAction action=new AssistantAction(AssistantAction.Kind.CALENDAR_WRITE,Collections.emptyMap());
  assertEquals(ActionResult.Status.NEEDS_CONFIRMATION,new ToolDispatcher().dispatch(action,false).status);
 }
 @Test public void unavailableIsNotSuccess() {
  AssistantAction action=new AssistantAction(AssistantAction.Kind.WEATHER,Collections.emptyMap());
  assertFalse(new ToolDispatcher().dispatch(action,true).executed());
 }
 @Test public void calculatorToolExecutes() {
  ToolDispatcher dispatcher=new ToolDispatcher();
  dispatcher.register(new AssistantTool() {
   public AssistantAction.Kind kind(){return AssistantAction.Kind.CALCULATE;}
   public ActionResult execute(AssistantAction action){return ActionResult.of(ActionResult.Status.SUCCESS,"4");}
  });
  assertTrue(dispatcher.dispatch(new AssistantAction(AssistantAction.Kind.CALCULATE,Collections.emptyMap()),false).executed());
 }
}
