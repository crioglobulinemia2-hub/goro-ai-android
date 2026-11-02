package ar.goro.ai.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Encrypts the NVIDIA NIM API token with an Android Keystore AES-GCM key. */
public final class NvidiaCredentialStore {
 private static final String PREFS="goro_secure_credentials";
 private static final String KEY="nvidia_nim_token_v1";
 private static final String ALIAS="goro.nvidia.nim.aes.v1";
 private final SharedPreferences prefs;
 public NvidiaCredentialStore(Context context){prefs=context.getApplicationContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE);}
 public synchronized void save(String token)throws Exception{
  if(token==null||!token.trim().startsWith("nvapi-")||token.trim().length()<20)
   throw new IllegalArgumentException("NVIDIA API key format is invalid");
  Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
  cipher.init(Cipher.ENCRYPT_MODE,getOrCreateKey());
  byte[] iv=cipher.getIV();
  byte[] encrypted=cipher.doFinal(token.trim().getBytes(StandardCharsets.UTF_8));
  byte[] packed=new byte[iv.length+encrypted.length];
  System.arraycopy(iv,0,packed,0,iv.length);
  System.arraycopy(encrypted,0,packed,iv.length,encrypted.length);
  if(!prefs.edit().putString(KEY,Base64.encodeToString(packed,Base64.NO_WRAP)).commit())
   throw new IllegalStateException("Credential storage failed");
 }
 public synchronized String read()throws Exception{
  String encoded=prefs.getString(KEY,null);
  if(encoded==null)return null;
  byte[] packed=Base64.decode(encoded,Base64.NO_WRAP);
  if(packed.length<29)throw new IllegalStateException("Encrypted credential is truncated");
  byte[] iv=new byte[12],encrypted=new byte[packed.length-12];
  System.arraycopy(packed,0,iv,0,12);
  System.arraycopy(packed,12,encrypted,0,encrypted.length);
  Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
  cipher.init(Cipher.DECRYPT_MODE,getOrCreateKey(),new GCMParameterSpec(128,iv));
  return new String(cipher.doFinal(encrypted),StandardCharsets.UTF_8);
 }
 public synchronized void clear(){prefs.edit().remove(KEY).commit();}
 public boolean isConfigured(){return prefs.contains(KEY);}
 private SecretKey getOrCreateKey()throws Exception{
  KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
  if(store.containsAlias(ALIAS))return (SecretKey)store.getKey(ALIAS,null);
  KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
  generator.init(new KeyGenParameterSpec.Builder(ALIAS,
    KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
    .setRandomizedEncryptionRequired(true)
    .setKeySize(256).build());
  return generator.generateKey();
 }
}