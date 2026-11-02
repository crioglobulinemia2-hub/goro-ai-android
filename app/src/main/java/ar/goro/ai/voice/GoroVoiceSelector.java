package ar.goro.ai.voice;

import android.speech.tts.Voice;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Selects an explicitly male Spanish voice; never silently falls back to a female default. */
public final class GoroVoiceSelector {
 private static final Pattern MALE=Pattern.compile("(^|[^a-z])(male|masculine|masculino)([^a-z]|$)");
 private GoroVoiceSelector(){}
 public static final class Candidate {
  public final String name;
  public final Locale locale;
  public final boolean networkRequired;
  public Candidate(String name,Locale locale,boolean networkRequired){
   this.name=name==null?"":name;this.locale=locale;this.networkRequired=networkRequired;
  }
 }
 public static boolean isMaleSpanishVoice(String name,Locale locale){
  if(locale==null||!"es".equalsIgnoreCase(locale.getLanguage()))return false;
  return MALE.matcher(name==null?"":name.toLowerCase(Locale.ROOT)).find();
 }
 public static Candidate chooseCandidate(Collection<Candidate> available){
  if(available==null||available.isEmpty())return null;
  List<Candidate> candidates=new ArrayList<>();
  for(Candidate c:available)if(c!=null&&isMaleSpanishVoice(c.name,c.locale))candidates.add(c);
  candidates.sort(Comparator.comparingInt((Candidate c)->localeRank(c.locale))
    .thenComparingInt(c->c.networkRequired?1:0).thenComparing(c->c.name));
  return candidates.isEmpty()?null:candidates.get(0);
 }
 public static Voice select(Set<Voice> available){
  if(available==null||available.isEmpty())return null;
  List<Candidate> candidates=new ArrayList<>();
  for(Voice v:available){
   if(v==null||v.getLocale()==null)continue;
   String name=v.getName()==null?"":v.getName();
   if(!isMaleSpanishVoice(name,v.getLocale())&&v.getFeatures()!=null){
    for(String feature:v.getFeatures())if(isMaleSpanishVoice(feature,v.getLocale())){name=feature;break;}
   }
   if(isMaleSpanishVoice(name,v.getLocale()))candidates.add(new Candidate(name,v.getLocale(),v.isNetworkConnectionRequired()));
  }
  Candidate selected=chooseCandidate(candidates);
  if(selected==null)return null;
  for(Voice v:available){
   if(v!=null&&v.getLocale()!=null&&v.getLocale().equals(selected.locale)){
    String name=v.getName()==null?"":v.getName();
    if(name.equals(selected.name)||(v.getFeatures()!=null&&v.getFeatures().contains(selected.name)))return v;
   }
  }
  return null;
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
