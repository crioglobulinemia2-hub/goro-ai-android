package ar.goro.ai;

import android.Manifest;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.SystemClock;
import ar.goro.ai.avatar.GoroAvatarView;
import ar.goro.ai.ui.GoroDashboardView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.core.app.ActivityScenario;
import org.junit.Test;
import org.junit.Before;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class GoroLaunchSmokeTest {
 @Before public void grantRuntimePermissions(){
  InstrumentationRegistry.getInstrumentation().getTargetContext().getSharedPreferences("goro_voice_control",0).edit().clear().commit();
  android.app.UiAutomation automation=InstrumentationRegistry.getInstrumentation().getUiAutomation();
  String packageName=InstrumentationRegistry.getInstrumentation().getTargetContext().getPackageName();
  for(String permission:new String[]{
    Manifest.permission.BLUETOOTH_SCAN,
    Manifest.permission.BLUETOOTH_CONNECT,
    Manifest.permission.RECORD_AUDIO,
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION
  }){
   try{automation.grantRuntimePermission(packageName,permission);}catch(Exception ignored){}
  }
 }
 @Test public void appLaunchesInConversationModeWithoutTechnicalButtonWall(){
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   scenario.onActivity(activity->{
    View root=activity.findViewById(android.R.id.content);
    assertNotNull(root);
    assertNotNull(findText(root,"GORO IA"));
    assertNotNull(findText(root,"GORO IA · Tocá Activar escucha para conversar con el dispositivo ZNP conectado. Podés pausarla en cualquier momento."));
    assertNotNull(findText(root,"Escucha pausada"));
    assertNotNull(findText(root,"Conversar"));
    assertNotNull(findText(root,"Explorar"));
    assertNotNull(findText(root,"Automatizar"));
    assertNotNull(findText(root,"Ajustes"));
    assertNotNull(findText(root,"Dispositivos"));
    assertEquals(4,countVisibleNavigation(root));
   });
  }
 }
 @Test public void listeningWithoutNvidiaNeverReportsFalseActiveState(){
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   scenario.onActivity(activity->{
    View root=activity.findViewById(android.R.id.content);
    TextView activate=findText(root,"Activar escucha");assertNotNull(activate);assertTrue(activate.isShown());
    activate.performClick();
    assertNull("The app cannot claim to be listening before NVIDIA is configured",findText(root,"Pausar escucha"));
    assertTrue(activity.getSharedPreferences("goro_voice_control",0).getBoolean("paused",false));
   });
   scenario.recreate();
   scenario.onActivity(activity->{
    View root=activity.findViewById(android.R.id.content);
    assertNotNull(findText(root,"Activar escucha"));assertNotNull(findText(root,"Escucha pausada"));
    try{java.lang.reflect.Field field=MainActivity.class.getDeclaredField("voiceActive");field.setAccessible(true);assertFalse(field.getBoolean(activity));}
    catch(ReflectiveOperationException error){throw new AssertionError(error);}
   });
  }
 }
 @Test public void conversationScreenKeepsTheSameAnimatedAvatarVisible(){
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   scenario.onActivity(activity->{
    View root=activity.findViewById(android.R.id.content);
    GoroAvatarView avatar=findAvatar(root);
    assertNotNull("GORO avatar must exist on the home page",avatar);
    View open=findDescription(root,"Abrir respuesta completa de Goro");
    assertNotNull(open);
    open.performClick();
    assertNotNull(findText(root,"CONVERSACIÓN"));
    assertTrue("GORO avatar must remain visible in conversation",avatar.isShown());
    View back=findDescription(root,"Menú y volver");
    assertNotNull(back);
    back.performClick();
    assertTrue("GORO avatar must return to the home page",avatar.isShown());
   });
  }
 }
 @Test public void navigationSeparatesDeviceAndSettingsControls(){
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   scenario.onActivity(activity->{
    View root=activity.findViewById(android.R.id.content);
    TextView device=findText(root,"Dispositivos");
    assertNotNull(device);
    device.performClick();
    TextView connect=findText(root,"Conectar AZ02 / Z02");
    assertNotNull(connect);
    assertTrue(connect.isShown());
    TextView settings=findText(root,"Ajustes");
    assertNotNull(settings);
    settings.performClick();
    TextView nvidia=findText(root,"Configurar NVIDIA");
    assertNotNull(nvidia);
    assertTrue(nvidia.isShown());
    assertFalse(connect.isShown());
   });
  }
 }
 @Test public void tallViewportShowsWholeSceneAndLowerControls() throws Exception{
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   java.util.concurrent.CountDownLatch laidOut=new java.util.concurrent.CountDownLatch(1);
   final GoroDashboardView[] dashboard=new GoroDashboardView[1];
   scenario.onActivity(activity->{
    dashboard[0]=findDashboard(activity.findViewById(android.R.id.content));
    assertNotNull(dashboard[0]);
    if(dashboard[0].isLaidOut())laidOut.countDown();
    else dashboard[0].addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->laidOut.countDown());
   });
   assertTrue("Dashboard must complete its real Android measurement",laidOut.await(10,java.util.concurrent.TimeUnit.SECONDS));
   scenario.onActivity(activity->{
    View scene=(View)dashboard[0].avatarStage.getParent();
    android.widget.ScrollView viewport=(android.widget.ScrollView)scene.getParent();
    int minimum=Math.round(460*activity.getResources().getDisplayMetrics().density);
    assertTrue("Test device must provide a viewport at least 460 dp tall",viewport.getHeight()>=minimum);
    assertEquals("The room image must not enlarge the scene beyond its viewport",viewport.getHeight(),scene.getHeight());
    assertEquals("Lower controls must be visible at initial scroll position",0,viewport.getScrollY());
    assertFullyVisible(dashboard[0].state);
    assertFullyVisible(findText(dashboard[0],"Gmail"));
    assertFullyVisible(findDescription(dashboard[0],"Activar escucha"));
   });
  }
 }
 private static void assertFullyVisible(View view){
  assertNotNull(view);
  android.graphics.Rect visible=new android.graphics.Rect();
  assertTrue("Lower control must have visible pixels",view.getLocalVisibleRect(visible));
  assertEquals("Lower control height must be entirely visible",view.getHeight(),visible.height());
  assertEquals("Lower control width must be entirely visible",view.getWidth(),visible.width());
 }
 private static GoroDashboardView findDashboard(View view){
  if(view instanceof GoroDashboardView)return (GoroDashboardView)view;
  if(view instanceof ViewGroup){
   ViewGroup group=(ViewGroup)view;
   for(int i=0;i<group.getChildCount();i++){
    GoroDashboardView result=findDashboard(group.getChildAt(i));if(result!=null)return result;
   }
  }
  return null;
 }
 private static View findDescription(View view,String description){
  if(view.getContentDescription()!=null&&description.contentEquals(view.getContentDescription()))return view;
  if(view instanceof ViewGroup){
   ViewGroup group=(ViewGroup)view;
   for(int i=0;i<group.getChildCount();i++){
    View result=findDescription(group.getChildAt(i),description);if(result!=null)return result;
   }
  }
  return null;
 }
 @Test public void nativeAvatarRendersWithTransparentEdgesOverTheRoom() throws Exception{
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   final GoroAvatarView[] avatar=new GoroAvatarView[1];
   final int[] bounds=new int[4];
   scenario.onActivity(activity->{
    avatar[0]=findAvatar(activity.findViewById(android.R.id.content));
    assertNotNull("The actual native avatar must be present",avatar[0]);
   });
   SystemClock.sleep(1600);
   scenario.onActivity(activity->{
    int[] position=new int[2];avatar[0].getLocationOnScreen(position);
    bounds[0]=position[0];bounds[1]=position[1];
    bounds[2]=avatar[0].getWidth();bounds[3]=avatar[0].getHeight();
    assertTrue(bounds[2]>40&&bounds[3]>40);

   });
   changeVisibilityAndAwaitCommit(scenario,avatar[0],View.INVISIBLE);
   Bitmap room=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
   assertNotNull(room);
   persistCapture("room-hidden.png",room);
   changeVisibilityAndAwaitCommit(scenario,avatar[0],View.VISIBLE);
   // Software Vulkan can take more than a second to present a frame. Wait
   // for the same pixel evidence asserted below, not for an arbitrary sleep.
   // The deadline still fails a surface that never draws the actual robot.
   Bitmap rendered=null;
   int changed=0;
   long deadline=SystemClock.uptimeMillis()+10000;
   do{
    if(rendered!=null)rendered.recycle();
    rendered=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
    assertNotNull(rendered);
    changed=countChangedPixels(room,rendered,bounds);
    if(changed>100)break;
    SystemClock.sleep(100);
   }while(SystemClock.uptimeMillis()<deadline);
   persistCapture("robot-visible.png",rendered);
   // The left edge is outside the full-height model and the right-hand cards.
   // It must retain the underlying room, not an opaque Vulkan clear rectangle.
   for(float fraction:new float[]{.35f,.45f,.55f}){
    int y=bounds[1]+Math.round(bounds[3]*fraction);
    for(int offset:new int[]{3,6,9}){
     int x=bounds[0]+offset;
     assertTrue("Native surface must preserve room pixels at "+x+","+y,
       colorDistance(room.getPixel(x,y),rendered.getPixel(x,y))<=24);
    }
   }
   assertTrue("Transparent surface must also draw the actual robot",changed>100);
   room.recycle();rendered.recycle();
  }
 }
 private static void persistCapture(String filename,Bitmap bitmap){
  java.io.File folder=new java.io.File(
      InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(null),
      "goro-visual-test");
  if(!folder.isDirectory()&&!folder.mkdirs())return;
  java.io.File image=new java.io.File(folder,filename);
  try(java.io.FileOutputStream file=new java.io.FileOutputStream(image)){
   bitmap.compress(Bitmap.CompressFormat.PNG,100,file);
  }catch(java.io.IOException failure){
   android.util.Log.w("GoroVisualTest","Could not save visual evidence",failure);return;
  }
  // Preserve only these emulator screenshots if AGP removes the test package.
  copyEvidenceWithShell("mkdir -p /sdcard/Download/goro-visual-test");
  if(image.getAbsolutePath().matches(".*\\s.*")||!filename.matches("[a-z-]+\\.png")){
   android.util.Log.w("GoroVisualTest","Refusing an unexpected visual evidence path");return;
  }
  // Both paths are generated by this test and contain no whitespace. The
  // UiAutomation command API executes argv directly, without shell quoting.
  copyEvidenceWithShell("cp "+image.getAbsolutePath()+
      " /sdcard/Download/goro-visual-test/"+filename);
 }
 private static void copyEvidenceWithShell(String command){
  android.os.ParcelFileDescriptor result=
      InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(command);
  try(java.io.InputStream stream=new android.os.ParcelFileDescriptor.AutoCloseInputStream(result)){
   java.io.ByteArrayOutputStream diagnostic=new java.io.ByteArrayOutputStream();
   byte[] buffer=new byte[256];int count;
   while((count=stream.read(buffer))!=-1)diagnostic.write(buffer,0,count);
   String message=new String(diagnostic.toByteArray(),java.nio.charset.StandardCharsets.UTF_8).trim();
   if(!message.isEmpty())android.util.Log.w("GoroVisualTest","Visual evidence export: "+message);
  }catch(java.io.IOException failure){android.util.Log.w("GoroVisualTest","Could not preserve visual evidence",failure);}
 }
 private static void changeVisibilityAndAwaitCommit(ActivityScenario<MainActivity> scenario,
     View avatar,int visibility) throws InterruptedException{
  java.util.concurrent.CountDownLatch committed=new java.util.concurrent.CountDownLatch(1);
  scenario.onActivity(activity->{
   View root=activity.findViewById(android.R.id.content);
   root.getViewTreeObserver().registerFrameCommitCallback(committed::countDown);
   avatar.setVisibility(visibility);
   root.invalidate();
  });
  assertTrue("Android must commit the avatar visibility change",
      committed.await(10,java.util.concurrent.TimeUnit.SECONDS));
 }
 private static int countChangedPixels(Bitmap room,Bitmap rendered,int[] bounds){
  int changed=0;
  for(int y=bounds[1];y<bounds[1]+bounds[3];y+=6){
   for(int x=bounds[0];x<bounds[0]+bounds[2];x+=6){
    if(colorDistance(room.getPixel(x,y),rendered.getPixel(x,y))>45)changed++;
   }
  }
  return changed;
 }
 private static int colorDistance(int a,int b){
  return Math.abs(Color.red(a)-Color.red(b))+Math.abs(Color.green(a)-Color.green(b))+Math.abs(Color.blue(a)-Color.blue(b));
 }
 private static GoroAvatarView findAvatar(View view){
  if(view instanceof GoroAvatarView)return (GoroAvatarView)view;
  if(view instanceof ViewGroup){
   ViewGroup group=(ViewGroup)view;
   for(int i=0;i<group.getChildCount();i++){
    GoroAvatarView found=findAvatar(group.getChildAt(i));if(found!=null)return found;
   }
  }
  return null;
 }
 private int countVisibleNavigation(View root){
  int count=0;
  for(String label:new String[]{"Conversar","Explorar","Automatizar","Ajustes"}){
   TextView value=findText(root,label);
   if(value!=null&&value.isShown())count++;
  }
  return count;
 }
 private static TextView findText(View view,String expected){
  if(view instanceof TextView){
   CharSequence text=((TextView)view).getText();
   if(text!=null&&expected.contentEquals(text))return (TextView)view;
  }
  if(view instanceof ViewGroup){
   ViewGroup group=(ViewGroup)view;
   for(int i=0;i<group.getChildCount();i++){
    TextView found=findText(group.getChildAt(i),expected);
    if(found!=null)return found;
   }
  }
  return null;
 }
}
