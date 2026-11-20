package ar.goro.ai.core;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Bounded Magpie requests, preserving all text and Unicode character boundaries. */
public final class SpeechTextChunks {
 private SpeechTextChunks(){}
 public static List<String> split(String text,int limit){
  if(text==null||limit<8)throw new IllegalArgumentException("Text and a useful limit are required");
  List<String> chunks=new ArrayList<>();
  BreakIterator characters=BreakIterator.getCharacterInstance(new Locale("es","AR"));
  characters.setText(text);
  int start=0;
  while(start<text.length()){
   int end=Math.min(start+limit,text.length());
   if(end<text.length()){
    if(!characters.isBoundary(end))end=characters.preceding(end);
    if(end<=start)throw new IllegalArgumentException("A character exceeds the speech request limit");
    int best=-1;
    for(int p=end-1;p>=start+limit/2;p--){
     char c=text.charAt(p);
     if(Character.isWhitespace(c)&&characters.isBoundary(p+1)){
      if(best<0)best=p+1;
      if(p>start&&".!?;:\n".indexOf(text.charAt(p-1))>=0){best=p+1;break;}
     }
    }
    if(best>start)end=best;
   }
   chunks.add(text.substring(start,end));start=end;
  }
  return chunks;
 }
}
