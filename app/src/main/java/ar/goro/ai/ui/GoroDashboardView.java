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
import android.widget.ImageView;
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
  void onLinkAccount();
  void onReadGmail();
  void onCalendarPermission();
  void onUnlinkAccount();
  void onWeather();
  void onForecast();
  void onReminders();
  void onConfigureNvidia();
  void onTestNvidia();
  void onConnectZn02();
  void onInspectZn02();
  void onZn02LightOn();
  void onZn02LightOff();
  void onZn02ReadLight();
  void onZn02AudioOn();
  void onZn02AudioOff();
  void onInstallGoroAnimation();
 }
 public final TextView output;
 public final TextView state;
 public final TextView accountLabel;
 private TextView conversationStatus;
 public final FrameLayout avatarStage;
 private final TextView speechBubble;
 private final GoroAvatarView avatar;
 private final TextView agendaValue;
 private final TextView weatherValue;
 private final TextView devicesValue;
 private final TextView nvidiaValue;
 private final TextView voiceValue;
 private final TextView gmailValue;
 private final TextView sectionValue;
 private final TextView clock;
 private final Activity activity;
 private final Actions actions;
 private final GoroUiState ui=new GoroUiState();
 private final boolean tablet;
 private final LinearLayout navigation;
 private final LinearLayout settingsPanel;
 private final LinearLayout devicePanel;
 private LinearLayout heroPane,quickPane,controlsPane;
 private ScrollView mainScrollContainer;
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
  clock=text("",tablet?19:13,true,palette.text);
  clock.setText(new SimpleDateFormat("HH:mm · EEE dd MMM",new Locale("es","AR")).format(new Date()));
  LinearLayout.LayoutParams clockParams=new LinearLayout.LayoutParams(0,dp(52),1f);
  header.addView(clock,clockParams);
  ImageView mark=new ImageView(activity);
  mark.setImageResource(ar.goro.ai.R.drawable.ic_goro_foreground);
  mark.setScaleType(ImageView.ScaleType.FIT_CENTER);
  mark.setPadding(dp(3),dp(3),dp(3),dp(3));
  mark.setBackground(gradient(palette.robotBackdrop,palette.surfaceElevated,dp(15)));
  LinearLayout.LayoutParams markParams=new LinearLayout.LayoutParams(dp(44),dp(44));
  markParams.setMargins(dp(3),0,dp(9),0);
  header.addView(mark,markParams);
  TextView logo=text("GORO   ASISTENTE PERSONAL",tablet?20:14,true,palette.text);
  logo.setLetterSpacing(.025f);
  header.addView(logo);
  addView(header,new LinearLayout.LayoutParams(-1,dp(56)));

  LinearLayout columns=new LinearLayout(activity);
  columns.setOrientation(VERTICAL);
  addView(columns,new LinearLayout.LayoutParams(-1,0,1f));

  navigation=new LinearLayout(activity);navigation.setOrientation(HORIZONTAL);
  navigation.setGravity(Gravity.CENTER);
  for(GoroSection section:new GoroSection[]{GoroSection.HOME,GoroSection.EXPLORE,GoroSection.AUTOMATIONS,GoroSection.SETTINGS}){
   Button item=button(section.label,()->select(section),false);
   LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(0,dp(46),1f);
   np.setMargins(dp(2),dp(2),dp(2),dp(2));
   navigation.addView(item,np);
  }

  LinearLayout mainColumn=new LinearLayout(activity);mainColumn.setOrientation(VERTICAL);
  ScrollView mainScroll=new ScrollView(activity);mainScroll.setFillViewport(true);
  mainScrollContainer=mainScroll;
  mainScroll.addView(mainColumn);
  LinearLayout.LayoutParams central=new LinearLayout.LayoutParams(-1,0,1f);
  columns.addView(mainScroll,central);

  LinearLayout hero=new LinearLayout(activity);hero.setOrientation(VERTICAL);
  heroPane=hero;
  hero.setPadding(dp(12),dp(10),dp(12),dp(12));
  hero.setBackground(gradient(palette.robotBackdrop,palette.surfaceElevated,dp(24)));
  LinearLayout.LayoutParams heroParams=new LinearLayout.LayoutParams(-1,tablet?dp(430):dp(330));
  heroParams.setMargins(dp(8),dp(4),dp(8),dp(8));
  mainColumn.addView(hero,heroParams);
  avatarStage=new FrameLayout(activity);
  avatarStage.setBackground(gradient(palette.robotBackdrop,palette.surface,dp(20)));
  avatar=enableAvatar?new GoroAvatarView(activity,this::onAvatarStatus):null;
  if(avatar!=null)avatarStage.addView(avatar,new FrameLayout.LayoutParams(-1,-1));
  else{TextView testStage=text("Avatar renderer disabled in JVM test",13,false,palette.muted);testStage.setGravity(Gravity.CENTER);avatarStage.addView(testStage,new FrameLayout.LayoutParams(-1,-1));}
  speechBubble=text("GORO · Escuchando y listo para responder",tablet?14:12,true,palette.text);
  speechBubble.setGravity(Gravity.CENTER_VERTICAL);
  speechBubble.setMaxLines(3);
  speechBubble.setPadding(dp(12),dp(10),dp(12),dp(10));
  speechBubble.setBackground(gradient(palette.surface,palette.surfaceElevated,dp(15)));
  speechBubble.setElevation(dp(8));
  speechBubble.setClickable(false);
  FrameLayout.LayoutParams speechParams=new FrameLayout.LayoutParams(dp(tablet?214:166),-2,Gravity.RIGHT|Gravity.BOTTOM);
  speechParams.setMargins(dp(8),dp(8),dp(7),dp(9));
  avatarStage.addView(speechBubble,speechParams);
  hero.addView(avatarStage,new LinearLayout.LayoutParams(-1,0,1f));
  sectionValue=text("Inicio · Panel personal",15,true,palette.text);

  state=text("GORO · Servicios locales",15,true,palette.accentSecondary);
  if(pendingAvatarStatus!=null)state.setText(pendingAvatarStatus);
  LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(34));
  sp.setMargins(dp(12),dp(2),dp(8),0);
  mainColumn.addView(state,sp);

  output=text("GORO IA · Conversación manos libres por ZNP Z02. La conexión se inicia automáticamente.",16,false,palette.text);
  output.setPadding(dp(14),dp(12),dp(14),dp(12));
  output.setBackground(gradient(palette.surface,palette.surfaceElevated,dp(15)));
  LinearLayout.LayoutParams op=new LinearLayout.LayoutParams(-1,-2);
  op.setMargins(dp(8),0,dp(8),dp(12));
  mainColumn.addView(output,op);

  LinearLayout quick=new LinearLayout(activity);quick.setOrientation(HORIZONTAL);quick.setGravity(Gravity.CENTER);
  quickPane=quick;
  Button agendaQuick=button("Agenda",()->actions.onCommand("agenda hoy"),false);
  Button gmailQuick=button("Gmail",()->actions.onReadGmail(),false);
  Button devicesQuick=button("Dispositivos",()->select(GoroSection.DEVICES),false);
  devicesQuick.setTextSize(10f);devicesQuick.setSingleLine(true);
  Button projectsQuick=button("Proyectos",()->select(GoroSection.EXPLORE),false);
  for(Button item:new Button[]{agendaQuick,gmailQuick,devicesQuick,projectsQuick}){
   LinearLayout.LayoutParams qp=new LinearLayout.LayoutParams(0,dp(42),1f);
   qp.setMargins(dp(2),0,dp(2),0);quick.addView(item,qp);
  }
  LinearLayout.LayoutParams quickParams=new LinearLayout.LayoutParams(-1,dp(46));
  quickParams.setMargins(dp(8),0,dp(8),dp(4));mainColumn.addView(quick,quickParams);

  LinearLayout controls=new LinearLayout(activity);
  controlsPane=controls;
  controls.setGravity(Gravity.CENTER);
  conversationStatus=text("Micrófono Z02 · conversación manos libres automática",14,true,palette.accentSecondary);
  conversationStatus.setGravity(Gravity.CENTER);
  conversationStatus.setPadding(dp(14),dp(12),dp(14),dp(12));
  conversationStatus.setBackground(gradient(palette.surface,palette.surfaceElevated,dp(16)));
  controls.addView(conversationStatus,new LinearLayout.LayoutParams(-1,dp(52)));
  LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(52));
  cp.setMargins(dp(8),0,dp(8),dp(8));
  mainColumn.addView(controls,cp);

  weatherValue=text("Sin consultar. Usar ubicación GPS.",14,false,palette.text);
  agendaValue=text("Sin consultar. Vinculá tu cuenta Google.",14,false,palette.text);
  devicesValue=text("ZNP Z02 · Conexión automática pendiente.",14,false,palette.text);
  nvidiaValue=text("NVIDIA NIM: sin configurar",14,false,palette.text);
  voiceValue=text("Magpie Diego: sin conexión verificada.",14,false,palette.text);
  gmailValue=text("Gmail: sin autorización OAuth.",14,false,palette.text);
  accountLabel=text(account==null?"Google Calendar: sin vincular":"Calendar · "+account,12,false,palette.muted);

  settingsPanel=new LinearLayout(activity);settingsPanel.setOrientation(VERTICAL);
  settingsPanel.addView(accountLabel,new LinearLayout.LayoutParams(-1,dp(32)));
  settingsPanel.addView(card("CUENTA GOOGLE",agendaValue),cardParams());
  settingsPanel.addView(button("Vincular cuenta Google",()->actions.onLinkAccount(),false),new LinearLayout.LayoutParams(-1,dp(42)));
  settingsPanel.addView(button("Autorizar Calendar",()->actions.onCalendarPermission(),false),new LinearLayout.LayoutParams(-1,dp(42)));
  settingsPanel.addView(button("Autorizar Gmail",()->actions.onReadGmail(),false),new LinearLayout.LayoutParams(-1,dp(42)));
  settingsPanel.addView(card("NVIDIA NEMOTRON",nvidiaValue),cardParams());
  settingsPanel.addView(card("GMAIL",gmailValue),cardParams());
  settingsPanel.addView(card("MAGPIE DIEGO",voiceValue),cardParams());
  settingsPanel.addView(button("Configurar NVIDIA",()->actions.onConfigureNvidia(),false),new LinearLayout.LayoutParams(-1,dp(42)));
  settingsPanel.addView(button("Verificar NVIDIA",()->actions.onTestNvidia(),false),new LinearLayout.LayoutParams(-1,dp(42)));
  settingsPanel.addView(button("Cambiar tema",()->selectTheme(readTheme(activity)==GoroTheme.Mode.DARK?GoroTheme.Mode.LIGHT:GoroTheme.Mode.DARK),false),new LinearLayout.LayoutParams(-1,dp(42)));
  settingsPanel.setVisibility(GONE);
  mainColumn.addView(settingsPanel,new LinearLayout.LayoutParams(-1,-2));

  devicePanel=new LinearLayout(activity);devicePanel.setOrientation(VERTICAL);
  devicePanel.addView(card("ZNP Z02",devicesValue),cardParams());
  devicePanel.addView(button("Conectar ZNP Z02",()->actions.onConnectZn02(),true),new LinearLayout.LayoutParams(-1,dp(46)));
  devicePanel.addView(text("Audio bidireccional: verificando micrófono RCSP y salida A2DP",12,false,palette.muted),new LinearLayout.LayoutParams(-1,dp(42)));
  devicePanel.addView(text("Conversación manos libres automática cuando Z02 y NVIDIA estén conectados",12,false,palette.muted),new LinearLayout.LayoutParams(-1,dp(42)));
  devicePanel.setVisibility(GONE);
  mainColumn.addView(devicePanel,new LinearLayout.LayoutParams(-1,-2));

  addView(navigation,new LinearLayout.LayoutParams(-1,dp(52)));
  select(GoroSection.HOME);
 }

 private void select(GoroSection section){
  ui.section(section);
  sectionValue.setText(section.label+" · GORO IA");
  for(int n=0;n<navigation.getChildCount();n++){
   Button item=(Button)navigation.getChildAt(n);
   boolean selected=GoroSection.values()[n]==section;
   int front=selected?Color.WHITE:palette.text;
   int first=selected?palette.accent:palette.surface;
   int last=selected?palette.accentSecondary:palette.surfaceElevated;
   item.setTextColor(front);
   GradientDrawable fill=gradient(first,last,dp(14));
   GradientDrawable mask=new GradientDrawable();
   mask.setColor(Color.WHITE);mask.setCornerRadius(dp(14));
   item.setBackground(new RippleDrawable(ColorStateList.valueOf(palette.accentSecondary),fill,mask));
   item.setElevation(selected?dp(6):dp(2));
  }
  settingsPanel.setVisibility(section==GoroSection.SETTINGS?VISIBLE:GONE);
  devicePanel.setVisibility(section==GoroSection.DEVICES?VISIBLE:GONE);
  boolean utilityPage=section==GoroSection.SETTINGS||section==GoroSection.DEVICES;
  heroPane.setVisibility(utilityPage?GONE:VISIBLE);
  quickPane.setVisibility(utilityPage?GONE:VISIBLE);
  controlsPane.setVisibility(utilityPage?GONE:VISIBLE);
  if(mainScrollContainer!=null)mainScrollContainer.post(()->mainScrollContainer.scrollTo(0,0));
  switch(section){
   case HOME: output.setText("GORO IA · Conversación manos libres por ZNP Z02. La conexión se inicia automáticamente.");break;
   case EXPLORE: output.setText("Explorar proyectos. Pedime que retomemos cualquiera de tus proyectos.");break;
   case DEVICES: output.setText("ZNP Z02. GORO busca el dispositivo, autentica RCSP y prepara el micrófono y el audio Bluetooth.");break;
   case AUTOMATIONS: output.setText("Automatizaciones. Pedime recordatorios, alarmas o temporizadores en lenguaje natural.");actions.onReminders();break;
   case SETTINGS: output.setText("Ajustes y conexiones. Configurá las cuentas y servicios que quieras vincular.");break;
  }
 }
 public void showAgenda(String value){agendaValue.setText(value==null?"Sin datos.":value);}
 public void showWeather(String value){weatherValue.setText(value==null?"Sin datos.":value);}
 public void showDeviceStatus(String value){devicesValue.setText(value==null?"Sin datos de dispositivo.":value);}
 public void setVoiceActive(boolean active){
  String status=active?"Micrófono Z02 activo · conversación manos libres":"Micrófono Z02 · esperando conexión automática";
  voiceValue.setText(status);
  if(conversationStatus!=null)conversationStatus.setText(status);
  if(state!=null)state.setText(active?"GORO · Escuchando desde Z02":"GORO · Conversación automática");
 }
 public void showVoiceStatus(String value){
  String detail=value==null?"Voz no disponible":value;
  voiceValue.setText(detail);
  state.setText(detail.contains("falta API key")?"Configurá NVIDIA en Ajustes para conversar":
      detail.contains("confirmó")?"Voz Diego disponible":"Voz pendiente de conexión · revisá Ajustes");
 }
 private void onAvatarStatus(String value){
  pendingAvatarStatus=value;
  if(state!=null&&value!=null&&value.startsWith("Error"))state.setText("Robot 3D no disponible");
 }
 public void showAvatarMood(GoroAvatarView.Mood mood){if(avatar!=null)avatar.setMood(mood);}
 public void avatarSpeak(String spoken,byte[] audio){if(avatar!=null)avatar.startSpeaking(spoken,audio);}
 public void showAssistantMessage(String value){
  if(value==null||value.trim().isEmpty())return;
  String message=value.trim().replace('\n',' ');
  speechBubble.setText(message.length()>104?message.substring(0,101)+"…":message);
 }
 public void showGmailStatus(String value){gmailValue.setText(value==null?"Gmail no disponible":value);}
 public void showNvidiaStatus(String value){nvidiaValue.setText(value==null?"NVIDIA NIM: sin estado":value);}
 private static GoroTheme.Mode readTheme(Activity a){
  String saved=a.getSharedPreferences("goro_ui",Activity.MODE_PRIVATE).getString("theme_mode",null);
  if(saved==null){
   android.content.SharedPreferences prefs=a.getSharedPreferences("goro_ui",Activity.MODE_PRIVATE);
   if(prefs.contains("dark"))return prefs.getBoolean("dark",true)?GoroTheme.Mode.DARK:GoroTheme.Mode.LIGHT;
   return GoroTheme.Mode.DARK;
  }
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