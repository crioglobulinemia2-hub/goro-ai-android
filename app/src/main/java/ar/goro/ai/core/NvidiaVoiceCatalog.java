package ar.goro.ai.core;
import com.google.gson.*;

/** Parse the documented locale -> voices array, never a substring in an error message. */
public final class NvidiaVoiceCatalog {
 private NvidiaVoiceCatalog(){}
 public static boolean contains(String json,String voice){
  JsonObject root=JsonParser.parseString(json).getAsJsonObject();
  for(java.util.Map.Entry<String,JsonElement> entry:root.entrySet()){
   if(!entry.getValue().isJsonObject())continue;
   JsonElement voices=entry.getValue().getAsJsonObject().get("voices");
   if(voices==null||!voices.isJsonArray())continue;
   for(JsonElement item:voices.getAsJsonArray())
    if(item.isJsonPrimitive()&&item.getAsJsonPrimitive().isString()&&voice.equals(item.getAsString()))return true;
  }
  return false;
 }
}
