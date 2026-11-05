package ar.goro.ai.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Pure Java streaming parser for NVIDIA NIM Chat Completions SSE data lines.
 * Only user-facing delta.content is returned. The separate reasoning_content
 * field must never be displayed or persisted in an assistant transcript.
 */
public final class NvidiaStreamParser {
 private boolean done;
 private boolean receivedReply;
 public String accept(String event){
  if(done||event==null)return "";
  String payload=event.trim();
  if(payload.startsWith("data:"))payload=payload.substring(5).trim();
  if(payload.isEmpty()||payload.startsWith(":"))return "";
  if("[DONE]".equals(payload)){done=true;return "";}
  final JsonObject root;
  try{root=JsonParser.parseString(payload).getAsJsonObject();}
  catch(RuntimeException malformed){throw new IllegalArgumentException("NVIDIA streaming event invalid",malformed);}
  JsonElement error=root.get("error");
  if(error!=null&&!error.isJsonNull()){
   String code="NVIDIA streaming error";
   if(error.isJsonObject()){
    JsonElement message=error.getAsJsonObject().get("message");
    if(message!=null&&!message.isJsonNull()){
     String text=message.getAsString();
     code += ": "+(text.length()>160?text.substring(0,160):text);
    }
   }
   throw new IllegalStateException(code);
  }
  JsonArray choices=root.getAsJsonArray("choices");
  if(choices==null||choices.isEmpty())return "";
  JsonObject choice=choices.get(0).getAsJsonObject();
  JsonElement finish=choice.get("finish_reason");
  if(finish!=null&&!finish.isJsonNull()){
   String reason=finish.getAsString();
   if("length".equals(reason))
    throw new IllegalStateException("NVIDIA alcanzó el límite de respuesta; no se recibió una respuesta completa");
   if(!"stop".equals(reason))
    throw new IllegalStateException("NVIDIA no completó la respuesta: "+reason);
  }
  JsonElement deltaElement=choice.get("delta");
  if(deltaElement==null||deltaElement.isJsonNull())return "";
  JsonObject delta=deltaElement.getAsJsonObject();
  JsonElement content=delta.get("content");
  if(content==null||content.isJsonNull())return "";
  String text=content.getAsString();
  if(!text.isEmpty())receivedReply=true;
  return text;
 }
 public boolean isDone(){return done;}
 public boolean hasAnswer(){return receivedReply;}
}
