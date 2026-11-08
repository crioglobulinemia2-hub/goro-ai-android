package ar.goro.ai.core;

import java.text.Normalizer;
import java.util.Locale;

/** Stable personal identity, independent of remote model availability. */
public final class GoroIdentity {
 public static final String INTRODUCTION="Soy Goro, tu asistente a cargo de gestiones especiales, Matías.";
 public static final String CONTEXT="Tu nombre es Goro, se pronuncia como una palabra, no como siglas. "
   +"Sos el asistente a cargo de gestiones especiales de Matías. "
   +"Matías es el usuario; Goro sos vos. No confundas sus identidades. "
   +"Respondé en español rioplatense, con frases naturales y breves, sin leer formatos Markdown. "
   +"Por defecto respondé en una a tres frases; ampliá si Matías pide detalle. ";
 private GoroIdentity(){}
 public static String localReply(String text){
  if(text==null)return null;
  String value=Normalizer.normalize(text,Normalizer.Form.NFD).replaceAll("\\p{M}+","")
      .toLowerCase(Locale.ROOT).replaceAll("[¿?¡!.,]","").trim().replaceAll("\\s+"," ");
  value=value.replaceFirst("^(?:goro |che goro )","");
  if(value.matches("(?:quien sos|quien eres|como te llamas|cual es tu nombre|presentate|decime quien sos|decime tu nombre)"))return INTRODUCTION;
  if(value.matches("(?:quien soy|como me llamo|cual es mi nombre|sabes quien soy)"))return "Sos Matías. Yo soy Goro, tu asistente a cargo de gestiones especiales.";
  return null;
 }
}
