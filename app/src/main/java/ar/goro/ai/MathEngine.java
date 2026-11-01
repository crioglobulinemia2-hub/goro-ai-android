package ar.goro.ai;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
public final class MathEngine {
 private MathEngine(){}
 public static double calculate(String input) {
  if(input==null) throw new IllegalArgumentException("Empty expression");
  Matcher m=Pattern.compile("\\s*(-?\\d+(?:[.,]\\d+)?)\\s*([+*/-])\\s*(-?\\d+(?:[.,]\\d+)?)\\s*").matcher(input);
  if(!m.matches()) throw new IllegalArgumentException("Unsupported expression");
  double a=Double.parseDouble(m.group(1).replace(',','.')),b=Double.parseDouble(m.group(3).replace(',','.'));
  switch(m.group(2)){
   case "+": return a+b;
   case "-": return a-b;
   case "*": return a*b;
   case "/": if(b==0) throw new ArithmeticException("Division by zero");return a/b;
   default:throw new IllegalArgumentException("Unsupported operator");
  }
 }
}
