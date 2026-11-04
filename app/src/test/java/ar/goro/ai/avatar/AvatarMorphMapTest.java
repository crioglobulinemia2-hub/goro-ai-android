package ar.goro.ai.avatar;

import org.junit.Test;
import static org.junit.Assert.*;

public class AvatarMorphMapTest {
 @Test public void reorderedTargetsKeepTheirExpressionMeaning(){
  String[] names={"smile","viseme_MBP","eye_blink_right","viseme_E","viseme_A",
    "extra","brow_raise","viseme_I","eye_blink_left","viseme_O","viseme_U"};
  AvatarMorphMap mapping=new AvatarMorphMap(names);
  assertTrue(mapping.isComplete());
  float[] actual=mapping.map(new float[]{.1f,.2f,.3f,.4f,.5f,.6f,.7f,.8f,.9f,1f});
  assertArrayEquals(new float[]{1f,.6f,.8f,.2f,.1f,0f,.9f,.3f,.7f,.4f,.5f},actual,0f);
 }

 @Test public void currentGeneratorOrderRemainsCompatible(){
  AvatarMorphMap mapping=new AvatarMorphMap(AvatarRigContract.REQUIRED_MORPHS);
  float[] expression={.1f,.2f,.3f,.4f,.5f,.6f,.7f,.8f,.9f,1f};
  assertTrue(mapping.isComplete());
  assertArrayEquals(expression,mapping.map(expression),0f);
 }

 @Test public void unnamedOrDuplicateTargetsDisableExpressions(){
  assertFalse(new AvatarMorphMap(new String[0]).isComplete());
  String[] names=AvatarRigContract.REQUIRED_MORPHS.clone();
  names[0]=null;
  assertFalse(new AvatarMorphMap(names).isComplete());
  names=AvatarRigContract.REQUIRED_MORPHS.clone();
  names[0]="viseme_E";
  assertFalse(new AvatarMorphMap(names).isComplete());
 }
}
