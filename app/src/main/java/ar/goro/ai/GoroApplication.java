package ar.goro.ai;

import android.app.Application;

/**
 * Application startup must remain independent from external Bluetooth hardware.
 * The vendor RCSP stack is initialized after the user's explicit connect action.
 */
public final class GoroApplication extends Application {
 @Override public void onCreate(){super.onCreate();}
}
