package ar.goro.ai.auth;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;
/** Device-bound encrypted OAuth token storage. Never stores a client secret. */
public final class GoogleTokenStore {
 private final SharedPreferences prefs;
 public GoogleTokenStore(Context context)throws Exception{
  MasterKey key=new MasterKey.Builder(context.getApplicationContext()).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build();
  prefs=EncryptedSharedPreferences.create(context.getApplicationContext(),"goro_google_oauth",key,EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
 }
 public synchronized void save(String access,String refresh,long expiresAt){
  prefs.edit().putString("access_token",access).putString("refresh_token",refresh).putLong("expires_at",expiresAt).apply();
 }
 public synchronized String access(){return prefs.getString("access_token",null);}
 public synchronized String refresh(){return prefs.getString("refresh_token",null);}
 public synchronized long expiresAt(){return prefs.getLong("expires_at",0);}
 public synchronized void clear(){prefs.edit().clear().apply();}
}
