package ar.goro.ai.memory;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

/** Local user-controlled memory. Not medical/financial secret storage; app-private SQLite. */
public final class MemoryStore extends SQLiteOpenHelper {
 private static final String DB="goro_memory.db";
 public MemoryStore(Context c){super(c,DB,null,1);}
 @Override public void onCreate(SQLiteDatabase db){
  db.execSQL("CREATE TABLE memories (_id INTEGER PRIMARY KEY AUTOINCREMENT, category TEXT NOT NULL, content TEXT NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)");
  db.execSQL("CREATE INDEX memories_category_idx ON memories(category)");
 }
 @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){}
 public long add(String category,String content){
  MemoryRecord check=new MemoryRecord(0,category,content,System.currentTimeMillis(),System.currentTimeMillis());
  long now=System.currentTimeMillis();ContentValues v=new ContentValues();v.put("category",check.category);v.put("content",check.content);v.put("created_at",now);v.put("updated_at",now);
  return getWritableDatabase().insertOrThrow("memories",null,v);
 }
 public List<MemoryRecord> recent(int limit){
  int safe=Math.max(1,Math.min(100,limit));List<MemoryRecord> out=new ArrayList<>();
  try(Cursor c=getReadableDatabase().query("memories",null,null,null,null,null,"updated_at DESC",String.valueOf(safe))){
   while(c.moveToNext())out.add(new MemoryRecord(c.getLong(c.getColumnIndexOrThrow("_id")),c.getString(c.getColumnIndexOrThrow("category")),c.getString(c.getColumnIndexOrThrow("content")),c.getLong(c.getColumnIndexOrThrow("created_at")),c.getLong(c.getColumnIndexOrThrow("updated_at"))));
  }return out;
 }
 public boolean update(long id,String category,String content){
  MemoryRecord check=new MemoryRecord(id,category,content,0,0);ContentValues v=new ContentValues();v.put("category",check.category);v.put("content",check.content);v.put("updated_at",System.currentTimeMillis());
  return getWritableDatabase().update("memories",v,"_id=?",new String[]{String.valueOf(id)})>0;
 }
 public boolean delete(long id){return getWritableDatabase().delete("memories","_id=?",new String[]{String.valueOf(id)})>0;}
 public int deleteCategory(String category){return getWritableDatabase().delete("memories","category=?",new String[]{category});}
 public List<MemoryRecord> search(String query,int limit){
  List<MemoryRecord> out=new ArrayList<>(); if(query==null||query.trim().isEmpty())return out;
  int safe=Math.max(1,Math.min(100,limit)); String pattern="%"+query.trim()+"%";
  try(Cursor c=getReadableDatabase().query("memories",null,"content LIKE ? OR category LIKE ?",new String[]{pattern,pattern},null,null,"updated_at DESC",String.valueOf(safe))){
   while(c.moveToNext())out.add(new MemoryRecord(c.getLong(c.getColumnIndexOrThrow("_id")),c.getString(c.getColumnIndexOrThrow("category")),c.getString(c.getColumnIndexOrThrow("content")),c.getLong(c.getColumnIndexOrThrow("created_at")),c.getLong(c.getColumnIndexOrThrow("updated_at"))));
  }return out;
 }
 public int clearAll(){return getWritableDatabase().delete("memories",null,null);}
 public int count(){try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM memories",null)){return c.moveToFirst()?c.getInt(0):0;}}
 /** One-time migration from the first prototype SharedPreferences store. */
 public int migrateLegacy(SharedPreferences legacy){
  if(legacy==null||legacy.getBoolean("sqlite_migrated",false))return 0;
  int imported=0; SQLiteDatabase db=getWritableDatabase(); db.beginTransaction();
  try{
   int n=legacy.getInt("next",0); long now=System.currentTimeMillis();
   for(int i=0;i<n;i++){String value=legacy.getString("fact_"+i,null);if(value==null||value.trim().isEmpty())continue;
    ContentValues v=new ContentValues();v.put("category","general");v.put("content",value.trim());v.put("created_at",now);v.put("updated_at",now);db.insertOrThrow("memories",null,v);imported++;
   }
   db.setTransactionSuccessful();
  } finally {db.endTransaction();}
  legacy.edit().putBoolean("sqlite_migrated",true).apply(); return imported;
 }
}
