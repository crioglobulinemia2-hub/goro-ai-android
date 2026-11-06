package ar.goro.ai.core;

import android.content.Intent;
import android.net.Uri;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Explicit web requests go to a real search engine, without a model or paid API. */
public final class WebSearchCommand {
 private static final Pattern COMMAND=Pattern.compile(
   "(?iu)^(?:(?:che\\s+)?goro[, ]+)?(?:busc[aá]|buscar|buscame|buscáme|busque|busques)\\s+"
   +"(?:(?:por|en)\\s+(?:internet|la web|google)\\s+(.+)|(.+?)\\s+(?:por|en)\\s+(?:internet|la web|google))[.!?]*$");
 private WebSearchCommand(){}
 public static String query(String text){
  if(text==null)return null;
  Matcher m=COMMAND.matcher(text.trim());if(!m.matches())return null;
  String q=(m.group(1)!=null?m.group(1):m.group(2)).trim();
  return q.isEmpty()?null:q;
 }
 public static Intent intent(String query){
  if(query==null||query.trim().isEmpty())throw new IllegalArgumentException("Empty search");
  return new Intent(Intent.ACTION_VIEW,new Uri.Builder().scheme("https")
    .authority("www.google.com").path("search").appendQueryParameter("q",query.trim()).build());
 }
}
