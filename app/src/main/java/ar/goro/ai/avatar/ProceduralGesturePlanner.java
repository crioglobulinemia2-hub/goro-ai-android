package ar.goro.ai.avatar;
import java.util.Random;
/** Non-looping gesture choices; a renderer applies the targets to the actual rig. */
public final class ProceduralGesturePlanner {
 public enum Gesture { SMALL_NOD, OPEN_HAND_EXPLAIN, RIGHT_HAND_PRESENT, LEFT_HAND_PRESENT, THINKING_HEAD_TILT, ACKNOWLEDGE }
 private final Random random;
 private Gesture previous;
 public ProceduralGesturePlanner(){this(new Random());}
 public ProceduralGesturePlanner(long seed){this(new Random(seed));}
 private ProceduralGesturePlanner(Random random){this.random=random;}
 public Gesture next(){
  Gesture[] all=Gesture.values();Gesture chosen;
  do{chosen=all[random.nextInt(all.length)];}while(chosen==previous);
  previous=chosen;return chosen;
 }
 public long durationMs(Gesture gesture){return 650+random.nextInt(850);}
 public float blendWeight(){return 0.65f+random.nextFloat()*0.35f;}
}
