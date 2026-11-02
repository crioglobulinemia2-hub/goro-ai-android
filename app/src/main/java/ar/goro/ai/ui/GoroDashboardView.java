package ar.goro.ai.ui;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import ar.goro.ai.avatar.GoroAvatarView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Live, native Android dashboard based on the approved GORO reference.
 * Real widgets and buttons: never assert calendar, weather or device values that were not read.
 * The final full-body rigged GLB remains mandatory; the empty avatar stage is intentionally
 * explicit rather than presenting a flat bitmap or primitive as a finished humanoid.
 */
public final class GoroDashboardView extends LinearLayout {
 public interface Actions {
  void onCommand(String command);
  void onVoiceSession();
  void onLinkAccount();
  void onCalendarPermission();
  void onUnlinkAccount();
  void onWeather();
  void onForecast();
  void onReminders();
  void onBleScan();
  void onConfigureNvidia();
  void onTestNvidia();
  void onConnectZnp();
  void onZnpLightOn();
  void onZnpLightOff();
  void onZnpReadLight();
  void onInstallGoroAnimation();
 }
 public final TextView output;
 public final TextView state;
 public final TextView accountLabel;
 public final EditText command;
 public final Button talk;
 public final FrameLayout avatarStage;
 private final TextView agendaValue;
 private final TextView weatherValue;
 private final TextView devicesValue;
 private final TextView nvidiaValue;
 private final TextView sectionValue;
 private final TextView clock;
 private final Activity activity;
 private final Actions actions;
 private final GoroUiState ui=new GoroUiState();
 private final boolean tablet;
 private final LinearLayout navigation;
 private GoroTheme palette;
 private String pendingAvatarStatus;

 public GoroDashboardView(Activity activity, Actions actions, String account) { this(activity,actions,account,true); }
 public GoroDashboardView(Activity activity, Actions actions, String account, boolean enableAvatar) {
  super(activity);
  this.activity=activity;this.actions=actions;
  tablet=activity.getResources().getConfiguration().smallestScreenWidthDp>=600;
  palette=GoroTheme.of(readTheme(activity));
  setOrientation(VERTICAL);setBackgroundColor(palette.background);
  setPadding(dp(12),dp(12),dp(12),dp(8));

  LinearLayout header=new LinearLayout(activity);header.setGravity(Gravity.CENTER_VERTICAL);
  header.setOrientation(HORIZONTAL);
  clock=text("",tablet?21:17,true,palette.text);
  clock.setText(new SimpleDateFormat("HH:mm · EEE dd MMM",new Locale("es","AR")).format(new Date()));
  LinearLayout.LayoutParams clockParams=new LinearLayout.LayoutParams(0,dp(52),1f);
  header.addView(clock,clockParams);
  TextView logo=text("GORO   ASISTENTE PERSONAL",tablet?22:16,true,palette.text);
  header.addView(logo);
  addView(header,new LinearLayout.LayoutParams(-1,dp(56)));

  LinearLayout columns=new LinearLayout(activity);
  columns.setOrientation(tablet?HORIZONTAL:VERTICAL);
  addView(columns,new LinearLayout.LayoutParams(-1,0,1f));

  navigation=new LinearLayout(activity);navigation.setOrientation(tablet?VERTICAL:HORIZONTAL);
  if(tablet) {
   ScrollView navScroller=new ScrollView(activity);
   navScroller.setFillViewport(true);
   navScroller.addView(navigation);
   columns.addView(navScroller,new LinearLayout.LayoutParams(dp(170),-1));
  } else {
   HorizontalScrollView navScroller=new HorizontalScrollView(activity);
   navScroller.setHorizontalScrollBarEnabled(false);
   navScroller.addView(navigation);
   columns.addView(navScroller,new LinearLayout.LayoutParams(-1,dp(52)));
  }
  for(GoroSection section:GoroSection.values()){
   Button item=button(section.label,()->select(section),false);
   LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(tablet?-1:-2,dp(42));
   np.setMargins(dp(3),dp(3),dp(3),dp(3));
   navigation.addView(item,np);
  }

  LinearLayout mainColumn=new LinearLayout(activity);mainColumn.setOrientation(VERTICAL);
  ScrollView mainScroll=new ScrollView(activity);mainScroll.setFillViewport(true);
  mainScroll.addView(mainColumn);
  LinearLayout.LayoutParams central=tablet?new LinearLayout.LayoutParams(0,-1,1f):new LinearLayout.LayoutParams(-1,0,1f);
  columns.addView(mainScroll,central);

  LinearLayout hero=new LinearLayout(activity);hero.setOrientation(VERTICAL);
  hero.setPadding(dp(12),dp(10),dp(12),dp(12));
  hero.setBackground(gradient(palette.robotBackdrop,palette.surfaceElevated,dp(24)));
  LinearLayout.LayoutParams heroParams=new LinearLayout.LayoutParams(-1,tablet?dp(340):dp(260));
  heroParams.setMargins(dp(8),dp(4),dp(8),dp(8));
  mainColumn.addView(hero,heroParams);
  TextView title=text("GORO  ·  ASISTENTE PERSONAL",tablet?18:16,true,palette.text);
  hero.addView(title,new LinearLayout.LayoutParams(-1,dp(36)));
  avatarStage=new FrameLayout(activity);
  if(enableAvatar){avatarStage.addView(new GoroAvatarView(activity,this::onAvatarStatus),
      new FrameLayout.LayoutParams(-1,-1));}
  else{TextView testStage=text("Avatar renderer disabled in JVM test",13,false,palette.muted);testStage.setGravity(Gravity.CENTER);avatarStage.addView(testStage,new FrameLayout.LayoutParams(-1,-1));}
  hero.addView(avatarStage,new LinearLayout.LayoutParams(-1,0,1f));
  sectionValue=text("Inicio · Panel personal",15,true,palette.text);
  hero.addView(sectionValue,new LinearLayout.LayoutParams(-1,dp(30)));

  state=text("GORO · Servicios locales",15,true,palette.accentSecondary);
  if(pendingAvatarStatus!=null)state.setText(pendingAvatarStatus);
  LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(34));
  sp.setMargins(dp(12),dp(2),dp(8),0);
  mainColumn.addView(state,sp);

