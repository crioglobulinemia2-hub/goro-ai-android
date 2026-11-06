package ar.goro.ai.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import ar.goro.ai.R;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import ar.goro.ai.avatar.GoroAvatarView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Native, connected GORO interface. Display values come from the activity's services. */
public final class GoroDashboardView extends LinearLayout {
 public interface Actions {
  void onCommand(String command); void onLinkAccount(); void onReadGmail();
  void onCalendarPermission(); void onUnlinkAccount(); void onWeather();
  void onForecast(); void onReminders(); void onConfigureNvidia(); void onTestNvidia(); default void onImportNvidiaConfig(){}
  void onConnectZn02(); default void onConnectZnpModel(String model){onConnectZn02();} default void onDisconnectZnp(){} void onInspectZn02(); default void onInspectZnpModel(String model){onInspectZn02();} void onZn02LightOn(); void onZn02LightOff();
  void onZn02ReadLight(); void onZn02AudioOn(); void onZn02AudioOff(); void onInstallGoroAnimation(); default void onInstallGoroAnimation(String model){onInstallGoroAnimation();}
  default void onToggleListening(){}
 }
 private static final int BG=0xff030c17,SURFACE=0xee091d30,EDGE=0xff25465f;
 private static final int TEXT=0xffeef8ff,MUTED=0xff91abc3,CYAN=0xff2bd8ff;
 public final TextView output,state,accountLabel;
 public final FrameLayout avatarStage;
 private final Activity activity;
 private final Actions actions;
 private final boolean tablet;
 private final GoroAvatarView avatar;
 private final FrameLayout body,home;
 private final ScrollView pages,homeScroll;
 private final LinearLayout pageContent,navigation;
 private final TextView title,clock,date,weather,agenda,homeDevices,speechBubble,conversationStatus;
 private final TextView agendaValue,gmailValue,devicesValue,nvidiaValue,voiceValue;
 private final Button back,microphone,listeningToggle;
 private final TextView listeningLabel;
 private boolean listeningPaused=true;
 private final LinearLayout agendaPanel,gmailPanel,devicePanel,settingsPanel,explorePanel,automationPanel;
 private final GoroSection[] primary={GoroSection.HOME,GoroSection.EXPLORE,GoroSection.AUTOMATIONS,GoroSection.SETTINGS};
 private final Handler handler=new Handler(Looper.getMainLooper());
 private GoroSection selected=GoroSection.HOME;
 private boolean showTranscript;
 private final Runnable clockTick=new Runnable(){public void run(){Date now=new Date();clock.setText(new SimpleDateFormat("HH:mm",new Locale("es","AR")).format(now));date.setText(new SimpleDateFormat("EEE d MMM",new Locale("es","AR")).format(now));handler.postDelayed(this,1000);}};
 public GoroDashboardView(Activity a,Actions actions,String account){this(a,actions,account,true);}
 public GoroDashboardView(Activity a,Actions actions,String account,boolean enableAvatar){
  super(a);activity=a;this.actions=actions;tablet=getResources().getConfiguration().smallestScreenWidthDp>=600;
  setOrientation(VERTICAL);setBackgroundColor(BG);setPadding(dp(tablet?24:12),dp(5),dp(tablet?24:12),dp(8));
  final int baseLeft=getPaddingLeft(),baseTop=getPaddingTop(),baseRight=getPaddingRight(),baseBottom=getPaddingBottom();
  androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(this,(v,insets)->{
   androidx.core.graphics.Insets bars=insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()|androidx.core.view.WindowInsetsCompat.Type.displayCutout());
   v.setPadding(baseLeft+bars.left,baseTop+bars.top,baseRight+bars.right,baseBottom+bars.bottom);
   return insets;
  });
  LinearLayout header=new LinearLayout(a);header.setGravity(Gravity.CENTER_VERTICAL);
  back=iconButton("","menu",()->{if(selected!=GoroSection.HOME||showTranscript)select(GoroSection.HOME);else select(GoroSection.SETTINGS);},false,22);
  back.setContentDescription("Menú y volver");header.addView(back,new LinearLayout.LayoutParams(dp(46),dp(46)));
  title=text("GORO IA",23,true,TEXT);title.setGravity(Gravity.CENTER);title.setLetterSpacing(.09f);title.setShadowLayer(dp(8),0,0,0x8058bbff);
  header.addView(title,new LinearLayout.LayoutParams(0,dp(52),1));
  Button deviceShortcut=iconButton("","devices",()->select(GoroSection.DEVICES),false,23);deviceShortcut.setContentDescription("Mis dispositivos");header.addView(deviceShortcut,new LinearLayout.LayoutParams(dp(46),dp(46)));
  addView(header,new LinearLayout.LayoutParams(-1,dp(56)));
  LinearLayout voiceBar=new LinearLayout(a);voiceBar.setGravity(Gravity.CENTER_VERTICAL);
  listeningLabel=text("Escucha pausada",14,true,MUTED);
  voiceBar.addView(listeningLabel,new LinearLayout.LayoutParams(0,dp(44),1));
  listeningLabel.setGravity(Gravity.CENTER_VERTICAL);
  listeningToggle=iconButton("Activar escucha","mic",()->actions.onToggleListening(),true,18);
  listeningToggle.setTextSize(13);listeningToggle.setCompoundDrawables(null,null,null,null);
  voiceBar.addView(listeningToggle,new LinearLayout.LayoutParams(dp(156),dp(44)));
  addView(voiceBar,new LinearLayout.LayoutParams(-1,dp(48)));
  View line=new View(a);line.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{0x002bd8ff,0x905cb8ef,0x002bd8ff}));addView(line,new LinearLayout.LayoutParams(-1,dp(1)));
  body=new FrameLayout(a);addView(body,new LinearLayout.LayoutParams(-1,0,1));
  // Preserve the scene and reachable controls in landscape and split screen.
  // A fixed-height viewport otherwise collapses the avatar behind the footer.
  homeScroll=new ScrollView(a);homeScroll.setFillViewport(true);homeScroll.setVerticalScrollBarEnabled(false);
  body.addView(homeScroll,new FrameLayout.LayoutParams(-1,-1));
  // ScrollView intentionally measures its child with UNSPECIFIED height,
  // ignoring lp.height. Bound only that first pass; fillViewport will then
  // remeasure with EXACTLY the available height on phones and tablets.
  home=new FrameLayout(a){
   @Override protected void onMeasure(int widthSpec,int heightSpec){
    if(View.MeasureSpec.getMode(heightSpec)==View.MeasureSpec.UNSPECIFIED)
     heightSpec=View.MeasureSpec.makeMeasureSpec(dp(460),View.MeasureSpec.EXACTLY);
    super.onMeasure(widthSpec,heightSpec);
   }
  };
  home.setMinimumHeight(dp(460));homeScroll.addView(home,new ScrollView.LayoutParams(-1,-2));
  ImageView room=new ImageView(a);room.setImageResource(R.drawable.goro_room);room.setScaleType(ImageView.ScaleType.CENTER_CROP);room.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
  home.addView(room,new FrameLayout.LayoutParams(-1,-1));
  View sceneShade=new View(a);sceneShade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x20241a10,0x00180f09,0xe6030c17}));sceneShade.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);home.addView(sceneShade,new FrameLayout.LayoutParams(-1,-1));
  avatarStage=new FrameLayout(a);
  FrameLayout.LayoutParams stageParams=new FrameLayout.LayoutParams(-1,-1);stageParams.setMargins(0,dp(36),dp(tablet?214:92),dp(124));home.addView(avatarStage,stageParams);
  state=text("Preparando conexiones",11,false,MUTED);state.setGravity(Gravity.CENTER);state.setMaxLines(1);state.setEllipsize(TextUtils.TruncateAt.END);
  state.setOnClickListener(v->select(GoroSection.SETTINGS));
  state.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence t,int start,int count,int after){}public void onTextChanged(CharSequence t,int start,int before,int count){state.setContentDescription(t);}public void afterTextChanged(android.text.Editable text){}});
  avatar=enableAvatar?new GoroAvatarView(a,this::onAvatarStatus):null;
  if(avatar!=null)avatarStage.addView(avatar,new FrameLayout.LayoutParams(-1,-1));
  LinearLayout timeCard=new LinearLayout(a);timeCard.setOrientation(VERTICAL);timeCard.setGravity(Gravity.CENTER);timeCard.setPadding(dp(12),dp(6),dp(12),dp(6));timeCard.setBackground(sceneGlass());
  date=text("",10,false,0xffe1d6c5);date.setGravity(Gravity.CENTER);clock=text("",25,true,TEXT);clock.setGravity(Gravity.CENTER);timeCard.addView(date);timeCard.addView(clock);
  FrameLayout.LayoutParams timeParams=new FrameLayout.LayoutParams(dp(tablet?144:112),dp(61),Gravity.LEFT|Gravity.TOP);timeParams.setMargins(dp(12),dp(14),0,0);home.addView(timeCard,timeParams);
  weather=text("Clima local\nConsultar ubicación",tablet?15:12,false,TEXT);weather.setMaxLines(5);weather.setEllipsize(TextUtils.TruncateAt.END);
  agenda=text("Tu agenda · consultar",tablet?14:12,false,TEXT);agenda.setMaxLines(5);agenda.setEllipsize(TextUtils.TruncateAt.END);
  homeDevices=text("ZNP · esperando conexión",tablet?14:12,false,TEXT);homeDevices.setMaxLines(5);homeDevices.setEllipsize(TextUtils.TruncateAt.END);
  ScrollView overview=new ScrollView(a);overview.setVerticalScrollBarEnabled(false);overview.setClipToPadding(false);
  LinearLayout overviewCards=new LinearLayout(a);overviewCards.setOrientation(VERTICAL);overviewCards.setPadding(0,0,0,dp(8));overview.addView(overviewCards);
  overviewCards.addView(homeCard("CLIMA",weather,"sun",()->actions.onWeather()));
  overviewCards.addView(homeCard("AGENDA DE HOY",agenda,"agenda",()->{select(GoroSection.AGENDA);actions.onCommand("agenda hoy");}));
  overviewCards.addView(homeCard("DISPOSITIVOS",homeDevices,"devices",()->select(GoroSection.DEVICES)));
  FrameLayout.LayoutParams overviewParams=new FrameLayout.LayoutParams(dp(tablet?228:136),-1,Gravity.RIGHT|Gravity.TOP);overviewParams.setMargins(0,dp(14),dp(10),dp(138));home.addView(overview,overviewParams);
  speechBubble=text("Hola Matías.\nEstoy preparando tus conexiones.",tablet?15:13,false,0xff28251f);speechBubble.setPadding(dp(14),dp(12),dp(14),dp(12));speechBubble.setMaxLines(4);speechBubble.setEllipsize(TextUtils.TruncateAt.END);
  GradientDrawable bubbleBackground=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xfaf7f2e7,0xf0e7dfd1});bubbleBackground.setCornerRadius(dp(16));speechBubble.setBackground(bubbleBackground);speechBubble.setElevation(dp(7));speechBubble.setContentDescription("Abrir respuesta completa de Goro");speechBubble.setOnClickListener(v->openTranscript());
  FrameLayout.LayoutParams bubbleParams=new FrameLayout.LayoutParams(dp(tablet?270:174),-2,Gravity.LEFT|Gravity.BOTTOM);bubbleParams.setMargins(dp(12),0,0,dp(144));home.addView(speechBubble,bubbleParams);
  LinearLayout lower=new LinearLayout(a);lower.setOrientation(VERTICAL);
  LinearLayout quick=new LinearLayout(a);quick.setGravity(Gravity.CENTER_VERTICAL);
  quick.addView(quick("Agenda","agenda",()->{select(GoroSection.AGENDA);actions.onCommand("agenda hoy");}),quickParams());
  quick.addView(quick("Gmail","mail",()->{select(GoroSection.GMAIL);actions.onReadGmail();}),quickParams());
  microphone=iconButton("","mic",()->actions.onToggleListening(),true,31);microphone.setContentDescription("Activar escucha");microphone.setBackground(ripple(0xff063453,0xff07486b,40,0xff33cffa));microphone.setElevation(dp(9));
  LinearLayout.LayoutParams micParams=new LinearLayout.LayoutParams(dp(74),dp(74));micParams.setMargins(dp(6),0,dp(6),0);quick.addView(microphone,micParams);
  quick.addView(quick("Proyectos","folder",()->select(GoroSection.EXPLORE)),quickParams());quick.addView(quick("Dispositivos","devices",()->select(GoroSection.DEVICES)),quickParams());
  lower.addView(quick,new LinearLayout.LayoutParams(-1,dp(82)));
  conversationStatus=text("Escucha pausada",10,false,MUTED);conversationStatus.setGravity(Gravity.CENTER);conversationStatus.setSingleLine();conversationStatus.setEllipsize(TextUtils.TruncateAt.END);lower.addView(conversationStatus,new LinearLayout.LayoutParams(-1,dp(22)));lower.addView(state,new LinearLayout.LayoutParams(-1,dp(20)));
  home.addView(lower,new FrameLayout.LayoutParams(-1,dp(124),Gravity.BOTTOM));
  pages=new ScrollView(a);pages.setFillViewport(true);pageContent=new LinearLayout(a);pageContent.setOrientation(VERTICAL);pageContent.setPadding(dp(4),dp(16),dp(4),dp(20));pages.addView(pageContent);body.addView(pages,new FrameLayout.LayoutParams(-1,-1));
  output=text("GORO IA · Tocá Activar escucha para conversar con el dispositivo ZNP conectado. Podés pausarla en cualquier momento.",16,false,TEXT);output.setTextIsSelectable(true);output.setPadding(dp(16),dp(14),dp(16),dp(14));output.setBackground(glass(16));pageContent.addView(output,new LinearLayout.LayoutParams(-1,-2));
  output.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence t,int start,int count,int after){}public void onTextChanged(CharSequence t,int start,int before,int count){showAssistantMessage(t.toString());}public void afterTextChanged(android.text.Editable text){}});
  agendaValue=text("Todavía no consultaste tu agenda.",15,false,TEXT);gmailValue=text("Autorizá tu cuenta para consultar Gmail.",15,false,TEXT);devicesValue=text("ZNP · Conexión automática pendiente.",15,false,TEXT);nvidiaValue=text("NVIDIA · pendiente de conexión",13,false,TEXT);voiceValue=text("Diego · pendiente de conexión",13,false,TEXT);accountLabel=text(account==null?"Google Calendar: sin vincular":"Calendar · "+account,13,false,MUTED);
  agendaPanel=panel();agendaPanel.addView(card("TU AGENDA",agendaValue));addAction(agendaPanel,"Consultar agenda de hoy","agenda",()->actions.onCommand("agenda hoy"));addAction(agendaPanel,"Consultar agenda de mañana","agenda",()->actions.onCommand("agenda mañana"));
  gmailPanel=panel();gmailPanel.addView(card("TU CORREO",gmailValue));addAction(gmailPanel,"Actualizar bandeja","mail",()->actions.onReadGmail());
  devicePanel=panel();devicePanel.addView(card("DISPOSITIVOS ZNP",devicesValue));TextView deviceCaption=text("Activá o pausá la escucha con el botón superior. La pausa se mantiene al reconectar.",13,false,MUTED);deviceCaption.setPadding(dp(10),dp(8),dp(10),dp(14));devicePanel.addView(deviceCaption);
  addAction(devicePanel,"Conectar ZNP Z01","link",()->actions.onConnectZnpModel("Z01"));
  addAction(devicePanel,"Conectar ZNP Z02","link",()->actions.onConnectZnpModel("Z02"));
  addAction(devicePanel,"Conectar ZNP N02","link",()->actions.onConnectZnpModel("N02"));
  addAction(devicePanel,"Desconectar dispositivo ZNP","unlink",()->actions.onDisconnectZnp());
  addAction(devicePanel,"Diagnosticar ZNP Z01 (BLE)","devices",()->actions.onInspectZnpModel("Z01"));
  addAction(devicePanel,"Diagnosticar ZNP Z02 (BLE)","devices",()->actions.onInspectZnpModel("Z02"));
  addAction(devicePanel,"Diagnosticar ZNP N02 (BLE)","devices",()->actions.onInspectZnpModel("N02"));
  addAction(devicePanel,"Instalar animación GORO en Z01","face",()->actions.onInstallGoroAnimation("Z01"));
  addAction(devicePanel,"Instalar animación GORO en Z02","face",()->actions.onInstallGoroAnimation("Z02"));
  addAction(devicePanel,"Instalar animación GORO en N02","face",()->actions.onInstallGoroAnimation("N02"));
  settingsPanel=panel();settingsPanel.addView(card("TU CUENTA",accountLabel));addAction(settingsPanel,"Vincular cuenta Google","link",()->actions.onLinkAccount());addAction(settingsPanel,"Autorizar Calendar","agenda",()->actions.onCalendarPermission());addAction(settingsPanel,"Autorizar Gmail","mail",()->actions.onReadGmail());settingsPanel.addView(card("INTELIGENCIA",nvidiaValue));settingsPanel.addView(card("VOZ DIEGO",voiceValue));addAction(settingsPanel,"Configurar NVIDIA","settings",()->actions.onConfigureNvidia());addAction(settingsPanel,"Importar NVIDIA desde GORO 1.2.4","download",()->actions.onImportNvidiaConfig());addAction(settingsPanel,"Verificar NVIDIA","link",()->actions.onTestNvidia());addAction(settingsPanel,"Desvincular cuenta Google","settings",()->actions.onUnlinkAccount());
  explorePanel=panel();TextView projectNote=text("Tus ideas y proyectos\n\nPedile a Goro que recuerde un dato o consultá lo que ya guardaste.",16,false,TEXT);explorePanel.addView(card("MEMORIA PERSONAL",projectNote));addAction(explorePanel,"Consultar memoria","folder",()->actions.onCommand("qué recordás"));addAction(explorePanel,"Buscar en internet","search",()->{
    EditText query=new EditText(activity);query.setSingleLine(true);query.setHint("Qué querés buscar…");query.setTextSize(16);query.setPadding(dp(14),dp(12),dp(14),dp(12));
    new AlertDialog.Builder(activity).setTitle("Buscar en internet").setMessage("GORO abrirá resultados web y podrá leerte los fragmentos en voz alta.").setView(query)
      .setNegativeButton("Cancelar",null).setPositiveButton("Buscar",(dialog,which)->{String value=query.getText().toString().trim();if(!value.isEmpty())actions.onCommand("buscá por internet "+value);}).show();
   });
  automationPanel=panel();TextView automaticNote=text("Recordatorios, alarmas y temporizadores. Goro te pide confirmar las acciones que lo requieren.",15,false,TEXT);automationPanel.addView(card("AUTOMATIZAR",automaticNote));addAction(automationPanel,"Activar recordatorios","bolt",()->actions.onReminders());
  navigation=new LinearLayout(a);navigation.setGravity(Gravity.CENTER);navigation.setPadding(0,dp(4),0,0);navigation.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x00112336,0xff061425}));
  String[] icons={"chat","grid","bolt","settings"};for(int i=0;i<primary.length;i++){GoroSection section=primary[i];Button item=iconButton(section.label,icons[i],()->select(section),false,21);navigation.addView(item,new LinearLayout.LayoutParams(0,dp(59),1));}
  addView(navigation,new LinearLayout.LayoutParams(-1,dp(65)));clockTick.run();select(GoroSection.HOME);
 }
 private void openTranscript(){showTranscript=true;selected=GoroSection.HOME;homeScroll.setVisibility(GONE);pages.setVisibility(VISIBLE);hidePanels();title.setText("CONVERSACIÓN");back.setCompoundDrawables(null,new GoroIcon("back",TEXT,dp(22)),null,null);output.setVisibility(VISIBLE);pages.post(()->pages.scrollTo(0,0));}
 private void hidePanels(){for(LinearLayout p:new LinearLayout[]{agendaPanel,gmailPanel,devicePanel,settingsPanel,explorePanel,automationPanel})p.setVisibility(GONE);}
 private void select(GoroSection section){
  selected=section;showTranscript=false;hidePanels();boolean isHome=section==GoroSection.HOME;homeScroll.setVisibility(isHome?VISIBLE:GONE);pages.setVisibility(isHome?GONE:VISIBLE);output.setVisibility(GONE);title.setText(isHome?"GORO IA":section.label.toUpperCase(new Locale("es","AR")));title.setTextSize(isHome?23:19);back.setCompoundDrawables(null,new GoroIcon(isHome?"menu":"back",TEXT,dp(22)),null,null);
  for(int i=0;i<navigation.getChildCount();i++){Button b=(Button)navigation.getChildAt(i);boolean active=primary[i]==section;b.setTextColor(active?CYAN:MUTED);b.setBackground(ripple(active?0xff10294c:0x00000000,active?0xff06162c:0x00000000,15,active?0xff235b82:0x0023455f));String icon=new String[]{"chat","grid","bolt","settings"}[i];b.setCompoundDrawables(null,new GoroIcon(icon,active?CYAN:MUTED,dp(21)),null,null);}
  switch(section){
   case HOME:break;
   case AGENDA:agendaPanel.setVisibility(VISIBLE);break;
   case GMAIL:gmailPanel.setVisibility(VISIBLE);break;
   case DEVICES:devicePanel.setVisibility(VISIBLE);break;
   case SETTINGS:settingsPanel.setVisibility(VISIBLE);break;
   case EXPLORE:explorePanel.setVisibility(VISIBLE);break;
   case AUTOMATIONS:automationPanel.setVisibility(VISIBLE);break;
  }
  pages.post(()->pages.scrollTo(0,0));
 }
 public void showAgenda(String value){String t=value==null?"Agenda sin consultar":value;agendaValue.setText(t);agenda.setText(t);}
 public void showWeather(String value){weather.setText(value==null?"Clima sin consultar":value);}
 public void showDeviceStatus(String value){String status=value==null?"Sin datos del dispositivo.":value;devicesValue.setText(status);homeDevices.setText(deviceSummary(status));homeDevices.setContentDescription(status);}
 private static String deviceSummary(String status){
  String model=null;
  java.util.regex.Matcher match=java.util.regex.Pattern.compile("(?i)\\b(?:ZNP\\s+)?(Z01|Z02|N02)\\b").matcher(status);
  if(match.find())model=match.group(1).toUpperCase(java.util.Locale.ROOT);
  if(model==null)return status;
  String label="ZNP "+model;
  if(status.startsWith("Buscando "+label))return "Buscando "+model+"…";
  if(status.startsWith("Conectando "+label))return "Conectando "+model+"…";
  if(status.startsWith("Anuncio "+label+" detectado.")||status.startsWith("Enlace BLE "+label+" abierto.")||status.startsWith("Enlace BLE "+model+" abierto."))return model+" · verificando conexión…";
  if(status.startsWith("Autenticación RCSP de "+label+" confirmada.")||status.startsWith("Autenticación RCSP del "+model+" confirmada."))return model+" · conexión verificada";
  if(status.startsWith("Micrófono de "+label+" activo.")||status.startsWith("Micrófono "+model+" activo."))return model+" · escuchando";
  if(status.startsWith("PCM16 recibido desde el micrófono de "+label)||status.startsWith("PCM16 recibido desde el micrófono "+model))return model+" · recibiendo voz";
  if(status.startsWith("Captura del micrófono de "+label+" recibida")||status.startsWith("Captura del micrófono "+model+" recibida"))return model+" · voz recibida";
  if(status.startsWith("Salida A2DP confirmada por Android para "+label))return model+" · audio verificado";
  return status;
 }
 public void setVoiceActive(boolean active){conversationStatus.setText(listeningPaused?"Escucha pausada":active?"Te escucho desde ZNP":"ZNP · preparando conexión");conversationStatus.setTextColor(active&&!listeningPaused?CYAN:MUTED);microphone.setAlpha(active&&!listeningPaused?1f:.55f);}
 public void setListeningPaused(boolean paused){
  listeningPaused=paused;
  String label=paused?"Activar escucha":"Pausar escucha";
  listeningToggle.setText(label);listeningToggle.setContentDescription(label);
  microphone.setContentDescription(label);microphone.setAlpha(paused?.55f:1f);
  listeningLabel.setText(paused?"Escucha pausada":"Escucha activada");listeningLabel.setTextColor(paused?MUTED:CYAN);
  conversationStatus.setText(paused?"Escucha pausada":"ZNP · preparando conversación");
 }
 public void showVoiceStatus(String value){
  String status=value==null||value.trim().isEmpty()?"Voz sin conexión":value.trim();
  // Preserve the service result when microphone capture starts or stops.
  // A failed or completed probe must never look like a check still in progress.
  voiceValue.setText(status);state.setText(status);state.setContentDescription(status);
 }
 public void showNvidiaStatus(String value){nvidiaValue.setText(value==null?"Sin estado de NVIDIA":value);}
 public void showGmailStatus(String value){gmailValue.setText(value==null?"Gmail sin conexión":value);}
 public void showAssistantMessage(String value){
  if(value==null||value.trim().isEmpty())return;
  speechBubble.setText(value.trim().replace('\n',' '));
  // Commands also complete while the user is in Agenda, Gmail or Settings.
  // Keep their reply visible there, especially when audio is unavailable.
  if(selected!=GoroSection.HOME||showTranscript){output.setVisibility(VISIBLE);pages.post(()->pages.smoothScrollTo(0,0));}
 }
 public void showAvatarMood(GoroAvatarView.Mood mood){if(avatar!=null)avatar.setMood(mood);}
 public void avatarSpeak(String text,byte[] wav){if(avatar!=null)avatar.startSpeaking(text,wav);}
 private void onAvatarStatus(String message){if(message!=null&&message.startsWith("Error"))state.setText("No se pudo mostrar el robot");}
 public int navigationItemCount(){return navigation.getChildCount();}
 private LinearLayout panel(){LinearLayout p=new LinearLayout(activity);p.setOrientation(VERTICAL);pageContent.addView(p,new LinearLayout.LayoutParams(-1,-2));return p;}
 private LinearLayout card(String label,TextView value){LinearLayout c=new LinearLayout(activity);c.setOrientation(VERTICAL);c.setPadding(dp(16),dp(16),dp(16),dp(16));c.setBackground(glass(18));TextView h=text(label,11,true,CYAN);h.setLetterSpacing(.12f);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);hp.bottomMargin=dp(10);c.addView(h,hp);c.addView(value,new LinearLayout.LayoutParams(-1,-2));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.setMargins(0,dp(14),0,dp(8));c.setLayoutParams(cp);return c;}
 private void addAction(LinearLayout parent,String label,String icon,Runnable action){Button b=iconButton(label,icon,action,false,21);b.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);b.setCompoundDrawables(new GoroIcon(icon,CYAN,dp(21)),null,null,null);b.setCompoundDrawablePadding(dp(12));b.setTextSize(13);b.setPadding(dp(16),dp(10),dp(12),dp(10));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(54));lp.setMargins(0,dp(5),0,dp(5));parent.addView(b,lp);}
 private Button quick(String label,String icon,Runnable action){Button b=iconButton(label,icon,action,false,25);b.setTextSize(label.equals("Dispositivos")?8:10);return b;}
 private LinearLayout.LayoutParams quickParams(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(65),1);p.setMargins(dp(2),0,dp(2),0);return p;}
 private Button iconButton(String label,String icon,Runnable action,boolean primary,int iconSize){Button b=new Button(activity);b.setText(label);b.setAllCaps(false);b.setTextSize(10);b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));b.setTextColor(TEXT);b.setGravity(Gravity.CENTER);b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(dp(44));b.setPadding(dp(5),dp(7),dp(5),dp(5));b.setCompoundDrawables(null,new GoroIcon(icon,primary?CYAN:TEXT,dp(iconSize)),null,null);b.setCompoundDrawablePadding(dp(5));b.setBackground(ripple(0xee0b2035,0xee051222,14,EDGE));b.setElevation(dp(2));b.setContentDescription(label);b.setOnTouchListener((v,event)->{if(event.getActionMasked()==MotionEvent.ACTION_DOWN){v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);v.animate().cancel();v.setScaleX(.965f);v.setScaleY(.965f);}else if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL){v.animate().scaleX(1).scaleY(1).setDuration(125).start();}return false;});b.setOnClickListener(v->action.run());return b;}
 private LinearLayout homeCard(String label,TextView value,String icon,Runnable action){
  LinearLayout card=new LinearLayout(activity);card.setOrientation(VERTICAL);card.setPadding(dp(12),dp(12),dp(12),dp(13));card.setBackground(sceneGlass());card.setElevation(dp(3));
  TextView heading=text(label,tablet?11:9,true,0xffe6decf);heading.setLetterSpacing(.06f);heading.setCompoundDrawables(new GoroIcon(icon,0xffe2c993,dp(tablet?20:17)),null,null,null);heading.setCompoundDrawablePadding(dp(7));
  LinearLayout.LayoutParams headingParams=new LinearLayout.LayoutParams(-1,-2);headingParams.bottomMargin=dp(9);card.addView(heading,headingParams);card.addView(value,new LinearLayout.LayoutParams(-1,-2));
  LinearLayout.LayoutParams cardParams=new LinearLayout.LayoutParams(-1,-2);cardParams.bottomMargin=dp(9);card.setLayoutParams(cardParams);card.setMinimumHeight(dp(82));card.setFocusable(true);card.setOnClickListener(v->action.run());return card;
 }
 private GradientDrawable sceneGlass(){GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xe041423d,0xde262b2b});d.setCornerRadius(dp(17));d.setStroke(dp(1),0x66e5d5b9);return d;}
 private GradientDrawable glass(int radius){GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{SURFACE,0xee061424});d.setCornerRadius(dp(radius));d.setStroke(dp(1),EDGE);return d;}
 private RippleDrawable ripple(int a,int b,int radius,int edge){GradientDrawable bg=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{a,b});bg.setCornerRadius(dp(radius));bg.setStroke(dp(1),edge);GradientDrawable mask=new GradientDrawable();mask.setColor(Color.WHITE);mask.setCornerRadius(dp(radius));return new RippleDrawable(ColorStateList.valueOf(0x552bd8ff),bg,mask);}
 private TextView text(String label,int size,boolean bold,int color){TextView t=new TextView(activity);t.setText(label);t.setTextSize(size);t.setTextColor(color);t.setTypeface(Typeface.create(bold?"sans-serif-medium":"sans-serif",Typeface.NORMAL));t.setGravity(Gravity.CENTER_VERTICAL);t.setIncludeFontPadding(false);return t;}
 @Override protected void onAttachedToWindow(){super.onAttachedToWindow();handler.removeCallbacks(clockTick);handler.post(clockTick);}
 @Override protected void onDetachedFromWindow(){handler.removeCallbacksAndMessages(null);super.onDetachedFromWindow();}
 private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
