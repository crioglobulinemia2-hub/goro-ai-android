package ar.goro.ai.ui;
import android.graphics.Color;
/** Approved GORO palettes: warm premium light and graphite/copper/cyan dark. */
public final class GoroTheme {
 public enum Mode { LIGHT, DARK }
 public final int background,surface,surfaceElevated,text,muted,accent,accentSecondary,border,robotBackdrop;
 private GoroTheme(Mode mode){
  if(mode==Mode.DARK){background=0xff171b20;surface=0xff222830;surfaceElevated=0xff2c343d;text=0xfff3f0e9;muted=0xffa9b2bd;accent=0xffc58b59;accentSecondary=0xff55c7df;border=0xff39434e;robotBackdrop=0xff252c34;}
  else{background=0xfff1eee7;surface=0xfffffdf8;surfaceElevated=0xffe8e2d7;text=0xff26313c;muted=0xff727b83;accent=0xffb77c48;accentSecondary=0xff277f9a;border=0xffd8d0c3;robotBackdrop=0xffe3ddd2;}
 }
 public static GoroTheme of(Mode mode){return new GoroTheme(mode);}
 public static int colorWithAlpha(int color,int alpha){return Color.argb(alpha,Color.red(color),Color.green(color),Color.blue(color));}
}
