package ar.goro.ai.core;
public interface AssistantTool {
 AssistantAction.Kind kind();
 ActionResult execute(AssistantAction action);
}
