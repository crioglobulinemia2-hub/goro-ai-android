package ar.goro.ai.avatar;
/** Required named nodes and morph targets for the authored GORO GLB. */
public final class AvatarRigContract {
 private AvatarRigContract(){}
 public static final String[] REQUIRED_BONES={"root","pelvis","spine","chest","neck","head","left_shoulder","left_elbow","left_wrist","left_hand","right_shoulder","right_elbow","right_wrist","right_hand"};
 public static final String[] REQUIRED_MORPHS={"viseme_A","viseme_E","viseme_I","viseme_O","viseme_U","viseme_MBP","eye_blink_left","eye_blink_right","brow_raise","smile"};
 public static final String FORMAT="glTF 2.0 GLB";
 public static final String[] REQUIRED_MATERIALS={"pearl_white","titanium","graphite","optical_blue"};
}
