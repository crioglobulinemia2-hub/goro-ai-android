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
 @Test public void reportedLandscapeDisplayGetsActualLandscapeGif()throws Exception{
  File f=ZnpScreenAsset.forDisplay(InstrumentationRegistry.getInstrumentation().getTargetContext(),320,172);
  byte[] b=new byte[10];try(FileInputStream in=new FileInputStream(f)){assertEquals(10,in.read(b));}
  assertEquals(320,(b[6]&255)|((b[7]&255)<<8));assertEquals(172,(b[8]&255)|((b[9]&255)<<8));
  assertTrue(f.length()>1000);
 }
 @Test public void selectedModelGetsItsOwnNamedPortraitAsset()throws Exception{
  File f=ZnpScreenAsset.forDevice(InstrumentationRegistry.getInstrumentation().getTargetContext(),"Z01",240,296);
  assertTrue(f.getName().startsWith("GORO-Z01-"));
  byte[] b=new byte[10];try(FileInputStream in=new FileInputStream(f)){assertEquals(10,in.read(b));}
  assertEquals(240,(b[6]&255)|((b[7]&255)<<8));assertEquals(296,(b[8]&255)|((b[9]&255)<<8));
 }
 @Test public void AZ02AliasGetsTheZ02PortraitAsset()throws Exception{
  File f=ZnpScreenAsset.forDevice(InstrumentationRegistry.getInstrumentation().getTargetContext(),"AZ02",240,296);
  assertTrue(f.getName().startsWith("GORO-Z02-"));
 }
 @Test public void selectedModelGetsItsOwnNamedLandscapeAsset()throws Exception{
  File f=ZnpScreenAsset.forDevice(InstrumentationRegistry.getInstrumentation().getTargetContext(),"N02",320,172);
  assertTrue(f.getName().startsWith("GORO-N02-"));
  byte[] b=new byte[10];try(FileInputStream in=new FileInputStream(f)){assertEquals(10,in.read(b));}
  assertEquals(320,(b[6]&255)|((b[7]&255)<<8));assertEquals(172,(b[8]&255)|((b[9]&255)<<8));
 }
 @Test(expected=IOException.class) public void unknownModelCannotReceiveAnotherModelsAnimation()throws Exception{
  ZnpScreenAsset.forDevice(InstrumentationRegistry.getInstrumentation().getTargetContext(),"UNKNOWN",320,172);
 }
 @Test(expected=IOException.class) public void unknownScreenIsNeverGivenWrongResolution()throws Exception{
  ZnpScreenAsset.forDisplay(InstrumentationRegistry.getInstrumentation().getTargetContext(),128,128);
 }
}
