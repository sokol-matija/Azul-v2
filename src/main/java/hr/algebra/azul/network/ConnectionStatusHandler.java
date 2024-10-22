
package hr.algebra.azul.network;

public interface ConnectionStatusHandler {
    void onConnected();
    void onDisconnected(String reason);
    void onConnectionFailed(String reason);
}