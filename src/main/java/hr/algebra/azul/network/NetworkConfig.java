// File: src/main/java/hr/algebra/azul/network/NetworkConfig.java
package hr.algebra.azul.network;

public class NetworkConfig {
    public static final String DEFAULT_HOST = "localhost";
    public static final int DEFAULT_PORT = 5000;
    public static final int SOCKET_TIMEOUT = 30000; // 30 seconds
    public static final int MAX_RECONNECT_ATTEMPTS = 3;

    private NetworkConfig() {
        // Private constructor to prevent instantiation
    }
}