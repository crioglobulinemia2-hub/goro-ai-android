package ar.goro.ai.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Map;

/** Parses NVIDIA's documented language-group -> voices[] catalogue. */
final class NvidiaVoiceCatalog {
 static boolean contains(String json,String language,String voice){
  JsonElement root=JsonParser.parseString(json);
  if(!root.isJsonObject())return false;
  for(Map.Entry<String,JsonElement> entry:root.getAsJsonObject().entrySet()){
   boolean languageSupported=false;
   for(String code:entry.getKey().split(","))if(language.equalsIgnoreCase(code.trim()))languageSupported=true;
   if(!languageSupported||!entry.getValue().isJsonObject())continue;
   JsonObject group=entry.getValue().getAsJsonObject();
   JsonElement voices=group.get("voices");
   if(voices==null||!voices.isJsonArray())continue;
   for(JsonElement item:voices.getAsJsonArray())
    if(item.isJsonPrimitive()&&item.getAsJsonPrimitive().isString()&&voice.equals(item.getAsString()))return true;
  }
  return false;
 }
}
