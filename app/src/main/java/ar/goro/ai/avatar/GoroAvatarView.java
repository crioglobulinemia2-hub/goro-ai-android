package ar.goro.ai.avatar;

import android.content.Context;
import android.graphics.Color;
import android.os.SystemClock;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.TextureView;
import android.widget.FrameLayout;
import android.widget.TextView;
import com.google.android.filament.Engine;
import com.google.android.filament.RenderableManager;
import com.google.android.filament.LightManager;
import com.google.android.filament.IndirectLight;
import com.google.android.filament.Camera;
import com.google.android.filament.EntityManager;
import com.google.android.filament.View;
import com.google.android.filament.android.UiHelper;
import com.google.android.filament.gltfio.FilamentAsset;
import com.google.android.filament.utils.GoroModelViewer;
import com.google.android.filament.utils.KTX1Loader;
import com.google.android.filament.utils.Manipulator;
import com.google.android.filament.utils.Float3;
import com.google.android.filament.utils.Utils;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;

/** Live Filament humanoid: skeletal states + ten speech/eye morph targets. */
public final class GoroAvatarView extends FrameLayout {
 static {
  if(!"robolectric".equals(android.os.Build.FINGERPRINT)
      && System.getProperty("robolectric.version")==null)Utils.INSTANCE.init();
 }
 public interface StatusListener {void onStatus(String status);}
 public enum Mood {IDLE,LISTENING,THINKING,SPEAKING,CONFIRMING,ALERT}
 private static final String ASSET_PATH="goro/goro_rigged.glb";
 private final StatusListener statusListener;
 private TextureView surface;
 private GoroModelViewer viewer;
 private Engine engine;
 private int sunEntity=0;
 private int fillEntity=0;
 private IndirectLight ambientLight;
 private double frameHalfHeight=1.30,frameHalfWidth=.75;
 private boolean morphErrorReported=false;
 private boolean recreateOnAttach=false;
 private int faceEntity=0;
 private AvatarMorphMap faceMorphMap;
 private final float[] expressionWeights=new float[10];
 private float[] mappedMorphWeights;
 private Mood mood=Mood.IDLE;
 private AvatarAnimationTransition animationTransition=new AvatarAnimationTransition();
 private AvatarAnimationCatalog animationCatalog;
 private String speechText="";
 private long speechStartedUptime=0;
 private long speechDurationMs=0;
 private AvatarSpeechEnvelope speechEnvelope;
 private boolean speechHasAudio=false;
 private final VisemeEstimator visemes=new VisemeEstimator();
 private final Choreographer.FrameCallback frameCallback=time->this.frameLoop.onFrame(time);
 private final AvatarFrameLoop frameLoop=new AvatarFrameLoop(new AvatarFrameLoop.Scheduler(){
  public void post(){Choreographer.getInstance().postFrameCallback(frameCallback);}
  public void remove(){Choreographer.getInstance().removeFrameCallback(frameCallback);}
 },this::renderFrame);
 private void renderFrame(long frameTimeNanos){
   GoroModelViewer current=viewer;
   if(current==null||!isAttachedToWindow()||!isShown()||getWindowVisibility()!=VISIBLE)return;
   current.prepareFrame();
   com.google.android.filament.gltfio.Animator animator=current.getAnimator();
   if(animator!=null){
    if(animationCatalog==null){
     int count=animator.getAnimationCount();
     String[] names=new String[count];
     float[] durations=new float[count];
     for(int i=0;i<count;i++){
      names[i]=animator.getAnimationName(i);
      durations[i]=animator.getAnimationDuration(i);
     }
     animationCatalog=new AvatarAnimationCatalog(names,durations);
    }
    animationTransition.advance(animationCatalog.resolve(mood.name()),frameTimeNanos);
    int clip=animationTransition.currentClip();
    if(clip>=0){
     animator.applyAnimation(clip,animationCatalog.timeSeconds(clip,animationTransition.currentElapsedSeconds()));
     int previous=animationTransition.previousClip();
     if(previous>=0)animator.applyCrossFade(previous,
         animationCatalog.timeSeconds(previous,animationTransition.previousElapsedSeconds()),
         animationTransition.blendAlpha());
    }
    animator.updateBoneMatrices();
   }
   applyExpression(frameTimeNanos);
   // Consistent full-height framing for wide tablet and portrait layouts.
   if(surface!=null&&surface.getHeight()>0&&surface.getWidth()>0){
    double aspect=(double)surface.getWidth()/(double)surface.getHeight();
    double halfHeight=Math.max(frameHalfHeight,frameHalfWidth/aspect);
    current.getCamera().setProjection(Camera.Projection.ORTHO,
        -halfHeight*aspect,halfHeight*aspect,-halfHeight,halfHeight,.05,60.0);
   }
   current.render(frameTimeNanos);
 }
 public GoroAvatarView(Context context,StatusListener listener){
  super(context);
  this.statusListener=listener;
  setBackgroundColor(Color.TRANSPARENT);
  loadIfPresent();
 }
 private void loadIfPresent(){
  byte[] bytes;
  try{bytes=readAsset();}
  catch(Exception missing){
   TextView label=new TextView(getContext());
   label.setText("GORO 3D · MODELO NO INCLUIDO EN ESTE BUILD");
   label.setTextColor(0xff294456);label.setTextSize(14);
   label.setGravity(android.view.Gravity.CENTER);
   addView(label,new FrameLayout.LayoutParams(-1,-1));
   status("No se encontró el robot 3D de GORO.");
   return;
  }
  // TextureView participates in the normal view hierarchy, so the room stays
  // behind the transparent robot and dialogue/cards can be drawn above it.
  surface=new TextureView(getContext());
  surface.setOpaque(false);
  surface.setFocusable(false);
  surface.setFocusableInTouchMode(false);
  // Android detaches children before their parent. Register this listener
  // before ModelViewer's listener, which destroys the engine on surface detach.
  surface.addOnAttachStateChangeListener(new android.view.View.OnAttachStateChangeListener(){
   public void onViewAttachedToWindow(android.view.View view){}
   public void onViewDetachedFromWindow(android.view.View view){
    frameLoop.setActive(false);
    releaseOwnedLighting();
   }
  });
  addView(surface,new FrameLayout.LayoutParams(-1,-1));
  surface.setOnTouchListener((v,event)->{
   if(viewer!=null){viewer.onTouchEvent(event);return true;}
   return false;
  });
  try{
   // This skinned PBR avatar exceeds GLES uniform limits on some devices.
   // Vulkan is the supported backend; never retry with a backend known to
   // abort inside the native renderer. The catch below reports the exact cause.
   engine=Engine.create(Engine.Backend.VULKAN);
   if(engine==null)throw new IllegalStateException("Filament no pudo crear el motor Vulkan");
   UiHelper helper=new UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK);
   helper.setOpaque(false);
   // A null argument creates GoroModelViewer's default orbit controller, which
   // overwrites Camera.lookAt every frame. Supply the centered camera explicitly.
   Manipulator cameraControl=new Manipulator.Builder()
       .targetPosition(0f,0f,0f).orbitHomePosition(0f,0f,8f)
       .viewport(1,1).orbitSpeed(0f,0f).zoomSpeed(0f).panning(false)
       .build(Manipulator.Mode.ORBIT);
   viewer=new GoroModelViewer(surface,engine,helper,cameraControl);
   viewer.getView().setAntiAliasing(View.AntiAliasing.FXAA);
   viewer.getView().setBlendMode(View.BlendMode.TRANSLUCENT);
   com.google.android.filament.Renderer.ClearOptions clearOptions=new com.google.android.filament.Renderer.ClearOptions();
   clearOptions.clearColor=new float[]{0f,0f,0f,0f};
   clearOptions.clear=true;
   clearOptions.discard=true;
   viewer.getRenderer().setClearOptions(clearOptions);
   viewer.loadModelGlb(ByteBuffer.wrap(bytes));
   if(viewer.getAsset()==null)throw new IllegalStateException("Filament could not load the packaged GLB");
   viewer.transformToUnitCube(new Float3(0f,0f,0f));
   FilamentAsset asset=viewer.getAsset();
   if(asset!=null){
    faceEntity=asset.getFirstEntityByName("GORO_Face_Morphs");
    if(faceEntity!=0){
     faceMorphMap=new AvatarMorphMap(asset.getMorphTargetNames(faceEntity));
     mappedMorphWeights=new float[faceMorphMap.targetCount()];
    }
    float[] extent=asset.getBoundingBox().getHalfExtent();
    double scale=1.0/Math.max(extent[0],Math.max(extent[1],extent[2]));
    frameHalfHeight=extent[1]*scale*1.10;
    frameHalfWidth=extent[0]*scale*1.15;
   }
   // GoroModelViewer supplies a default sun; use only GORO's controlled lighting.
   viewer.getScene().removeEntity(viewer.getLight());
   sunEntity=EntityManager.get().create();
   new LightManager.Builder(LightManager.Type.DIRECTIONAL)
       .color(1f,.96f,.90f)
       .intensity(76000f)
       .direction(-.45f,-.8f,-.45f)
       .build(engine,sunEntity);
   viewer.getScene().addEntity(sunEntity);
   fillEntity=EntityManager.get().create();
   new LightManager.Builder(LightManager.Type.DIRECTIONAL)
       .color(.35f,.80f,1f)
       .intensity(21000f)
       .direction(.65f,-.40f,.42f)
       .build(engine,fillEntity);
   viewer.getScene().addEntity(fillEntity);
   try(InputStream lighting=getContext().getAssets().open("goro/studio_ibl.ktx");
       ByteArrayOutputStream data=new ByteArrayOutputStream()){
    byte[] buffer=new byte[16384];int count;
    while((count=lighting.read(buffer))!=-1)data.write(buffer,0,count);
    KTX1Loader.IndirectLightBundle bundle=KTX1Loader.INSTANCE.createIndirectLight(
        engine,ByteBuffer.wrap(data.toByteArray()),new KTX1Loader.Options());
    ambientLight=bundle.getIndirectLight();
    if(ambientLight==null)throw new IllegalStateException("Studio lighting could not be loaded");
    ambientLight.setIntensity(16000f);
    viewer.setIndirectLightCubemap(bundle.getCubemap());
    viewer.getScene().setIndirectLight(ambientLight);
   }
   animationTransition=new AvatarAnimationTransition();
   status(faceMorphMap!=null&&faceMorphMap.isComplete()?
     "GORO 3D · Esqueleto y expresiones cargados.":
     "GORO 3D · Modelo cargado; faltan expresiones faciales compatibles.");
  }catch(Throwable error){
   android.util.Log.e("GoroAvatar","No se pudo inicializar el renderer Vulkan o cargar el GLB",error);
   // A failed setup can occur while this view is still attached. Release
   // native allocations now; the viewer's detach listener is idempotent.
   frameLoop.setActive(false);
   releaseOwnedLighting();
   try{
    if(viewer!=null)viewer.destroy();
    else if(engine!=null)engine.destroy();
   }catch(RuntimeException cleanupError){
    android.util.Log.w("GoroAvatar","Could not release failed renderer",cleanupError);
   }
   viewer=null;engine=null;faceEntity=0;faceMorphMap=null;mappedMorphWeights=null;
   animationCatalog=null;
   TextView label=new TextView(getContext());
   label.setText("No pude mostrar a Goro.");
   label.setTextColor(Color.WHITE);label.setTextSize(14);
   label.setGravity(android.view.Gravity.CENTER);
   addView(label,new FrameLayout.LayoutParams(-1,-1));
   status("Error al renderizar GORO: "+error.getClass().getSimpleName());
  }
 }
 public void setMood(Mood next){
  if(next==null)return;
  if(mood!=next)mood=next;
  if(next!=Mood.SPEAKING){speechText="";speechEnvelope=null;speechHasAudio=false;}
 }
 public void startSpeaking(String text,byte[] wav){
  speechText=text==null?"":text.trim();
  speechHasAudio=wav!=null;
  speechEnvelope=null;
  if(speechHasAudio){
   try{speechEnvelope=new AvatarSpeechEnvelope(wav);}
   catch(IllegalArgumentException invalid){status("Audio no compatible con las expresiones de GORO.");}
  }
  speechDurationMs=speechEnvelope!=null?Math.max(1,speechEnvelope.durationMs()):
    Math.max(500,speechText.length()*74L);
  speechStartedUptime=SystemClock.uptimeMillis();
  setMood(Mood.SPEAKING);
 }
 /** Audio-driven RMS envelope, sampled from the actual NVIDIA PCM WAV response. */
 private float speechEnergy(long elapsedMs){
  if(speechEnvelope!=null)return speechEnvelope.energyAt(elapsedMs);
  return speechHasAudio?0f:.55f;
 }
 private void applyExpression(long frameTimeNanos){
  if(viewer==null||engine==null||faceEntity==0||faceMorphMap==null||!faceMorphMap.isComplete())return;
  RenderableManager renderables=engine.getRenderableManager();
  int instance=renderables.getInstance(faceEntity);
  if(instance==0)return;
  float[] weights=expressionWeights;
  java.util.Arrays.fill(weights,0f);
  // Blink events are time-based, never a static screen sprite.
  float phase=(SystemClock.uptimeMillis()%4300)/4300f;
  float blink=Math.max(0f,1f-Math.abs(phase-.70f)/.025f);
  weights[6]=blink;weights[7]=blink;
  if(mood==Mood.LISTENING){weights[8]=.24f;}
  else if(mood==Mood.CONFIRMING){weights[9]=.95f;weights[8]=.28f;}
  else if(mood==Mood.THINKING){weights[8]=.54f;}
  else if(mood==Mood.SPEAKING){
   long delta=Math.max(0,SystemClock.uptimeMillis()-speechStartedUptime);
   float progress=Math.min(1f,delta/(float)Math.max(1,speechDurationMs));
   float speechWeight=speechEnergy(delta);
   if(!speechText.isEmpty()){
    int index=Math.min(speechText.length()-1,(int)(progress*speechText.length()));
    VisemeEstimator.Viseme shape=visemes.estimate(speechText.charAt(index));
    switch(shape){
     case A:weights[0]=.90f;break;
     case E:weights[1]=.86f;break;
     case I:weights[2]=.85f;break;
     case O:weights[3]=.95f;break;
     case U:weights[4]=.80f;break;
     case MBP:weights[5]=1f;break;
     default:break;
    }
    // Lip movement follows both the text viseme estimate and speech RMS.
    // A near-silent interval returns to the closed-mouth expression.
    for(int i=0;i<6;i++)weights[i]*=speechWeight;
   }
  }
  try{
   faceMorphMap.mapInto(weights,mappedMorphWeights);
   renderables.setMorphWeights(instance,mappedMorphWeights,0);
  }
  catch(RuntimeException unsupported){
   if(!morphErrorReported){morphErrorReported=true;status("El dispositivo no admite morph targets de GORO.");}
  }
 }
 private byte[] readAsset()throws Exception{
  try(InputStream in=getContext().getAssets().open(ASSET_PATH);
      ByteArrayOutputStream out=new ByteArrayOutputStream()){
   byte[] buffer=new byte[16384];int count;
   while((count=in.read(buffer))!=-1)out.write(buffer,0,count);
   byte[] bytes=out.toByteArray();
   if(bytes.length<20||bytes[0]!='g'||bytes[1]!='l'||bytes[2]!='T'||bytes[3]!='F')
    throw new IllegalStateException("Invalid GLB header");
   return bytes;
  }
 }
 private void status(String value){if(statusListener!=null)statusListener.onStatus(value);}
 private void updateFrameLoop(){
  // Android can dispatch visibility callbacks while the superclass constructor
  // is still running, before this field is initialized.
  if(frameLoop!=null)frameLoop.setActive(viewer!=null&&isAttachedToWindow()
      &&isShown()&&getWindowVisibility()==VISIBLE);
 }
 @Override protected void onAttachedToWindow(){
  super.onAttachedToWindow();
  if(recreateOnAttach){
   // ModelViewer destroys its engine when the old TextureView is detached.
   // Reattaching that surface cannot revive the destroyed renderer. Replace
   // it and load the same packaged model before restarting the frame chain.
   recreateOnAttach=false;
   removeAllViews();
   surface=null;
   morphErrorReported=false;
   loadIfPresent();
  }
  updateFrameLoop();
 }
 @Override protected void onVisibilityChanged(android.view.View changedView,int visibility){
  super.onVisibilityChanged(changedView,visibility);updateFrameLoop();
 }
 @Override protected void onWindowVisibilityChanged(int visibility){
  super.onWindowVisibilityChanged(visibility);updateFrameLoop();
 }
 private void releaseOwnedLighting(){
  if(viewer!=null&&engine!=null){
   try{
    if(sunEntity!=0){
     viewer.getScene().removeEntity(sunEntity);engine.destroyEntity(sunEntity);
     EntityManager.get().destroy(sunEntity);
    }
    if(fillEntity!=0){
     viewer.getScene().removeEntity(fillEntity);engine.destroyEntity(fillEntity);
     EntityManager.get().destroy(fillEntity);
    }
   }catch(RuntimeException ignored){}
  }
  if(viewer!=null&&engine!=null&&ambientLight!=null){
   try{viewer.getScene().setIndirectLight(null);engine.destroyIndirectLight(ambientLight);}
   catch(RuntimeException ignored){}
  }
  ambientLight=null;
  sunEntity=0;fillEntity=0;
 }
 @Override protected void onDetachedFromWindow(){
  recreateOnAttach=true;
  frameLoop.setActive(false);
  // The child listeners already released lighting and destroyed ModelViewer.
  // Never call the engine here: Android dispatches the child's detach first.
  sunEntity=0;fillEntity=0;faceEntity=0;faceMorphMap=null;mappedMorphWeights=null;viewer=null;engine=null;animationTransition=new AvatarAnimationTransition();animationCatalog=null;
  super.onDetachedFromWindow();
 }
}
