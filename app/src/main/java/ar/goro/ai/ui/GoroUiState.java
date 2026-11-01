package ar.goro.ai.ui;
public final class GoroUiState {
 public enum AvatarState { IDLE, LISTENING, THINKING, SPEAKING, CONFIRMING, ALERT }
 public enum ThemeMode { LIGHT, DARK }
 private AvatarState avatarState=AvatarState.IDLE;private ThemeMode theme=ThemeMode.LIGHT;private GoroSection section=GoroSection.HOME;
 public AvatarState avatarState(){return avatarState;}public void avatarState(AvatarState s){if(s!=null)avatarState=s;}
 public ThemeMode theme(){return theme;}public void toggleTheme(){theme=theme==ThemeMode.LIGHT?ThemeMode.DARK:ThemeMode.LIGHT;}
 public GoroSection section(){return section;}public void section(GoroSection s){if(s!=null)section=s;}
}
