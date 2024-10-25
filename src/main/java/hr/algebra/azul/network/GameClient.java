package hr.algebra.azul.network;

import hr.algebra.azul.network.lobby.*;
import hr.algebra.azul.network.server.*;
import javafx.application.Platform;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class GameClient {
    private static final Logger LOGGER = Logger.getLogger(GameClient.class.getName());
    private static final int CONNECTION_TIMEOUT = 5000;
    private static final int SOCKET_TIMEOUT = 30000;
    private static final int MAX_RECONNECT_ATTEMPTS = 3;
    private static final long RECONNECT_DELAY = 2000;
    private static final int COMPRESSION_THRESHOLD = 1024;
    private static final String PING_MESSAGE = "PING";
    private static final String PONG_MESSAGE = "PONG";

    private final String host;
    private final int port;
    private final String clientId;
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private volatile boolean running;
    private final ExecutorService executorService;
    private final ScheduledExecutorService scheduledExecutor;
    private final BlockingQueue<Object> messageQueue;
    private final Map<String, CompletableFuture<Object>> responseHandlers;
    private final Map<String, Integer> reconnectAttempts;
    private final Object connectionLock = new Object();
    private ScheduledFuture<?> pingTask;
    private ScheduledFuture<?> timeoutTask;

    // Handlers
    private GameStateUpdateHandler gameHandler;
    private LobbyUpdateHandler lobbyHandler;
    private ConnectionStatusHandler connectionHandler;
    private MessageHandler messageHandler;

    public GameClient(String host, int port) {
        this.host = host;
        this.port = port;
        this.clientId = generateClientId();
        this.executorService = Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r);
            thread.setDaemon(true);
            return thread;
        });
        this.scheduledExecutor = Executors.newScheduledThreadPool(2);
        this.messageQueue = new LinkedBlockingQueue<>();
        this.responseHandlers = new ConcurrentHashMap<>();
        this.reconnectAttempts = new ConcurrentHashMap<>();
    }

    private String generateClientId() {
        return "Player-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public CompletableFuture<Boolean> connect() {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        synchronized (connectionLock) {
            if (running) {
                future.complete(true);
                return future;
            }

            executorService.submit(() -> {
                try {
                    socket = new Socket();
                    socket.connect(new InetSocketAddress(host, port), CONNECTION_TIMEOUT);
                    socket.setSoTimeout(SOCKET_TIMEOUT);
                    socket.setKeepAlive(true);
                    socket.setTcpNoDelay(true);

                    setupStreams();
                    running = true;
                    startMessageProcessor();
                    startMessageReceiver();
                    startPingScheduler();

                    notifyConnectionEstablished();
                    future.complete(true);
                } catch (Exception e) {
                    LOGGER.severe("Connection failed: " + e.getMessage());
                    notifyConnectionFailed(e.getMessage());
                    future.complete(false);
                }
            });
        }

        return future;
    }

    private void setupStreams() throws IOException {
        out = new ObjectOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        out.flush();
        in = new ObjectInputStream(new BufferedInputStream(socket.getInputStream()));
    }

    private void startMessageProcessor() {
        executorService.submit(() -> {
            while (running) {
                try {
                    Object message = messageQueue.take();
                    processMessage(message);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    LOGGER.severe("Error processing message: " + e.getMessage());
                }
            }
        });
    }

    private void startMessageReceiver() {
        executorService.submit(() -> {
            while (running) {
                try {
                    Object message = in.readObject();
                    if (message != null) {
                        handleIncomingMessage(message);
                    }
                } catch (SocketTimeoutException e) {
                    handleSocketTimeout();
                } catch (IOException e) {
                    handleConnectionLost(e);
                    break;
                } catch (Exception e) {
                    LOGGER.severe("Error receiving message: " + e.getMessage());
                }
            }
        });
    }

    private void startPingScheduler() {
        pingTask = scheduledExecutor.scheduleAtFixedRate(() -> {
            try {
                sendRawMessage(PING_MESSAGE);
            } catch (Exception e) {
                LOGGER.warning("Failed to send ping: " + e.getMessage());
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
    }

    private void handleIncomingMessage(Object message) {
        try {
            if (message instanceof String) {
                handleStringMessage((String) message);
            } else {
                messageQueue.put(message);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void handleStringMessage(String message) {
        switch (message) {
            case PING_MESSAGE -> sendRawMessage(PONG_MESSAGE);
            case PONG_MESSAGE -> resetTimeoutTimer();
            default -> LOGGER.warning("Unknown string message: " + message);
        }
    }

    private void resetTimeoutTimer() {
        if (timeoutTask != null) {
            timeoutTask.cancel(false);
        }
        timeoutTask = scheduledExecutor.schedule(
                this::handleSocketTimeout,
                SOCKET_TIMEOUT,
                TimeUnit.MILLISECONDS
        );
    }

    private void handleSocketTimeout() {
        if (running) {
            LOGGER.warning("Socket timeout detected");
            handleConnectionLost(new SocketTimeoutException("Connection timed out"));
        }
    }

    private void handleConnectionLost(Exception e) {
        synchronized (connectionLock) {
            if (!running) return;

            running = false;
            cleanup();
            notifyConnectionLost(e.getMessage());

            if (shouldAttemptReconnect()) {
                scheduleReconnect();
            }
        }
    }

    private boolean shouldAttemptReconnect() {
        int attempts = reconnectAttempts.getOrDefault(clientId, 0);
        return attempts < MAX_RECONNECT_ATTEMPTS;
    }

    private void scheduleReconnect() {
        int attempts = reconnectAttempts.getOrDefault(clientId, 0);
        reconnectAttempts.put(clientId, attempts + 1);

        scheduledExecutor.schedule(() -> {
            LOGGER.info("Attempting reconnection...");
            connect().thenAccept(success -> {
                if (success) {
                    reconnectAttempts.remove(clientId);
                    LOGGER.info("Reconnection successful");
                }
            });
        }, RECONNECT_DELAY * (attempts + 1), TimeUnit.MILLISECONDS);
    }

    private void processMessage(Object message) {
        try {
            if (message instanceof GameMessage gameMessage) {
                processGameMessage(gameMessage);
            } else if (message instanceof LobbyMessage lobbyMessage) {
                processLobbyMessage(lobbyMessage);
            }
        } catch (Exception e) {
            LOGGER.severe("Error processing message: " + e.getMessage());
        }
    }

    private void processGameMessage(GameMessage message) {
        if (gameHandler != null) {
            Platform.runLater(() -> {
                switch (message.getType()) {
                    case MOVE -> gameHandler.onGameMove(message.getAction());
                    case SYNC -> gameHandler.onGameStateUpdate(message.getGameState());
                    case CHAT -> gameHandler.onChatMessage(message.getPlayerId(), message.getChatContent());
                    case JOIN, LEAVE -> gameHandler.onPlayerStatusChange(message.getPlayerId(),
                            message.getType() == MessageType.JOIN);
                }
            });
        }
    }

    private void processLobbyMessage(LobbyMessage message) {
        if (lobbyHandler != null) {
            Platform.runLater(() -> {
                switch (message.getType()) {
                    case LOBBY_UPDATE -> lobbyHandler.onLobbyUpdate(message.getLobby());
                    case GAME_START -> lobbyHandler.onGameStart(message.getLobby(), message.getGameState());
                    case LOBBY_CLOSED -> lobbyHandler.onLobbyClosed(message.getLobby());
                }
            });
        }
    }

    public void sendGameMessage(GameMessage message) {
        if (!running) return;
        sendMessage(message);
    }

    public void sendLobbyMessage(LobbyMessage message) {
        if (!running) return;
        sendMessage(message);
    }

    public void sendMessage(Object message) {
        executorService.submit(() -> {
            try {
                byte[] serialized = serializeMessage(message);
                byte[] compressed = compressIfNeeded(serialized);
                out.writeObject(compressed);
                out.flush();
            } catch (Exception e) {
                LOGGER.severe("Failed to send message: " + e.getMessage());
                handleConnectionLost(e);
            }
        });
    }

    private byte[] serializeMessage(Object message) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(message);
            return baos.toByteArray();
        }
    }

    private byte[] compressIfNeeded(byte[] data) throws IOException {
        if (data.length < COMPRESSION_THRESHOLD) {
            return data;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
            gzip.write(data);
            gzip.finish();
            return baos.toByteArray();
        }
    }

    private void sendRawMessage(String message) {
        try {
            out.writeObject(message);
            out.flush();
        } catch (IOException e) {
            LOGGER.severe("Failed to send raw message: " + e.getMessage());
            handleConnectionLost(e);
        }
    }

    private void notifyConnectionEstablished() {
        if (connectionHandler != null) {
            Platform.runLater(() -> connectionHandler.onConnected());
        }
    }

    private void notifyConnectionLost(String reason) {
        if (connectionHandler != null) {
            Platform.runLater(() -> connectionHandler.onDisconnected(reason));
        }
    }

    private void notifyConnectionFailed(String reason) {
        if (connectionHandler != null) {
            Platform.runLater(() -> connectionHandler.onConnectionFailed(reason));
        }
    }

    public void disconnect() {
        synchronized (connectionLock) {
            if (!running) return;

            running = false;
            cleanup();
            notifyConnectionLost("Client disconnected");
        }
    }

    private void cleanup() {
        try {
            if (pingTask != null) {
                pingTask.cancel(true);
            }
            if (timeoutTask != null) {
                timeoutTask.cancel(true);
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            if (out != null) out.close();
            if (in != null) in.close();
        } catch (IOException e) {
            LOGGER.severe("Error during cleanup: " + e.getMessage());
        }
    }

    // Handler setters
    public void setGameHandler(GameStateUpdateHandler handler) {
        this.gameHandler = handler;
    }

    public void setLobbyHandler(LobbyUpdateHandler handler) {
        this.lobbyHandler = handler;
    }

    public void setConnectionHandler(ConnectionStatusHandler handler) {
        this.connectionHandler = handler;
    }

    public void setMessageHandler(MessageHandler handler) {
        this.messageHandler = handler;
    }

    public String getClientId() {
        return clientId;
    }

    public interface MessageHandler {
        void onMessage(Object message);
        void onError(String error);
    }
}
