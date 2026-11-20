package ar.goro.ai.avatar;

/** Maps canonical expression weights to the named targets of the loaded mesh. */
final class AvatarMorphMap {
 private final int[] indices;
 private final int targetCount;
 private final boolean complete;

 AvatarMorphMap(String[] targetNames){
  targetCount=targetNames.length;
  String[] required=AvatarRigContract.REQUIRED_MORPHS;
  indices=new int[required.length];
  boolean all=true;
  for(int i=0;i<required.length;i++){
   indices[i]=-1;
   int matches=0;
   for(int j=0;j<targetNames.length;j++){
    if(required[i].equals(targetNames[j])){indices[i]=j;matches++;}
   }
   if(matches!=1)all=false;
  }
  complete=all;
 }

 boolean isComplete(){return complete;}
 int targetCount(){return targetCount;}

 float[] map(float[] canonical){
  float[] result=new float[targetCount];
  mapInto(canonical,result);
  return result;
 }

 /** Reuse the render thread's output buffer, including clearing extra targets. */
 void mapInto(float[] canonical,float[] result){
  if(!complete)throw new IllegalStateException("Incomplete named facial targets");
  if(canonical.length!=indices.length)throw new IllegalArgumentException("Unexpected expression count");
  if(result.length!=targetCount)throw new IllegalArgumentException("Unexpected target count");
  if(canonical==result)throw new IllegalArgumentException("Expression and target buffers must be separate");
  java.util.Arrays.fill(result,0f);
  for(int i=0;i<indices.length;i++)result[indices[i]]=canonical[i];
 }
}
