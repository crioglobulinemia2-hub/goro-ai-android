package ar.goro.ai.core;
public interface DeviceBridge {
 enum Connection { DISCONNECTED, CONNECTING, CONNECTED, UNSUPPORTED }
 Connection state();
 void disconnect();
}
