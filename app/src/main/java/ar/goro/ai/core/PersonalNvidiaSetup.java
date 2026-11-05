package ar.goro.ai.core;

import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/** Optional owner-only provisioning asset; absent from source and all public CI builds. */
public final class PersonalNvidiaSetup {
 private PersonalNvidiaSetup(){}
 public static void importIfPresent(Context context,NvidiaCredentialStore store)throws Exception{
  if(store.read()!=null)return;
  byte[] bytes;
  try(InputStream input=context.getAssets().open("goro-personal.json");
      ByteArrayOutputStream output=new ByteArrayOutputStream()){
   byte[] buffer=new byte[1024];int n;
   while((n=input.read(buffer))!=-1){
    if(output.size()+n>8192)throw new IllegalArgumentException("Invalid personal setup");
    output.write(buffer,0,n);
   }
   bytes=output.toByteArray();
  }catch(FileNotFoundException absent){return;}
  JSONObject config=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
  store.save(config.getString("nvidia_key"));
 }
}
