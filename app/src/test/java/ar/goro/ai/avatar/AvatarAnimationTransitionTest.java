package ar.goro.ai.avatar;

import org.junit.Test;
import static org.junit.Assert.*;

public class AvatarAnimationTransitionTest {
 private static long ms(long value){return value*1_000_000L;}

 @Test public void stateChangeContinuesTheOutgoingClipWhileFadingInTheNewOne(){
  AvatarAnimationTransition motion=new AvatarAnimationTransition();
  motion.advance(2,ms(1000));
  motion.advance(4,ms(3000));
  assertEquals(4,motion.currentClip());
  assertEquals(2,motion.previousClip());
  assertEquals(0f,motion.blendAlpha(),0f);
  assertEquals(2.0,motion.previousElapsedSeconds(),.00001);
  motion.advance(4,ms(3120));
  assertEquals(.5f,motion.blendAlpha(),.00001f);
  assertEquals(.12,motion.currentElapsedSeconds(),.00001);
  assertEquals(2.12,motion.previousElapsedSeconds(),.00001);
  motion.advance(4,ms(3240));
  assertEquals(-1,motion.previousClip());
  assertEquals(1f,motion.blendAlpha(),0f);
 }

 @Test public void rapidStateChangesKeepTheCurrentBlendThenUseOnlyTheLatestRequest(){
  AvatarAnimationTransition motion=new AvatarAnimationTransition();
  motion.advance(0,ms(1000));
  motion.advance(1,ms(1100));
  motion.advance(2,ms(1180));
  assertEquals(1,motion.currentClip());
  assertEquals(0,motion.previousClip());
  motion.advance(3,ms(1260));
  motion.advance(3,ms(1340));
  assertEquals(3,motion.currentClip());
  assertEquals(1,motion.previousClip());
  assertEquals(.24,motion.previousElapsedSeconds(),.00001);
  assertEquals(0f,motion.blendAlpha(),0f);
 }

 @Test public void repeatedStateAndMissingClipDoNotRestartTheMotion(){
  AvatarAnimationTransition motion=new AvatarAnimationTransition();
  motion.advance(-1,ms(900));
  assertEquals(-1,motion.currentClip());
  motion.advance(0,ms(1000));
  motion.advance(0,ms(2000));
  motion.advance(-1,ms(3000));
  assertEquals(0,motion.currentClip());
  assertEquals(-1,motion.previousClip());
  assertEquals(2.0,motion.currentElapsedSeconds(),.00001);
 }

 @Test public void reversedFrameTimeDoesNotReverseTheBlend(){
  AvatarAnimationTransition motion=new AvatarAnimationTransition();
  motion.advance(0,ms(1000));
  motion.advance(1,ms(1100));
  motion.advance(1,ms(1220));
  motion.advance(1,ms(1150));
  assertEquals(.5f,motion.blendAlpha(),.00001f);
  assertEquals(.12,motion.currentElapsedSeconds(),.00001);
 }
}
