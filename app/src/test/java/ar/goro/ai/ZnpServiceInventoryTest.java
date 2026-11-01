package ar.goro.ai;
import org.junit.Test;
import java.util.Arrays;
import java.util.UUID;
import static org.junit.Assert.*;
public class ZnpServiceInventoryTest {
 @Test public void recognizesBattery(){assertEquals("BATTERY",ZnpServiceInventory.classify(ZnpServiceInventory.BATTERY));}
 @Test public void recognizesGenericAccess(){assertEquals("GENERIC_ACCESS",ZnpServiceInventory.classify(ZnpServiceInventory.GENERIC_ACCESS));}
 @Test public void unknownDoesNotImplyRcsp(){UUID unknown=UUID.fromString("12345678-0000-1000-8000-00805f9b34fb");assertEquals("UNIDENTIFIED",ZnpServiceInventory.classify(unknown));assertFalse(ZnpServiceInventory.rcspVerified(Arrays.asList(unknown)));}
 @Test public void nullSafe(){assertEquals("INVALID",ZnpServiceInventory.classify(null));assertTrue(ZnpServiceInventory.summarize(null).isEmpty());}
}
