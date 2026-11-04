package ar.goro.ai.ui;
import android.graphics.Color;
/** Approved GORO palettes: warm premium light and graphite/copper/cyan dark. */
public final class GoroTheme {
 public enum Mode { LIGHT, DARK, PROFESSIONAL }
 public final int background,surface,surfaceElevated,text,muted,accent,accentSecondary,border,robotBackdrop;
 private GoroTheme(Mode mode){
  if(mode==Mode.DARK){background=0xff030e18;surface=0xff091c2e;surfaceElevated=0xff102c42;text=0xffeef6ff;muted=0xff9eb7cd;accent=0xff36b9ed;accentSecondary=0xff38d8ed;border=0xff21435c;robotBackdrop=0xff0b2337;}
  else if(mode==Mode.PROFESSIONAL){background=0xffe7edf2;surface=0xfff8fafc;surfaceElevated=0xffd4e0e9;text=0xff172938;muted=0xff536a7b;accent=0xff2b6e93;accentSecondary=0xff268bb2;border=0xffb8ccd9;robotBackdrop=0xffc7dbe8;}
  else{background=0xfff1eee7;surface=0xfffffdf8;surfaceElevated=0xffe8e2d7;text=0xff26313c;muted=0xff727b83;accent=0xffb77c48;accentSecondary=0xff277f9a;border=0xffd8d0c3;robotBackdrop=0xffe3ddd2;}
 }
 public static GoroTheme of(Mode mode){return new GoroTheme(mode);}
 public static int colorWithAlpha(int color,int alpha){return Color.argb(alpha,Color.red(color),Color.green(color),Color.blue(color));}
}
