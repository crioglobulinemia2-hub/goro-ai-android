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
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * Filament glTF viewer for the authored GORO rig.
 * It intentionally refuses to substitute a bitmap, emoji or primitive when the GLB is absent.
 */
public final class GoroAvatarView extends FrameLayout {
 public interface StatusListener {void onStatus(String status);}
 private static final String ASSET_PATH="goro/goro_rigged.glb";
 private final StatusListener statusListener;
 private final SurfaceView surface;
 private ModelViewer viewer;
 private boolean framePosted;
 private final Choreographer.FrameCallback frameCallback=new Choreographer.FrameCallback(){
  @Override public void doFrame(long frameTimeNanos){
   framePosted=false;
   if(viewer==null||!isAttachedToWindow())return;
   viewer.render(frameTimeNanos);
   postFrame();
  }
 };
 public GoroAvatarView(Context context,StatusListener listener){
  super(context);statusListener=listener;
  setBackgroundColor(Color.TRANSPARENT);
  surface=new SurfaceView(context);
  surface.setBackgroundColor(Color.TRANSPARENT);
  addView(surface,new FrameLayout.LayoutParams(-1,-1));
  surface.setOnTouchListener((v,event)->{
   if(viewer!=null){viewer.onTouchEvent(event);return true;}
   return false;
  });
  loadIfPresent();
 }
 private void loadIfPresent(){
  byte[] bytes;
  try{bytes=readAsset();}
  catch(Exception missing){
   TextView label=new TextView(getContext());
   label.setText("GORO 3D · ESPERANDO MODELO GLB RIGGEADO");
   label.setTextColor(Color.WHITE);label.setTextSize(15);label.setGravity(android.view.Gravity.CENTER);
   addView(label,new FrameLayout.LayoutParams(-1,-1));
   status("Modelo GLB de GORO no incluido: renderizador Filament preparado, avatar pendiente.");
   return;
  }
  try{
   viewer=new ModelViewer(surface,Engine.create(),new UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK),null);
   viewer.setAutoPlayAnimations(true);
   viewer.getView().setAntiAliasing(View.AntiAliasing.FXAA);
   viewer.loadModelGlb(ByteBuffer.wrap(bytes));
   viewer.transformToUnitCube();
   status("GLB de GORO cargado en Filament. Animaciones del archivo habilitadas.");
  }catch(Throwable error){
   if(viewer!=null){try{viewer.destroy();}catch(Throwable ignored){}viewer=null;}
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
 @Override protected void onAttachedToWindow(){
  super.onAttachedToWindow();postFrame();
 }
 @Override protected void onDetachedFromWindow(){
  Choreographer.getInstance().removeFrameCallback(frameCallback);framePosted=false;
  if(viewer!=null){viewer.destroy();viewer=null;}
  super.onDetachedFromWindow();
 }
}