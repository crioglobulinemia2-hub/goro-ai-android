package ar.goro.ai.avatar;

import org.junit.Test;
import static org.junit.Assert.*;

public class AvatarAnimationCatalogTest {
 @Test public void reorderedExportKeepsTheRequestedState(){
  AvatarAnimationCatalog clips=new AvatarAnimationCatalog(
    new String[]{"alert","speaking","idle","confirming","listening","thinking"},
    new float[]{1.1f,2.75f,4f,.8f,3f,2f});
  assertEquals(2,clips.resolve("IDLE"));
  assertEquals(4,clips.resolve("LISTENING"));
  assertEquals(5,clips.resolve("THINKING"));
  assertEquals(1,clips.resolve("SPEAKING"));
  assertEquals(3,clips.resolve("CONFIRMING"));
  assertEquals(0,clips.resolve("ALERT"));
 }

 @Test public void missingStateUsesOnlyNamedIdle(){
  AvatarAnimationCatalog clips=new AvatarAnimationCatalog(
    new String[]{"speaking","idle"},new float[]{2f,4f});
  assertEquals(1,clips.resolve("thinking"));
  assertEquals(1,clips.resolve(null));
 }

 @Test public void missingStateWithoutIdleDoesNotPlayAnotherAction(){
  AvatarAnimationCatalog clips=new AvatarAnimationCatalog(
    new String[]{"speaking","alert"},new float[]{2f,1f});
  assertEquals(-1,clips.resolve("listening"));
  assertEquals(-1,new AvatarAnimationCatalog(new String[0],new float[0]).resolve("idle"));
 }

 @Test public void usesTheSelectedClipsActualDuration(){
  AvatarAnimationCatalog clips=new AvatarAnimationCatalog(
    new String[]{"speaking","idle"},new float[]{2.75f,4f});
  assertEquals(1.5f,clips.timeSeconds(clips.resolve("speaking"),7),.00001f);
  assertEquals(3f,clips.timeSeconds(clips.resolve("idle"),7),.00001f);
  assertEquals(0f,clips.timeSeconds(0,5.5),.00001f);
 }

 @Test public void zeroAndInvalidDurationsStayAtStart(){
  AvatarAnimationCatalog clips=new AvatarAnimationCatalog(
    new String[]{"idle","speaking","thinking","alert"},
    new float[]{0f,-1f,Float.NaN,Float.POSITIVE_INFINITY});
  for(int i=0;i<4;i++)assertEquals(0f,clips.timeSeconds(i,7),0f);
 }

 @Test public void invalidClockOrClipCannotProduceInvalidAnimationTime(){
  AvatarAnimationCatalog clips=new AvatarAnimationCatalog(new String[]{"idle"},new float[]{2f});
  for(double elapsed:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY})
   assertEquals(0f,clips.timeSeconds(0,elapsed),0f);
  assertEquals(0f,clips.timeSeconds(-1,1),0f);
  assertEquals(0f,clips.timeSeconds(1,1),0f);
 }

 @Test public void exportNameFormattingDoesNotChangeMeaning(){
  AvatarAnimationCatalog clips=new AvatarAnimationCatalog(
    new String[]{null,"","  Speaking  ","IDLE"},new float[]{1f,1f,2f,4f});
  assertEquals(2,clips.resolve("speaking"));
  assertEquals(3,clips.resolve(""));
 }
}
