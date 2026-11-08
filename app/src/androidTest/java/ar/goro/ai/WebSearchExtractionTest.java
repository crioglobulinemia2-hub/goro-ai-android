package ar.goro.ai;
import android.webkit.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import ar.goro.ai.ui.WebSearchPanel;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class WebSearchExtractionTest {
 @Test public void actualAndroidJavascriptExtractsOnlyVisibleSources()throws Exception{
  CountDownLatch ready=new CountDownLatch(1);AtomicReference<String[]> result=new AtomicReference<>();WebView[] web=new WebView[1];
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   scenario.onActivity(a->{
    web[0]=new WebView(a);a.setContentView(web[0]);web[0].getSettings().setJavaScriptEnabled(true);
    web[0].setWebViewClient(new WebViewClient(){@Override public void onPageFinished(WebView v,String u){v.postVisualStateCallback(1,new WebView.VisualStateCallback(){@Override public void onComplete(long id){v.evaluateJavascript(WebSearchPanel.EXTRACT,json->{result.set(WebSearchPanel.parse(json));ready.countDown();});}});}});
    web[0].loadDataWithBaseURL("https://www.google.com/search?q=fixture","<html><body><div><a href='https://developer.android.com/guide'><h3>Android</h3></a><p>Documentación de Android.</p></div><div style='display:none'><a href='https://hidden.example'><h3>Oculto</h3></a><p>No debe leerse</p></div></body></html>","text/html","UTF-8",null);
   });
   assertTrue(ready.await(15,TimeUnit.SECONDS));assertNotNull(result.get());assertTrue(result.get()[0].contains("developer.android.com/guide"));assertTrue(result.get()[1].contains("Documentación de Android"));assertFalse(result.get()[0].contains("hidden.example"));
   scenario.onActivity(a->web[0].destroy());
  }
 }
}
