package ar.goro.ai.avatar;

import android.content.Context;
import android.graphics.Color;
import android.os.SystemClock;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.SurfaceView;
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
import com.google.android.filament.utils.ModelViewer;
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
 private SurfaceView surface;
 private ModelViewer viewer;
 private Engine engine;
 private int sunEntity=0;
 private int fillEntity=0;
 private IndirectLight ambientLight;
 private boolean morphErrorReported=false;
 private int faceEntity=0;
 private boolean framePosted=false;
 private Mood mood=Mood.IDLE;
 private long moodStartedNanos=0;
 private String speechText="";
 private long speechStartedUptime=0;
 private long speechDurationMs=0;
 private byte[] speechAudio;
 private int pcmStart=44;
 private int pcmByteRate=44100;
 private int pcmBlockAlign=2;
 private final VisemeEstimator visemes=new VisemeEstimator();
 private final Choreographer.FrameCallback frameCallback=new Choreographer.FrameCallback(){
  @Override public void doFrame(long frameTimeNanos){
   framePosted=false;
   ModelViewer current=viewer;
   if(current==null||!isAttachedToWindow())return;
   com.google.android.filament.gltfio.Animator animator=current.getAnimator();
   if(animator!=null&&animator.getAnimationCount()>0){
    if(moodStartedNanos==0)moodStartedNanos=frameTimeNanos;
    int clip=Math.min(mood.ordinal(),animator.getAnimationCount()-1);
    float elapsed=((frameTimeNanos-moodStartedNanos)/1_000_000_000f)%1.80f;
    animator.applyAnimation(clip,elapsed);
    animator.updateBoneMatrices();
   }
   applyExpression(frameTimeNanos);
   // Consistent full-height framing for wide tablet and portrait layouts.
   if(surface!=null&&surface.getHeight()>0&&surface.getWidth()>0){
    double aspect=(double)surface.getWidth()/(double)surface.getHeight();
    double halfHeight=1.30;
    current.getCamera().setProjection(Camera.Projection.ORTHO,
        -halfHeight*aspect,halfHeight*aspect,-halfHeight,halfHeight,.05,60.0);
   }
   current.render(frameTimeNanos);
   postFrame();
  }
 };
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
  surface=new SurfaceView(getContext());
  surface.setFocusable(false);
  surface.setFocusableInTouchMode(false);
  surface.setBackgroundColor(Color.TRANSPARENT);
  addView(surface,new FrameLayout.LayoutParams(-1,-1));
  surface.setOnTouchListener((v,event)->{
   if(viewer!=null){viewer.onTouchEvent(event);return true;}
   return false;
  });
  try{
   // Vulkan avoids the GL_MAX_VERTEX_UNIFORM_VECTORS(256) limitation
   // triggered by GORO's skinned PBR model on the API 36 emulator.
   // Do not silently fall back to unsafe GLES, which aborts natively.
   engine=Engine.create(Engine.Backend.VULKAN);
   if(engine==null)throw new IllegalStateException("Vulkan is not available");
   UiHelper helper=new UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK);
   helper.setOpaque(true);
   viewer=new ModelViewer(surface,engine,helper,null);
   viewer.getView().setAntiAliasing(View.AntiAliasing.FXAA);
   viewer.getView().setBlendMode(View.BlendMode.OPAQUE);
   com.google.android.filament.Renderer.ClearOptions clearOptions=new com.google.android.filament.Renderer.ClearOptions();
   // Filament clear colors are linear; display-space gray values made the
   // approved dark stage look washed out after tone mapping.
   clearOptions.clearColor=new float[]{0.003f,0.009f,0.018f,1.0f};
   clearOptions.clear=true;
   clearOptions.discard=true;
   viewer.getRenderer().setClearOptions(clearOptions);
   viewer.loadModelGlb(ByteBuffer.wrap(bytes));
   viewer.transformToUnitCube(new Float3(0f,0f,0f));
   // Position between normalized and raw-space model centers so both fits
   // remain visible until the asset bounding box is fully resolved.
   viewer.getCamera().lookAt(0.0,.65,8.0,0.0,.65,0.0,0.0,1.0,0.0);
   FilamentAsset asset=viewer.getAsset();
   if(asset!=null)faceEntity=asset.getFirstEntityByName("GORO_Face_Morphs");
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
   // The robot's previous high-metallic materials looked black without IBL.
   ambientLight=new IndirectLight.Builder()
       .irradiance(1,new float[]{.86f,.91f,1.0f})
       .intensity(26000f)
       .build(engine);
   viewer.getScene().setIndirectLight(ambientLight);
   moodStartedNanos=0;
   status("GORO 3D · Esqueleto y expresiones cargados.");
  }catch(Throwable error){
   viewer=null;
   TextView label=new TextView(getContext());
   label.setText("GORO 3D · ERROR DEL MODELO");
   label.setTextColor(Color.WHITE);label.setTextSize(14);
   label.setGravity(android.view.Gravity.CENTER);
   addView(label,new FrameLayout.LayoutParams(-1,-1));
   status("Error al renderizar GORO: "+error.getClass().getSimpleName());
  }
 }
 public void setMood(Mood next){
  if(next==null)return;
  if(mood!=next){mood=next;moodStartedNanos=0;}
  if(next!=Mood.SPEAKING){speechText="";speechAudio=null;}
 }
 public void startSpeaking(String text,byte[] wav){
  speechText=text==null?"":text.trim();
  speechAudio=wav;
  pcmStart=44;pcmByteRate=44100;pcmBlockAlign=2;
  int pcmSize=wav==null?0:Math.max(0,wav.length-44);
  if(wav!=null&&wav.length>=44){
   int byteRate=readLe32(wav,28);
   int align=readLe16(wav,32);
   if(byteRate>0&&byteRate<400000&&align>=2&&align<=16){
    pcmByteRate=byteRate;pcmBlockAlign=align;
   }
   // WAV permits metadata chunks before the audio payload.
   for(int offset=12;offset+8<=wav.length;){
    int size=readLe32(wav,offset+4);
    if(size<0||size>wav.length-offset-8)break;
    if(wav[offset]=='d'&&wav[offset+1]=='a'&&wav[offset+2]=='t'&&wav[offset+3]=='a'){
     pcmStart=offset+8;pcmSize=size;break;
    }
    offset+=(8+size+(size&1));
   }
  }
  speechDurationMs=pcmByteRate>0&&wav!=null?
    Math.max(200,1000L*pcmSize/pcmByteRate):
    Math.max(500,speechText.length()*74L);
  speechStartedUptime=SystemClock.uptimeMillis();
  setMood(Mood.SPEAKING);
 }
 private static int readLe16(byte[] data,int offset){
  return (data[offset]&255)|((data[offset+1]&255)<<8);
 }
 private static int readLe32(byte[] data,int offset){
  return (data[offset]&255)|((data[offset+1]&255)<<8)|
    ((data[offset+2]&255)<<16)|((data[offset+3]&255)<<24);
 }
 /** Audio-driven RMS envelope, sampled from the actual NVIDIA PCM WAV response. */
 private float speechEnergy(long elapsedMs){
  byte[] audio=speechAudio;
  if(audio==null||audio.length<pcmStart+4||pcmByteRate<=0)return .55f;
  long byteOffset=elapsedMs*pcmByteRate/1000;
  if(byteOffset<0||byteOffset>audio.length-pcmStart-4)return 0f;
  int pos=pcmStart+(int)(byteOffset/pcmBlockAlign)*pcmBlockAlign;
  if(pos<pcmStart||pos+1>=audio.length)return 0f;
  double squared=0;int count=0;
  int end=Math.min(audio.length-1,pos+pcmBlockAlign*256);
  for(int i=pos;i+1<end;i+=pcmBlockAlign){
   int sample=(short)readLe16(audio,i);
   squared+=(sample/32768.0)*(sample/32768.0);
   count++;
  }
  if(count==0)return 0f;
  float root=(float)Math.sqrt(squared/count);
  return Math.min(1f,root*7.2f);
 }
 private void applyExpression(long frameTimeNanos){
  if(viewer==null||engine==null||faceEntity==0)return;
  RenderableManager renderables=engine.getRenderableManager();
  int instance=renderables.getInstance(faceEntity);
  if(instance==0)return;
  float[] weights=new float[10];
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
  try{renderables.setMorphWeights(instance,weights,0);}
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
 private void postFrame(){
  if(!framePosted&&viewer!=null&&isAttachedToWindow()){
   framePosted=true;Choreographer.getInstance().postFrameCallback(frameCallback);
  }
 }
 @Override protected void onAttachedToWindow(){super.onAttachedToWindow();postFrame();}
 @Override protected void onDetachedFromWindow(){
  Choreographer.getInstance().removeFrameCallback(frameCallback);framePosted=false;
  if(viewer!=null&&engine!=null){
   try{
    if(sunEntity!=0){viewer.getScene().removeEntity(sunEntity);engine.destroyEntity(sunEntity);}
    if(fillEntity!=0){viewer.getScene().removeEntity(fillEntity);engine.destroyEntity(fillEntity);}
   }catch(RuntimeException ignored){}
  }
  if(viewer!=null&&engine!=null&&ambientLight!=null){
   try{viewer.getScene().setIndirectLight(null);engine.destroyIndirectLight(ambientLight);}
   catch(RuntimeException ignored){}
  }
  ambientLight=null;
  sunEntity=0;fillEntity=0;faceEntity=0;viewer=null;engine=null;moodStartedNanos=0;
  super.onDetachedFromWindow();
 }
}
