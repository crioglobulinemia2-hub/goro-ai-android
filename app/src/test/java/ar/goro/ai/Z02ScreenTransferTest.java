package ar.goro.ai;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import com.jieli.bluetooth.bean.settings.v0.ResourceInfo;
import com.jieli.bluetooth.impl.rcsp.charging_case.ChargingCaseOpImpl;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class Z02ScreenTransferTest {
 private GoroZn02Controller controller;
 private BluetoothDevice target;
 private Class<?> uploadClass;
 @Before public void setup()throws Exception{
  controller=new GoroZn02Controller(RuntimeEnvironment.getApplication(),status->{});
  target=BluetoothAdapter.getDefaultAdapter().getRemoteDevice("12:34:56:78:90:AB");
  ReflectionHelpers.setField(controller,"target",target);
  ReflectionHelpers.setField(controller,"connected",true);
  ReflectionHelpers.setField(controller,"connectionEpoch",3L);
  uploadClass=Class.forName("ar.goro.ai.GoroZn02Controller$ScreenUpload");
 }
 @After public void cleanup(){controller.close();}
 private Object upload(long epoch)throws Exception{
  Constructor<?> constructor=uploadClass.getDeclaredConstructor(BluetoothDevice.class,long.class,ChargingCaseOpImpl.class);
  constructor.setAccessible(true);return constructor.newInstance(target,epoch,null);
 }
 private File output()throws Exception{
  File directory=new File(RuntimeEnvironment.getApplication().getCacheDir(),"goro-screen-"+java.util.UUID.randomUUID());
  assertTrue(directory.mkdir());File file=new File(directory,"GORO-1234.RES");assertTrue(file.createNewFile());return file;
 }
 private Object call(String name,Object upload)throws Exception{
  Method method=GoroZn02Controller.class.getDeclaredMethod(name,uploadClass);method.setAccessible(true);
  return method.invoke(controller,upload);
 }
 @Test public void reconnectedSameAddressCannotActivateAnOldConvertedResource()throws Exception{
  Object old=upload(2L),current=upload(3L);ReflectionHelpers.setField(controller,"screenUpload",current);
  File oldFile=output();
  Method begin=GoroZn02Controller.class.getDeclaredMethod("beginScreenTransfer",uploadClass,File.class,int.class);begin.setAccessible(true);
  begin.invoke(controller,old,oldFile,100);
  assertFalse(oldFile.exists());assertFalse(oldFile.getParentFile().exists());
  assertSame(current,ReflectionHelpers.getField(controller,"screenUpload"));
  assertEquals(Boolean.TRUE,call("currentScreenUpload",current));
 }
 @Test public void lateCompletionCleansOnlyItsOwnFileAndPreservesTheNewTransfer()throws Exception{
  Object old=upload(2L),current=upload(3L);File oldFile=output(),newFile=output();
  ReflectionHelpers.setField(old,"output",oldFile);ReflectionHelpers.setField(current,"output",newFile);
  ReflectionHelpers.setField(controller,"screenUpload",current);
  call("finishScreenUpload",old);
  assertFalse(oldFile.exists());assertTrue(newFile.exists());
  assertSame(current,ReflectionHelpers.getField(controller,"screenUpload"));
 }
 @Test public void closingInvalidatesTransferAndDeletesItsPreparedFile()throws Exception{
  Object current=upload(3L);File file=output();ReflectionHelpers.setField(current,"output",file);
  ReflectionHelpers.setField(controller,"screenUpload",current);controller.close();
  assertEquals(Boolean.FALSE,call("currentScreenUpload",current));assertFalse(file.exists());
  assertNull(ReflectionHelpers.getField(controller,"screenUpload"));
 }
 private static final class ScreenInfoFixture {
  public int getWidth(){return 240;}
  public int getHeight(){return 296;}
 }
 @Test public void sdkScreenInfoFallbackReadsPhysicalDimensionsWhenCaseCacheIsEmpty(){
  ScreenInfoFixture info=new ScreenInfoFixture();
  assertEquals(240,GoroZn02Controller.readScreenDimension(info,true));
  assertEquals(296,GoroZn02Controller.readScreenDimension(info,false));
 }
 @Test public void activeResourceMustMatchBothStorageAndExactPath(){
  assertTrue(GoroZn02Controller.matchesScreenResource(new ResourceInfo(7,"GORO-1234.RES"),7,"/GORO-1234.RES"));
  assertFalse(GoroZn02Controller.matchesScreenResource(new ResourceInfo(8,"GORO-1234.RES"),7,"/GORO-1234.RES"));
  assertFalse(GoroZn02Controller.matchesScreenResource(new ResourceInfo(7,"OLD.RES"),7,"/GORO-1234.RES"));
  assertFalse(GoroZn02Controller.matchesScreenResource(null,7,"/GORO-1234.RES"));
 }
 @Test public void microphoneCannotStartDuringScreenTransfer()throws Exception{
  Object current=upload(3L);ReflectionHelpers.setField(controller,"screenUpload",current);
  assertTrue(controller.isScreenUploadActive());assertFalse(controller.startVoice());
  call("finishScreenUpload",current);assertFalse(controller.isScreenUploadActive());
 }
}
