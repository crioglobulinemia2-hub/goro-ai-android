package ar.goro.ai;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.core.app.ActivityScenario;
import org.junit.Test;
import org.junit.runner.RunWith;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.startsWith;

@RunWith(AndroidJUnit4.class)
public class GoroLaunchSmokeTest {
 @Test public void appLaunchesAndPrimaryVoiceControlsAreVisible(){
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   onView(withText("GORO  IA")).check(matches(isDisplayed()));
   onView(withText("Micrófono · Conversación continua")).check(matches(isDisplayed()));
   onView(withText("Enviar")).check(matches(isDisplayed()));
  }
 }
 @Test public void navigationChangesTheVisibleSection(){
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   onView(withText("Agenda")).perform(click());
   onView(withText(startsWith("Agenda. Consultá tus eventos"))).check(matches(isDisplayed()));
  }
 }
}
