package ar.goro.ai.avatar;
import java.util.Locale;
/** Lightweight Spanish viseme estimate. Renderer maps returned weights to GLB morph targets. */
public final class VisemeEstimator {
 public enum Viseme { REST, A, E, I, O, U, MBP }
 public Viseme estimate(char ch){
  switch(Character.toLowerCase(ch)){
   case 'a':case 'á':return Viseme.A;
   case 'e':case 'é':return Viseme.E;
   case 'i':case 'í':case 'y':return Viseme.I;
   case 'o':case 'ó':return Viseme.O;
   case 'u':case 'ú':return Viseme.U;
   case 'm':case 'b':case 'p':return Viseme.MBP;
   default:return Viseme.REST;
  }
 }
 public float[] weights(Viseme v){
  float[] w=new float[7];w[0]=1f;
  int index=v.ordinal();if(index>0){w[0]=0f;w[index]=1f;}return w;
 }
 public long suggestedDurationMs(char ch){return Character.isWhitespace(ch)?65:ch==','||ch=='.'?150:85;}
}
