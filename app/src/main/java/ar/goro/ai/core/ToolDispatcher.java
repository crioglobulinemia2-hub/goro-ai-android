package ar.goro.ai.core;
import java.util.EnumMap;
import java.util.Map;
public final class ToolDispatcher {
 private final Map<AssistantAction.Kind,AssistantTool> tools=new EnumMap<>(AssistantAction.Kind.class);
 public void register(AssistantTool tool){if(tool==null)throw new IllegalArgumentException("tool required");tools.put(tool.kind(),tool);}
 public ActionResult dispatch(AssistantAction action,boolean confirmed){
  if(action==null)return ActionResult.of(ActionResult.Status.ERROR,"Invalid action");
  if(action.requiresConfirmation()&&!confirmed)return ActionResult.of(ActionResult.Status.NEEDS_CONFIRMATION,"Confirm before execution");
  AssistantTool tool=tools.get(action.kind);
  if(tool==null)return ActionResult.of(ActionResult.Status.UNAVAILABLE,"Tool not configured");
  try {ActionResult result=tool.execute(action);return result==null?ActionResult.of(ActionResult.Status.ERROR,"No tool result"):result;}
  catch(RuntimeException e){return ActionResult.of(ActionResult.Status.ERROR,"Tool execution failed");}
 }
}
