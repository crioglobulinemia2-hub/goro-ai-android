package ar.goro.ai.memory;
import android.content.Context;
import android.content.SharedPreferences;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
public class MemoryStoreRobolectricTest {
 @Test public void persistsCrudAndMigratesLegacy(){
  Context c=RuntimeEnvironment.getApplication();
  c.deleteDatabase("goro_memory.db");
  MemoryStore store=new MemoryStore(c);
  SharedPreferences old=c.getSharedPreferences("goro_memory_test",Context.MODE_PRIVATE);
  old.edit().clear().putInt("next",2).putString("fact_0","proyecto GORO").putString("fact_1","revisar ZNP").commit();
  assertEquals(2,store.migrateLegacy(old));
  assertEquals(2,store.count());
  store.close();
  store=new MemoryStore(c);
  assertEquals(2,store.count());
  assertEquals(0,store.migrateLegacy(old));
  assertEquals(1,store.search("ZNP",10).size());
  MemoryRecord first=store.recent(10).get(0);
  assertTrue(store.update(first.id,"proyecto","memoria persistente"));
  assertTrue(store.delete(first.id));
  assertEquals(1,store.count());
  assertEquals(1,store.deleteCategory("general"));
  assertEquals(0,store.count());
  store.add("temas","borrar todo");
  assertEquals(1,store.clearAll());
  assertEquals(0,store.count());
  store.close();c.deleteDatabase("goro_memory.db");old.edit().clear().commit();
 }
}
