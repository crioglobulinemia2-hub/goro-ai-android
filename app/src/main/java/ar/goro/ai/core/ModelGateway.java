package ar.goro.ai.core;
/** Remote model access is implemented behind a server-side gateway. Never embed provider keys in the APK. */
public interface ModelGateway {
 interface Callback { void completed(String reply,String error); }
 void request(String conversationId,String userText,Callback callback);
}
