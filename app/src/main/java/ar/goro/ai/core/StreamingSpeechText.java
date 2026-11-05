package ar.goro.ai.core;

import java.util.ArrayList;
import java.util.List;

/** Releases complete spoken phrases once, and retains incomplete trailing text. */
public final class StreamingSpeechText {
 private final StringBuilder pending=new StringBuilder();
 private boolean finished;
 public List<String> append(String delta){
  if(finished)throw new IllegalStateException("Reply already finished");
  pending.append(delta);return drain(false);
 }
 public List<String> finish(){
  if(finished)return new ArrayList<>();
  finished=true;return drain(true);
 }
 private List<String> drain(boolean end){
  List<String> chunks=new ArrayList<>();
  while(pending.length()>0){
   int boundary=-1;
   for(int i=0;i<pending.length();i++){
    char c=pending.charAt(i);
    // Wait for the following character so split decimal tokens stay intact.
    if((c=='.'||c=='!'||c=='?'||c=='\n')&&(end||i+1<pending.length())
       &&(i+1==pending.length()||Character.isWhitespace(pending.charAt(i+1)))
       &&i>=20){boundary=i+1;break;}
   }
   if(boundary<0&&pending.length()>260){
    for(int i=220;i>=80;i--)if(Character.isWhitespace(pending.charAt(i))){boundary=i;break;}
   }
   if(boundary<0){if(!end)break;boundary=pending.length();}
   String part=pending.substring(0,boundary).trim();pending.delete(0,boundary);
   if(!part.isEmpty())chunks.add(part);
  }
  return chunks;
 }
}
