package ar.goro.ai.avatar;
import org.junit.Test;
import static org.junit.Assert.*;
public class AvatarMotionContractTest {
 @Test public void rigRequiresBodyAndFacialMorphs(){assertTrue(java.util.Arrays.asList(AvatarRigContract.REQUIRED_BONES).contains("right_elbow"));assertTrue(java.util.Arrays.asList(AvatarRigContract.REQUIRED_MORPHS).contains("viseme_A"));}
 @Test public void spanishVisemesMapVowelsAndMbp(){VisemeEstimator e=new VisemeEstimator();assertEquals(VisemeEstimator.Viseme.A,e.estimate('á'));assertEquals(VisemeEstimator.Viseme.O,e.estimate('ó'));assertEquals(VisemeEstimator.Viseme.MBP,e.estimate('m'));}
 @Test public void gesturesDoNotRepeatConsecutively(){ProceduralGesturePlanner p=new ProceduralGesturePlanner(42);ProceduralGesturePlanner.Gesture last=null;for(int i=0;i<100;i++){ProceduralGesturePlanner.Gesture next=p.next();assertNotEquals(last,next);last=next;}}
}
