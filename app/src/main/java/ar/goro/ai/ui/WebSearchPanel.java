package ar.goro.ai.ui;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.util.LinkedHashSet;

/** Visible web search inside Goro; speech quotes only retrieved search snippets. */
public final class WebSearchPanel {
 public interface Listener {void results(String sources,String speech);void closed();}
 // Read-only DOM extraction. No JavaScript bridge or page text is used as a command.
 public static final String EXTRACT="(()=>{const out=[];for(const h of document.querySelectorAll('h3')){"
   +"if(!h.getClientRects().length)continue;let a=h.closest('a')||h.querySelector('a');if(!a)continue;"
   +"let url=a.href;try{const u=new URL(url);if(u.pathname==='/url')url=u.searchParams.get('q')||u.searchParams.get('url')||url;}catch(e){continue;}"
   +"let p=a,text=h.innerText;for(let i=0;i<5&&p.parentElement;i++){const n=p.parentElement;if(n.querySelectorAll('h3').length>1)break;"
   +"const t=n.innerText||'';if(t.length>1800)break;p=n;if(t.length>text.length)text=t;}"
   +"out.push({title:h.innerText,url:url,snippet:text.slice(0,550)});if(out.length===8)break;}return JSON.stringify(out);})()";
 private final Dialog dialog;
 private final WebView web;
 private final TextView status;
 private final Listener listener;
 private final Handler handler=new Handler(Looper.getMainLooper());
 private boolean disposed,spoken;
 private int pageGeneration;
 public WebSearchPanel(Activity activity,String query,Listener listener){
  this.listener=listener;dialog=new Dialog(activity);
  LinearLayout root=new LinearLayout(activity);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(0xff071525);
  LinearLayout bar=new LinearLayout(activity);
  Button back=new Button(activity);back.setText("Volver a Goro");back.setOnClickListener(v->dialog.dismiss());bar.addView(back);
  Button read=new Button(activity);read.setText("Leer resultados");read.setOnClickListener(v->extract(pageGeneration,true));bar.addView(read);
  root.addView(bar);status=new TextView(activity);status.setTextColor(Color.WHITE);status.setTextSize(15);status.setPadding(18,12,18,12);status.setText("Buscando en internet: "+query);root.addView(status);
  web=new WebView(activity);web.setBackgroundColor(Color.WHITE);
  web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);
  web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);
  web.getSettings().setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW);
  web.setWebViewClient(new WebViewClient(){
   @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return !allowed(r.getUrl());}
   @Override public boolean shouldOverrideUrlLoading(WebView v,String url){return !allowed(Uri.parse(url));}
   private boolean allowed(Uri uri){boolean ok="https".equalsIgnoreCase(uri.getScheme());if(!ok)status.setText("Este enlace no usa HTTPS y no se abrió.");return ok;}
   @Override public void onPageStarted(WebView v,String url,android.graphics.Bitmap icon){pageGeneration++;handler.removeCallbacksAndMessages(null);}
   @Override public void onPageFinished(WebView v,String url){
    if(disposed)return;status.setText("Resultados web. Tocá una fuente para abrirla dentro de Goro.");
    final int ticket=pageGeneration;
    for(int delay:new int[]{400,1400,3000})handler.postDelayed(()->extract(ticket,false),delay);
   }
   @Override public void onReceivedError(WebView v,WebResourceRequest request,android.webkit.WebResourceError error){
    if(request.isForMainFrame())status.setText("No se pudo cargar la búsqueda. Revisá la conexión a internet.");
   }
  });
  root.addView(web,new LinearLayout.LayoutParams(-1,0,1));dialog.setContentView(root);
  dialog.setOnDismissListener(d->{boolean notify=!disposed;dispose();if(notify)listener.closed();});
  dialog.show();dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT);
  web.loadUrl(ar.goro.ai.core.WebSearchCommand.intent(query).getData().toString());
 }
 private void extract(int ticket,boolean force){
  if(disposed||ticket!=pageGeneration||(!force&&spoken))return;
  String current=web.getUrl();Uri uri=current==null?null:Uri.parse(current);
  if(uri==null||!"www.google.com".equalsIgnoreCase(uri.getHost())||!"/search".equals(uri.getPath())){
   if(force)status.setText("Volvé a los resultados de búsqueda para escucharlos.");return;
  }
  web.evaluateJavascript(EXTRACT,value->{
   if(disposed||ticket!=pageGeneration||(!force&&spoken))return;
   String[] parsed=parse(value);
   if(parsed==null){if(force)status.setText("No hay fragmentos legibles todavía. Los resultados o la verificación del buscador están en pantalla.");return;}
   spoken=true;status.setText("Leyendo fragmentos del buscador. Las fuentes se pueden abrir en esta pantalla.");listener.results(parsed[0],parsed[1]);
  });
 }
 public static String[] parse(String encoded){
  try{
   Object decoded=new JSONTokener(encoded).nextValue();if(!(decoded instanceof String))return null;
   JSONArray items=new JSONArray((String)decoded);StringBuilder display=new StringBuilder("Fuentes encontradas en internet:\n");
   StringBuilder speech=new StringBuilder("Te leo los fragmentos que muestra el buscador. ");LinkedHashSet<String> seen=new LinkedHashSet<>();
   int count=0;
   for(int i=0;i<items.length()&&count<5;i++){
    JSONObject item=items.getJSONObject(i);String url=item.optString("url"),title=clean(item.optString("title"),180),snippet=clean(item.optString("snippet"),420);
    Uri u=Uri.parse(url);String host=u.getHost();if(!"https".equalsIgnoreCase(u.getScheme())||host==null||u.getUserInfo()!=null||host.equals("google.com")||host.endsWith(".google.com")||title.isEmpty()||snippet.isEmpty()||!seen.add(url))continue;
    count++;display.append(count).append(". ").append(title).append('\n').append(snippet).append('\n').append(url).append("\n\n");
    if(count<=2)speech.append("Resultado de ").append(host.replaceFirst("^www\\.","")).append(": ").append(snippet).append(". ");
   }
   return count==0?null:new String[]{display.toString().trim(),speech.toString().trim()};
  }catch(Exception error){return null;}
 }
 private static String clean(String value,int max){String s=value.replaceAll("\\s+"," ").trim();return s.length()>max?s.substring(0,max):s;}
 public void close(){dispose();dialog.dismiss();}
 private void dispose(){if(disposed)return;disposed=true;pageGeneration++;handler.removeCallbacksAndMessages(null);web.stopLoading();web.setWebViewClient(new WebViewClient());web.destroy();}
}
