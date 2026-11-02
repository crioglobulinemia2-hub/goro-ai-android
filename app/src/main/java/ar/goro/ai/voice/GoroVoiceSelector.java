package ar.goro.ai.voice;

import android.speech.tts.Voice;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Selects an explicitly male Spanish voice; never silently falls back to a female default. */
public final class GoroVoiceSelector {
 private static final Pattern MALE=Pattern.compile("(^|[^a-z])(male|masculine|masculino)([^a-z]|$)");
 private GoroVoiceSelector(){}
 public static Voice select(Set<Voice> available){
  if(available==null||available.isEmpty())return null;
  List<Voice> candidates=new ArrayList<>();
  for(Voice voice:available){
   if(voice==null||voice.getLocale()==null)continue;
   if(!"es".equalsIgnoreCase(voice.getLocale().getLanguage()))continue;
   String name=voice.getName()==null?"":voice.getName().toLowerCase(Locale.ROOT);
   boolean marked=MALE.matcher(name).find();
   if(!marked && voice.getFeatures()!=null){
    for(String feature:voice.getFeatures()){
     if(feature!=null && MALE.matcher(feature.toLowerCase(Locale.ROOT)).find()){marked=true;break;}
    }
   }
   if(marked)candidates.add(voice);
  }
  candidates.sort(Comparator
    .comparingInt((Voice v)->localeRank(v.getLocale()))
    .thenComparingInt(v->v.isNetworkConnectionRequired()?1:0)
    .thenComparing(v->v.getName()==null?"":v.getName()));
  return candidates.isEmpty()?null:candidates.get(0);
 }
 private static int localeRank(Locale locale){
  String tag=locale.toLanguageTag().toLowerCase(Locale.ROOT);
  if("es-ar".equals(tag))return 0;
  if("es-us".equals(tag))return 1;
  if("es-mx".equals(tag))return 2;
  if("es-es".equals(tag))return 3;
  return 4;
 }
}
