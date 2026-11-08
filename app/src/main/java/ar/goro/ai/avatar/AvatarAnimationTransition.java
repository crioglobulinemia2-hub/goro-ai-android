package ar.goro.ai.avatar;

/** Keeps two authored clips continuous while the avatar changes state. */
final class AvatarAnimationTransition {
 private static final long FADE_NANOS=240_000_000L;
 private int current=-1,previous=-1;
 private long currentStarted,previousStarted,transitionStarted,lastFrame;
 private float alpha=1f;

 void advance(int requested,long frameNanos){
  long now=Math.max(lastFrame,frameNanos);
  lastFrame=now;
  if(previous>=0){
   float fraction=Math.min(1f,Math.max(0f,(now-transitionStarted)/(float)FADE_NANOS));
   alpha=fraction*fraction*(3f-2f*fraction);
   if(fraction>=1f){previous=-1;alpha=1f;}
  }
  if(requested<0)return;
  if(current<0){current=requested;currentStarted=now;return;}
  // Finish the short blend before accepting the latest state. Restarting a
  // blend from a raw clip during interruption would make the skeleton jump.
  if(previous<0&&requested!=current){
   previous=current;previousStarted=currentStarted;
   current=requested;currentStarted=now;transitionStarted=now;alpha=0f;
  }
 }

 int currentClip(){return current;}
 int previousClip(){return previous;}
 float blendAlpha(){return alpha;}
 double currentElapsedSeconds(){return Math.max(0,lastFrame-currentStarted)/1_000_000_000.0;}
 double previousElapsedSeconds(){return Math.max(0,lastFrame-previousStarted)/1_000_000_000.0;}
}
