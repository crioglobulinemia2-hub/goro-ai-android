package ar.goro.ai.avatar;

/** One frame chain that can be suspended while the avatar is not visible. */
final class AvatarFrameLoop {
 interface Scheduler {void post();void remove();}
 interface Renderer {void render(long frameTimeNanos);}
 private final Scheduler scheduler;
 private final Renderer renderer;
 private boolean active,posted;

 AvatarFrameLoop(Scheduler scheduler,Renderer renderer){
  this.scheduler=scheduler;this.renderer=renderer;
 }
 void setActive(boolean value){
  active=value;
  if(!active&&posted){scheduler.remove();posted=false;}
  requestFrame();
 }
 void onFrame(long frameTimeNanos){
  posted=false;
  if(!active)return;
  renderer.render(frameTimeNanos);
  requestFrame();
 }
 private void requestFrame(){
  if(active&&!posted){posted=true;scheduler.post();}
 }
}
