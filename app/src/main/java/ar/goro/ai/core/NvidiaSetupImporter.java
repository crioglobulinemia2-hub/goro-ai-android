package ar.goro.ai.core;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Imports only the owner provisioning asset from a previously installed/exported
 * GORO APK. The token is returned in memory and must immediately be saved using
 * NvidiaCredentialStore; it is never logged or written to a shared file.
 */
public final class NvidiaSetupImporter {
 private static final String ENTRY="assets/goro-personal.json";
 private static final int MAX_BYTES=8192;
 private NvidiaSetupImporter(){}
 public static String readKey(InputStream apkStream)throws Exception{
  if(apkStream==null)throw new IllegalArgumentException("APK stream is required");
  try(ZipInputStream zip=new ZipInputStream(apkStream)){
   ZipEntry entry;
   while((entry=zip.getNextEntry())!=null){
    if(!ENTRY.equals(entry.getName()))continue;
    ByteArrayOutputStream out=new ByteArrayOutputStream();
    byte[] buffer=new byte[1024];int n;
    while((n=zip.read(buffer))!=-1){
     if(out.size()+n>MAX_BYTES)throw new IllegalArgumentException("GORO personal setup exceeds 8 KB");
     out.write(buffer,0,n);
    }
    JsonElement parsed=JsonParser.parseString(new String(out.toByteArray(),StandardCharsets.UTF_8));
    JsonObject config=parsed.isJsonObject()?parsed.getAsJsonObject():null;
    JsonElement credential=config==null?null:config.get("nvidia_key");
    String key=credential!=null&&credential.isJsonPrimitive()&&credential.getAsJsonPrimitive().isString()
      ?credential.getAsString():null;
    if(key==null||!key.trim().startsWith("nvapi-")||key.trim().length()<20)
     throw new IllegalArgumentException("The selected GORO APK has no valid NVIDIA credential");
    return key.trim();
   }
  }
  throw new FileNotFoundException("The selected APK does not contain assets/goro-personal.json");
 }
}
