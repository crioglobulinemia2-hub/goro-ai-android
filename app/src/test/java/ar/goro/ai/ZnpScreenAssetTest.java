package ar.goro.ai;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.io.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class ZnpScreenAssetTest {
 @Test public void reportedPortraitDisplayGetsActualPortraitGif()throws Exception{
  File f=ZnpScreenAsset.forDisplay(RuntimeEnvironment.getApplication(),240,296);
  byte[] b=new byte[10];try(FileInputStream in=new FileInputStream(f)){assertEquals(10,in.read(b));}
  assertEquals(240,(b[6]&255)|((b[7]&255)<<8));assertEquals(296,(b[8]&255)|((b[9]&255)<<8));
  assertTrue(f.length()>1000);
 }
 @Test(expected=IOException.class) public void unknownScreenIsNeverGivenWrongResolution()throws Exception{
  ZnpScreenAsset.forDisplay(RuntimeEnvironment.getApplication(),128,128);
 }
}
