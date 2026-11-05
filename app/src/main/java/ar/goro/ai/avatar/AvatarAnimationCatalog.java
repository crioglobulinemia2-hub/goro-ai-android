package ar.goro.ai.avatar;

import java.util.Locale;

/** Resolves authored glTF clips without depending on their export order. */
final class AvatarAnimationCatalog {
 private final String[] names;
 private final float[] durations;

 AvatarAnimationCatalog(String[] names,float[] durations){
  if(names.length!=durations.length)throw new IllegalArgumentException("Animation metadata length mismatch");
  this.names=names.clone();
  this.durations=durations.clone();
  for(int i=0;i<this.names.length;i++)this.names[i]=normalize(this.names[i]);
 }

 /** A missing state may use an explicit idle clip; never an unrelated clip. */
 int resolve(String state){
  int requested=find(normalize(state));
  return requested>=0?requested:find("idle");
 }

 /** Static or invalid-duration clips are sampled safely at their start. */
 float timeSeconds(int clip,double elapsedSeconds){
  if(clip<0||clip>=durations.length||!Double.isFinite(elapsedSeconds)||elapsedSeconds<=0)return 0f;
  float duration=durations[clip];
  if(!Float.isFinite(duration)||duration<=0)return 0f;
  return (float)(elapsedSeconds%duration);
 }

 private int find(String name){
  if(name.isEmpty())return -1;
  for(int i=0;i<names.length;i++)if(name.equals(names[i]))return i;
  return -1;
 }

 private static String normalize(String value){
  return value==null?"":value.trim().toLowerCase(Locale.ROOT);
 }
}
