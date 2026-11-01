package ar.goro.ai.tools;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Deterministic Spanish command parser for local Android tools. */
public final class AssistantCommandParser {
 public static final class Alarm { public final int hour,minute; Alarm(int h,int m){hour=h;minute=m;} }
 public static final class Timer { public final int seconds; Timer(int s){seconds=s;} }
 private AssistantCommandParser(){}
 public static Alarm alarm(String input){
  if(input==null)return null;String s=input.toLowerCase(new Locale("es","AR"));
  Matcher m=Pattern.compile("(?:a las |para las |las )(\\d{1,2})(?:[:.](\\d{2}))?").matcher(s);
  if(!m.find())return null;
  int h=Integer.parseInt(m.group(1)),min=m.group(2)==null?0:Integer.parseInt(m.group(2));
  if(h<0||h>23||min<0||min>59)return null;
  return new Alarm(h,min);
 }
 public static Timer timer(String input){
  if(input==null)return null;String s=input.toLowerCase(new Locale("es","AR"));
  Matcher m=Pattern.compile("(\\d+)\\s*(segundos?|seg|minutos?|min|horas?|hs?)").matcher(s);
  if(!m.find())return null;
  long n=Long.parseLong(m.group(1));String unit=m.group(2);
  long seconds=n*(unit.startsWith("hora")||unit.equals("hs")?3600:unit.startsWith("min")?60:1);
  if(seconds<1||seconds>86400)return null;
  return new Timer((int)seconds);
 }
}
