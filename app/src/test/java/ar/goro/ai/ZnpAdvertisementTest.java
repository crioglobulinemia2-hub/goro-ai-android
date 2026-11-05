package ar.goro.ai;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import static org.junit.Assert.*;
public class ZnpAdvertisementTest {
 private byte[] name(int type,String name){byte[] n=name.getBytes(StandardCharsets.UTF_8);byte[] a=new byte[n.length+2];a[0]=(byte)(n.length+1);a[1]=(byte)type;System.arraycopy(n,0,a,2,n.length);return a;}
 @Test public void findsZ01FromAdvertisement(){assertEquals(ZnpDeviceProfile.Model.Z01_CANDIDATE,ZnpAdvertisement.profile(null,name(9,"ZNP Z01")).model);}
 @Test public void findsZ02WhenAndroidNameCacheIsEmpty(){assertTrue(ZnpAdvertisement.profile(null,name(9,"ZNP Z02")).isCandidate());}
 @Test public void findsZ02WhenAndroidCacheContainsOldGenericName(){assertTrue(ZnpAdvertisement.profile("Bluetooth device",name(9,"ZNP Z02")).isCandidate());}
 @Test public void recognizesShortenedLocalName(){assertTrue(ZnpAdvertisement.profile(null,name(8,"ZNP Z02")).isCandidate());}
 @Test public void completeNameOverridesShortenedName(){byte[] shortName=name(8,"ZNP Z02"),fullName=name(9,"Other device");byte[] raw=new byte[shortName.length+fullName.length];System.arraycopy(shortName,0,raw,0,shortName.length);System.arraycopy(fullName,0,raw,shortName.length,fullName.length);assertFalse(ZnpAdvertisement.profile(null,raw).isCandidate());}
 @Test public void manufacturerBytesCannotMasqueradeAsLocalName(){assertFalse(ZnpAdvertisement.profile(null,name(255,"ZNP Z02")).isCandidate());}
 @Test public void malformedRecordCannotReadBeyondItsLength(){assertFalse(ZnpAdvertisement.profile(null,new byte[]{100,9,90,48,50}).isCandidate());assertNull(ZnpAdvertisement.localName(new byte[]{0}));assertNull(ZnpAdvertisement.localName(new byte[]{1}));}
 @Test public void rejectsOtherNamedDevicesAndPreservesKnownCachedNames(){assertFalse(ZnpAdvertisement.profile("JBL Tune 520BT-LE",name(9,"JBL Tune 520BT-LE")).isCandidate());assertTrue(ZnpAdvertisement.profile("ZNP Z02",null).isCandidate());}
}
