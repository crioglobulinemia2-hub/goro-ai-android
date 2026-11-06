package ar.goro.ai.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Test;
import static org.junit.Assert.*;

public class NvidiaSetupImporterTest {
 private static byte[] apk(String path,String json)throws Exception{
  ByteArrayOutputStream bytes=new ByteArrayOutputStream();
  try(ZipOutputStream zip=new ZipOutputStream(bytes)){
   zip.putNextEntry(new ZipEntry(path));
   zip.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
   zip.closeEntry();
  }
  return bytes.toByteArray();
 }
 @Test public void readsOwnerCredentialFromOriginalGoroApkWithoutPersistingIt()throws Exception{
  String key="nvapi-test-token-123456789012345";
  byte[] apk=apk("assets/goro-personal.json","{\"nvidia_key\":\""+key+"\"}");
  assertEquals(key,NvidiaSetupImporter.readKey(new ByteArrayInputStream(apk)));
 }
 @Test(expected=FileNotFoundException.class)
 public void rejectsApkWithoutPrivateProvisioningAsset()throws Exception{
  NvidiaSetupImporter.readKey(new ByteArrayInputStream(apk("assets/readme.txt","no secret")));
 }
 @Test(expected=IllegalArgumentException.class)
 public void rejectsInvalidCredentialWithoutReturningIt()throws Exception{
  NvidiaSetupImporter.readKey(new ByteArrayInputStream(apk("assets/goro-personal.json","{\"nvidia_key\":\"not-a-key\"}")));
 }
 @Test(expected=IllegalArgumentException.class)
 public void boundsProvisioningAssetSize()throws Exception{
  String large="x".repeat(9000);
  NvidiaSetupImporter.readKey(new ByteArrayInputStream(apk("assets/goro-personal.json","{\"nvidia_key\":\"nvapi-"+large+"\"}")));
 }
}
