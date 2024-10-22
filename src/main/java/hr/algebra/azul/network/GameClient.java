// File: src/main/java/hr/algebra/azul/network/GameClient.java
package hr.algebra.azul.network;

import hr.algebra.azul.network.lobby.*;
import hr.algebra.azul.network.server.LobbyMessage;
import javafx.application.Platform;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.*;
import java.util.logging.Logger;

public class GameClient {
    private static final Logger LOGGER = Logger.getLogger(GameClient.class.getName());
    private static final int CONNECTION_TIMEOUT = 5000; // 5 seconds
    private static final String PING_MESSAGE = "PING";
    private static final String PONG_MESSAGE = "PONG";
    private static final long PING_INTERVAL = 1000;

    private final String host;
    private final int port;
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private volatile boolean running;
    private final ExecutorService executorService;
    private final ScheduledExecutorService pingExecutor;
    private final BlockingQueue<Object> messageQueue;
    private ScheduledFuture<?> pingTask;

    private GameStateUpdateHandler gameHandler;
    private LobbyUpdateHandler lobbyHandler;
    private ConnectionStatusHandler connectionHandler;

    private String clientId;
    private GameLobby currentLobby;

    public GameClient(String host, int port) {
        this.host = host;
        this.port = port;
        this.executorService = Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r);
            thread.setDaemon(true);
            return thread;
        });
        this.pingExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r);
            thread.setDaemon(true);
            return thread;
        });
        this.messageQueue = new LinkedBlockingQueue<>();
        this.clientId = generateClientId();
    }

    private String generateClientId() {
        return "Player-" + System.currentTimeMillis() % 10000;
    }

    public CompletableFuture<Boolean> connect() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                socket = new Socket(host, port);
                socket.setKeepAlive(true); // Enable TCP keep-alive
                socket.setTcpNoDelay(true); // Disable Nagle's algorithm

                out = new ObjectOutputStream(socket.getOutputStream());
                in = new ObjectInputStream(socket.getInputStream());

                running = true;
                startMessageProcessor();
                startMessageReceiver();
                startPingScheduler();

                if (connectionHandler != null) {
                    Platform.runLater(() -> connectionHandler.onConnected());
                }

                return true;
            } catch (IOException e) {
                LOGGER.severe("Connection failed: " + e.getMessage());
                if (connectionHandler != null) {
                    Platform.runLater(() -> connectionHandler.onConnectionFailed(e.getMessage()));
                }
                return false;
            }
        }, executorService);
    }

    private void startPingScheduler() {
        pingTask = pingExecutor.scheduleAtFixedRate(() -> {
            if (running) {
                try {
                    sendMessage(PING_MESSAGE);
                } catch (Exception e) {
                    handleDisconnection("Failed to send ping: " + e.getMessage());
                }
            }
        }, PING_INTERVAL, PING_INTERVAL, TimeUnit.MILLISECONDS);
    }

    private void startMessageProcessor() {
        executorService.submit(() -> {
            while (running) {
                try {
                    Object message = messageQueue.take();
                    if (message instanceof String) {
                        handleStringMessage((String) message);
                    } else {
                        processMessage(message);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    LOGGER.severe("Error processing message: " + e.getMessage());
                }
            }
        });
    }

    private void handleStringMessage(String message) {
        switch (message) {
            case PING_MESSAGE:
                sendMessage(PONG_MESSAGE);
                break;
            case PONG_MESSAGE:
                // Received pong, connection is alive
                break;
            default:
                LOGGER.warning("Unknown string message: " + message);
        }
    }

    private void startMessageReceiver() {
        executorService.submit(() -> {
            while (running) {
                try {
                    Object message = in.readObject();
                    messageQueue.put(message);
                } catch (IOException e) {
                    handleDisconnection("Connection lost: " + e.getMessage());
                    break;
                } catch (Exception e) {
                    LOGGER.severe("Error receiving message: " + e.getMessage());
                }
            }
        });
    }

    private void processMessage(Object message) {
        try {
            if (message instanceof GameMessage gameMessage) {
                handleGameMessage(gameMessage);
            } else if (message instanceof LobbyMessage lobbyMessage) {
                handleLobbyMessage(lobbyMessage);
            }
        } catch (Exception e) {
            LOGGER.severe("Error processing message: " + e.getMessage());
        }
    }

    private void handleGameMessage(GameMessage message) {
        if (gameHandler == null) return;

        Platform.runLater(() -> {
            switch (message.getType()) {
                case MOVE:
                    gameHandler.onGameMove(message.getAction());
                    break;
                case SYNC:
                    gameHandler.onGameStateUpdate(message.getGameState());
                    break;
                case CHAT:
                    gameHandler.onChatMessage(message.getPlayerId(), message.toString());
                    break;
                case JOIN:
                case LEAVE:
                    gameHandler.onPlayerStatusChange(message.getPlayerId(),
                            message.getType() == MessageType.JOIN);
                    break;
            }
        });
    }

    private void handleLobbyMessage(LobbyMessage message) {
        if (lobbyHandler == null) return;

        Platform.runLater(() -> {
            switch (message.getType()) {
                case LOBBY_UPDATE:
                    currentLobby = message.getLobby();
                    lobbyHandler.onLobbyUpdate(message.getLobby());
                    break;
                case GAME_START:
                    lobbyHandler.onGameStart(message.getLobby(), message.getGameState());
                    break;
                case LOBBY_CLOSED:
                    if (currentLobby != null &&
                            currentLobby.getLobbyId().equals(message.getLobby().getLobbyId())) {
                        currentLobby = null;
                    }
                    lobbyHandler.onLobbyClosed(message.getLobby());
                    break;
            }
        });
    }

    public void sendGameMessage(GameMessage message) {
        sendMessage(message);
    }

    public void sendLobbyMessage(LobbyMessage message) {
        sendMessage(message);
    }

    public void sendMessage(Object message) {
        executorService.submit(() -> {
            try {
                out.writeObject(message);
                out.flush();
            } catch (IOException e) {
                LOGGER.severe("Error sending message: " + e.getMessage());
                handleDisconnection("Failed to send message: " + e.getMessage());
            }
        });
    }

    private void handleDisconnection(String reason) {
        if (!running) return;

        running = false;
        if (connectionHandler != null) {
            Platform.runLater(() -> connectionHandler.onDisconnected(reason));
        }

        cleanup();
    }

    public void disconnect() {
        if (!running) return;

        running = false;
        if (pingTask != null) {
            pingTask.cancel(true);
        }
        cleanup();

        if (connectionHandler != null) {
            Platform.runLater(() -> connectionHandler.onDisconnected("Client disconnected"));
        }
    }

    private void cleanup() {
        try {
            if (socket != null && !socket.isClosed()) socket.close();
            if (out != null) out.close();
            if (in != null) in.close();
        } catch (IOException e) {
            LOGGER.severe("Error during cleanup: " + e.getMessage());
        }

        executorService.shutdownNow();
        pingExecutor.shutdownNow();
    }

    // Handlers setters
    public void setGameHandler(GameStateUpdateHandler handler) {
        this.gameHandler = handler;
    }

    public void setLobbyHandler(LobbyUpdateHandler handler) {
        this.lobbyHandler = handler;
    }

    public void setConnectionHandler(ConnectionStatusHandler handler) {
        this.connectionHandler = handler;
    }

    public String getClientId() {
        return clientId;
    }

    public GameLobby getCurrentLobby() {
        return currentLobby;
    }
}


