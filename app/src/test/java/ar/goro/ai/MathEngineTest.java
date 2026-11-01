package ar.goro.ai;
import org.junit.Test;
import static org.junit.Assert.*;
public class MathEngineTest {
 @Test public void adds(){assertEquals(296450.0,MathEngine.calculate("245000+51450"),0.00001);}
 @Test public void decimals(){assertEquals(3.5,MathEngine.calculate("1,5 + 2"),0.00001);}
 @Test public void negative(){assertEquals(-6,MathEngine.calculate("-2*3"),0.00001);}
 @Test public void divide(){assertEquals(2.5,MathEngine.calculate("5/2"),0.00001);}
 @Test(expected=ArithmeticException.class) public void divideByZero(){MathEngine.calculate("5/0");}
 @Test(expected=IllegalArgumentException.class) public void rejectCode(){MathEngine.calculate("Runtime.exec(1)");}
}
