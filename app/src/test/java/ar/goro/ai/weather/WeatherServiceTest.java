package ar.goro.ai.weather;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
public class WeatherServiceTest {
 @Test public void rejectsOutOfRangeCoordinates(){
  WeatherService service=new WeatherService();
  final String[] error={null};
  service.current(120,200,(value,message)->error[0]=message);
  assertNotNull(error[0]);service.close();
 }
 @Test public void rejectsInvalidForecastWindow(){WeatherService service=new WeatherService();final String[] error={null};service.forecast(-91,0,8,(days,message)->error[0]=message);assertNotNull(error[0]);service.close();}
 @Test public void rejectsBlankCity(){
  WeatherService service=new WeatherService();
  final String[] error={null};
  service.geocode(" ",(places,message)->error[0]=message);
  assertNotNull(error[0]);service.close();
 }
}
