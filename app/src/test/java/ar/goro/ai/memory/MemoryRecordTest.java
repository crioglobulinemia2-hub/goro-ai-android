package ar.goro.ai.memory;
import org.junit.Test;
import static org.junit.Assert.*;
public class MemoryRecordTest {
 @Test public void trimsFields(){MemoryRecord r=new MemoryRecord(1," proyecto "," revisar diseño ",1,2);assertEquals("proyecto",r.category);assertEquals("revisar diseño",r.content);}
 @Test(expected=IllegalArgumentException.class) public void rejectsEmpty(){new MemoryRecord(1,"","",1,2);}
 @Test(expected=IllegalArgumentException.class) public void rejectsOversize(){new MemoryRecord(1,"nota",new String(new char[2001]).replace('\0','x'),1,2);}
}
