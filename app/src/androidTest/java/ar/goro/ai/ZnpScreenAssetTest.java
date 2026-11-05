package ar.goro.ai;
import org.junit.Test;
import org.junit.runner.RunWith;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class ZnpScreenAssetTest {
 @Test public void reportedPortraitDisplayGetsActualPortraitGif()throws Exception{
  File f=ZnpScreenAsset.forDisplay(InstrumentationRegistry.getInstrumentation().getTargetContext(),240,296);
  byte[] b=new byte[10];try(FileInputStream in=new FileInputStream(f)){assertEquals(10,in.read(b));}
  assertEquals(240,(b[6]&255)|((b[7]&255)<<8));assertEquals(296,(b[8]&255)|((b[9]&255)<<8));
  assertTrue(f.length()>1000);
 }
 @Test(expected=IOException.class) public void unknownScreenIsNeverGivenWrongResolution()throws Exception{
  ZnpScreenAsset.forDisplay(InstrumentationRegistry.getInstrumentation().getTargetContext(),128,128);
 }
}
