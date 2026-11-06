package ar.goro.ai.avatar;

import org.junit.Test;
import static org.junit.Assert.*;

public class AvatarFrameLoopTest {
 private static final class Scheduler implements AvatarFrameLoop.Scheduler {
  int posts,removes;
  public void post(){posts++;}
  public void remove(){removes++;}
 }
 @Test public void hiddenAvatarCancelsItsFrameAndDoesNotRenderLateCallback(){
  Scheduler scheduler=new Scheduler();int[] renders={0};
  AvatarFrameLoop loop=new AvatarFrameLoop(scheduler,time->renders[0]++);
  loop.setActive(true);
  loop.setActive(false);
  loop.onFrame(123);
  assertEquals(1,scheduler.removes);
  assertEquals(1,scheduler.posts);
  assertEquals(0,renders[0]);
 }
 @Test public void showingAgainRestartsExactlyOneChainAndPreservesFrameTime(){
  Scheduler scheduler=new Scheduler();long[] lastTime={0};
  AvatarFrameLoop loop=new AvatarFrameLoop(scheduler,time->lastTime[0]=time);
  loop.setActive(true);loop.setActive(true);
  assertEquals(1,scheduler.posts);
  loop.setActive(false);loop.setActive(false);
  loop.setActive(true);loop.setActive(true);
  assertEquals(2,scheduler.posts);
  loop.onFrame(987654321);
  assertEquals(987654321,lastTime[0]);
  assertEquals(3,scheduler.posts);
 }
 @Test public void detachingDuringRenderDoesNotScheduleAnotherFrame(){
  Scheduler scheduler=new Scheduler();AvatarFrameLoop[] loop={null};
  loop[0]=new AvatarFrameLoop(scheduler,time->loop[0].setActive(false));
  loop[0].setActive(true);
  loop[0].onFrame(1);
  assertEquals(1,scheduler.posts);
 }
}