  output=text("Las funciones locales están disponibles. NVIDIA remoto y el avatar 3D todavía no están conectados.",16,false,palette.text);
  output.setPadding(dp(14),dp(12),dp(14),dp(12));
  output.setBackground(gradient(palette.surface,palette.surfaceElevated,dp(15)));
  LinearLayout.LayoutParams op=new LinearLayout.LayoutParams(-1,-2);
  op.setMargins(dp(8),0,dp(8),dp(12));
  mainColumn.addView(output,op);

  command=new EditText(activity);
  command.setHint("Escribí o dictá una orden");
  command.setTextColor(palette.text);
  command.setHintTextColor(palette.muted);
  command.setSingleLine(false);
  command.setMinLines(1);
  command.setMaxLines(3);
  command.setBackground(gradient(palette.surface,palette.surface,dp(16)));
  command.setPadding(dp(16),dp(10),dp(16),dp(10));
  LinearLayout.LayoutParams cmdParams=new LinearLayout.LayoutParams(-1,-2);
  cmdParams.setMargins(dp(8),0,dp(8),dp(10));
  mainColumn.addView(command,cmdParams);

  LinearLayout controls=new LinearLayout(activity);
  controls.setGravity(Gravity.CENTER);
  talk=button("Micrófono · Conversación continua",()->actions.onVoiceSession(),true);
  controls.addView(talk,new LinearLayout.LayoutParams(0,dp(52),1f));
  Button send=button("Enviar",()->actions.onCommand(command.getText().toString().trim()),false);
  controls.addView(send,new LinearLayout.LayoutParams(dp(102),dp(52)));
  LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(52));
  cp.setMargins(dp(8),0,dp(8),dp(8));
  mainColumn.addView(controls,cp);

  LinearLayout tools=new LinearLayout(activity);
  tools.setOrientation(VERTICAL);
  tools.setPadding(dp(9),dp(6),dp(9),dp(4));
  if(tablet){ScrollView toolsScroll=new ScrollView(activity);toolsScroll.addView(tools);columns.addView(toolsScroll,new LinearLayout.LayoutParams(dp(244),-1));}
  else mainColumn.addView(tools,new LinearLayout.LayoutParams(-1,-2));
  weatherValue=text("Sin consultar. Usar ubicación GPS.",14,false,palette.text);
  agendaValue=text("Sin consultar. Vinculá tu cuenta Google.",14,false,palette.text);
  devicesValue=text("Sin datos de vinculación verificados.",14,false,palette.text);
  nvidiaValue=text("NVIDIA NIM: sin configurar",14,false,palette.text);

  LinearLayout weatherCard=card("CLIMA LOCAL",weatherValue);
  tools.addView(weatherCard,cardParams());
  tools.addView(button("Consultar GPS",()->actions.onWeather(),false),new LinearLayout.LayoutParams(-1,dp(46)));
  tools.addView(button("Pronóstico · 3 días",()->actions.onForecast(),false),new LinearLayout.LayoutParams(-1,dp(46)));
  LinearLayout agendaCard=card("AGENDA DE HOY",agendaValue);
  tools.addView(agendaCard,cardParams());
  accountLabel=text(account==null?"Google Calendar: sin vincular":"Calendar · "+account,12,false,palette.muted);
  tools.addView(accountLabel,new LinearLayout.LayoutParams(-1,dp(42)));
  tools.addView(button("Vincular cuenta Google",()->actions.onLinkAccount(),false),new LinearLayout.LayoutParams(-1,dp(44)));
  tools.addView(button("Autorizar Google Calendar",()->actions.onCalendarPermission(),false),new LinearLayout.LayoutParams(-1,dp(44)));
  tools.addView(button("Desvincular cuenta",()->actions.onUnlinkAccount(),false),new LinearLayout.LayoutParams(-1,dp(44)));
  tools.addView(button("Consultar agenda",()->actions.onCommand("agenda hoy"),false),new LinearLayout.LayoutParams(-1,dp(46)));
  tools.addView(card("INTELIGENCIA NEMOTRON",nvidiaValue),cardParams());
  tools.addView(button("Configurar clave NVIDIA",()->actions.onConfigureNvidia(),false),new LinearLayout.LayoutParams(-1,dp(44)));
  tools.addView(button("Probar conexión Nemotron",()->actions.onTestNvidia(),false),new LinearLayout.LayoutParams(-1,dp(44)));
  tools.addView(card("DISPOSITIVOS",devicesValue),cardParams());
  tools.addView(button("Inspeccionar BLE Z02/N02",()->actions.onBleScan(),false),new LinearLayout.LayoutParams(-1,dp(44)));
  tools.addView(button("Conectar ZNP por RCSP JieLi",()->actions.onConnectZnp(),true),new LinearLayout.LayoutParams(-1,dp(48)));
  tools.addView(button("Encender luz del estuche",()->actions.onZnpLightOn(),false),new LinearLayout.LayoutParams(-1,dp(44)));
  tools.addView(button("Apagar luz del estuche",()->actions.onZnpLightOff(),false),new LinearLayout.LayoutParams(-1,dp(44)));
  tools.addView(button("Consultar estado de luz",()->actions.onZnpReadLight(),false),new LinearLayout.LayoutParams(-1,dp(44)));
  tools.addView(button("Enviar animación GORO al visor",()->actions.onInstallGoroAnimation(),true),new LinearLayout.LayoutParams(-1,dp(48)));
  TextView warning=text("Datos y conexión: solo estados verificados",12,false,palette.muted);
  tools.addView(warning);

  LinearLayout featureRow=new LinearLayout(activity);featureRow.setOrientation(HORIZONTAL);
  for(GoroTheme.Mode mode:GoroTheme.Mode.values()){
   String name=mode==GoroTheme.Mode.LIGHT?"Claro":mode==GoroTheme.Mode.DARK?"Oscuro":"Profesional";
   featureRow.addView(button(name,()->selectTheme(mode),false),new LinearLayout.LayoutParams(0,dp(48),1f));
  }
  addView(featureRow,new LinearLayout.LayoutParams(-1,dp(48)));
  select(GoroSection.HOME);
 }

 private void select(GoroSection section){
  ui.section(section);
  sectionValue.setText(section.label+" · GORO AI");
  switch(section){
   case HOME: output.setText("Inicio. Conversación, agenda, clima y dispositivos, sin valores inventados.");break;
   case AGENDA: output.setText("Agenda. Consultá tus eventos, o pedile a GORO que los cree, cambie o elimine, con confirmación.");break;
   case ALARMS: output.setText("Alarmas. Pedime: alarma a las 07:30, o temporizador de 10 minutos.");break;
   case WEATHER: output.setText("Clima. La consulta usa tu ubicación real solo con tu permiso.");actions.onWeather();break;
   case MEMORY: output.setText("Memoria local. Pedí: mis recuerdos, o guardá un dato diciendo acordate que...");break;
   case DEVICES: output.setText("Dispositivos. No se indica conexión si Android no la confirmó.");break;
   case AUTOMATIONS: output.setText("Automatizaciones. Programá recordatorios en lenguaje natural.");actions.onReminders();break;
   case SETTINGS: output.setText("Ajustes. Podés vincular una cuenta Google, autorizar calendario y cambiar el tema.");break;
  }
 }
 public void showAgenda(String value){agendaValue.setText(value==null?"Sin datos.":value);}
 public void showWeather(String value){weatherValue.setText(value==null?"Sin datos.":value);}
 public void showDeviceStatus(String value){devicesValue.setText(value==null?"Sin datos de dispositivo.":value);}
 public void setVoiceActive(boolean active){talk.setText(active?"Detener conversación":"Micrófono · Conversación continua");}
 public void showVoiceStatus(String value){state.setText(value==null?"Voz no disponible":value);}
 private void onAvatarStatus(String value){pendingAvatarStatus=value;if(state!=null)state.setText(value);}
 public void showNvidiaStatus(String value){nvidiaValue.setText(value==null?"NVIDIA NIM: sin estado":value);}
 private static GoroTheme.Mode readTheme(Activity a){
  String saved=a.getSharedPreferences("goro_ui",Activity.MODE_PRIVATE).getString("theme_mode",null);
  if(saved==null){return a.getSharedPreferences("goro_ui",Activity.MODE_PRIVATE).getBoolean("dark",false)?GoroTheme.Mode.DARK:GoroTheme.Mode.LIGHT;}
  try{return GoroTheme.Mode.valueOf(saved);}catch(Exception e){return GoroTheme.Mode.LIGHT;}
 }
 private void selectTheme(GoroTheme.Mode mode){
  activity.getSharedPreferences("goro_ui",Activity.MODE_PRIVATE).edit().putString("theme_mode",mode.name()).apply();
  activity.recreate();
 }
 private LinearLayout card(String heading,TextView value){
  LinearLayout card=new LinearLayout(activity);card.setOrientation(VERTICAL);
  card.setPadding(dp(14),dp(12),dp(14),dp(10));
  card.setBackground(gradient(palette.surface,palette.surfaceElevated,dp(18)));
  TextView label=text(heading,14,true,palette.accent);
  card.addView(label,new LinearLayout.LayoutParams(-1,dp(34)));
  card.addView(value,new LinearLayout.LayoutParams(-1,-2));
  return card;
 }
 private LinearLayout.LayoutParams cardParams(){
  LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
  lp.setMargins(dp(3),dp(7),dp(3),dp(7));return lp;
 }
 private Button button(String label,Runnable click,boolean primary){
  Button b=new Button(activity);
  b.setText(label);b.setAllCaps(false);b.setTextSize(12);
  b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
  b.setTextColor(primary?Color.WHITE:palette.text);
  b.setGravity(Gravity.CENTER);
  b.setMinHeight(dp(44));
  b.setPadding(dp(12),dp(8),dp(12),dp(8));
  b.setFocusable(true);
  b.setClickable(true);
  b.setContentDescription(label);
  int first=primary?palette.accentSecondary:palette.surface;
  int second=primary?palette.accent:palette.surfaceElevated;
  GradientDrawable content=gradient(first,second,dp(15));
  GradientDrawable mask=new GradientDrawable();
  mask.setColor(Color.WHITE);mask.setCornerRadius(dp(15));
  int rippleColor=Color.argb(82,Color.red(palette.accentSecondary),
      Color.green(palette.accentSecondary),Color.blue(palette.accentSecondary));
  b.setBackground(new RippleDrawable(ColorStateList.valueOf(rippleColor),content,mask));
  b.setElevation(dp(2));
  b.setOnTouchListener((view,event)->{
   if(event.getActionMasked()==MotionEvent.ACTION_DOWN){
    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    view.animate().cancel();
    view.setScaleX(0.965f);view.setScaleY(0.965f);
    view.setTranslationZ(dp(2));
   }else if(event.getActionMasked()==MotionEvent.ACTION_UP
       ||event.getActionMasked()==MotionEvent.ACTION_CANCEL){
    view.animate().scaleX(1f).scaleY(1f).translationZ(0f).setDuration(125).start();
   }
   return false;
  });
  b.setOnClickListener(v->click.run());
  return b;
 }
 public int navigationItemCount(){return navigation.getChildCount();}

 private TextView text(String label,int size,boolean strong,int color){
  TextView view=new TextView(activity);
  view.setText(label);view.setTextSize(size);view.setTextColor(color);
  if(strong)view.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
  view.setGravity(Gravity.CENTER_VERTICAL);
  return view;
 }
 private GradientDrawable gradient(int first,int second,int radius){
  GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{first,second});
  d.setCornerRadius(radius);d.setStroke(dp(1),palette.border);
  return d;
 }
 private int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
}