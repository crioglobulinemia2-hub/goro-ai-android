package ar.goro.ai.avatar;

import android.content.Context;
import android.graphics.Color;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.widget.FrameLayout;
import android.widget.TextView;
import com.google.android.filament.Engine;
import com.google.android.filament.View;
import com.google.android.filament.android.UiHelper;
import com.google.android.filament.utils.ModelViewer;
import com.google.android.filament.utils.Float3;
import com.google.android.filament.utils.Utils;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;

/** Filament glTF viewer for the authored GORO rig. */
public final class GoroAvatarView extends FrameLayout {
 static { if(!"robolectric".equals(android.os.Build.FINGERPRINT) && System.getProperty("robolectric.version")==null) Utils.INSTANCE.init(); }
 public interface StatusListener {void onStatus(String status);}
 private static final String ASSET_PATH="goro/goro_rigged.glb";
 private final StatusListener statusListener;
 private SurfaceView surface;
 private ModelViewer viewer;
 private boolean framePosted;
 private long animationStartNanos;
 private final Choreographer.FrameCallback frameCallback=new Choreographer.FrameCallback(){
  @Override public void doFrame(long frameTimeNanos){
   framePosted=false;
   ModelViewer current=viewer;
   if(current==null||!isAttachedToWindow())return;
   com.google.android.filament.gltfio.Animator animator=current.getAnimator();
   if(animator!=null&&animator.getAnimationCount()>0){
    if(animationStartNanos==0)animationStartNanos=frameTimeNanos;
    float elapsed=(frameTimeNanos-animationStartNanos)/1_000_000_000f;
    animator.applyAnimation(0,elapsed);
    animator.updateBoneMatrices();
   }
   current.render(frameTimeNanos);
   postFrame();
  }
 };
 public GoroAvatarView(Context context,StatusListener listener){
  super(context);statusListener=listener;
  setBackgroundColor(Color.TRANSPARENT);
  loadIfPresent();
 }
 private void loadIfPresent(){
  byte[] bytes;
  try{bytes=readAsset();}
  catch(Exception missing){
   TextView label=new TextView(getContext());
   label.setText("GORO 3D · MODELO ARTICULADO PENDIENTE");
   label.setTextColor(Color.WHITE);label.setTextSize(15);label.setGravity(android.view.Gravity.CENTER);
   addView(label,new FrameLayout.LayoutParams(-1,-1));
   status("Filament preparado; falta el GLB riggeado original de GORO.");
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
   viewer=new ModelViewer(surface,Engine.create(),new UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK),null);
   viewer.getView().setAntiAliasing(View.AntiAliasing.FXAA);
   viewer.loadModelGlb(ByteBuffer.wrap(bytes));
   // GLB front is +Z; use ModelViewer default framing so the camera faces the complete faceplate.
   viewer.transformToUnitCube(new Float3(0f,0f,0f));
   // GLB faceplate points +Z: camera must stay outside the head, in front of the model.
   viewer.getCamera().lookAt(0.0,0.0,4.0, 0.0,0.0,0.0, 0.0,1.0,0.0);
   animationStartNanos=0;
   status("GLB de GORO cargado en Filament; reproducción esquelética activa si el archivo contiene animaciones.");
  }catch(Throwable error){
   viewer=null;
   TextView label=new TextView(getContext());
   label.setText("GORO 3D · ERROR DE CARGA DEL MODELO");
   label.setTextColor(Color.WHITE);label.setTextSize(15);label.setGravity(android.view.Gravity.CENTER);
   addView(label,new FrameLayout.LayoutParams(-1,-1));
   status("No se pudo cargar el GLB de GORO: "+error.getClass().getSimpleName());
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
  // ModelViewer v1.70 owns SurfaceView teardown and destroys its Filament engine on detach.
  viewer=null;animationStartNanos=0;
  super.onDetachedFromWindow();
 }
}