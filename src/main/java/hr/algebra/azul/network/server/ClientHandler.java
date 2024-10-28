package hr.algebra.azul.network.server;

import hr.algebra.azul.model.User;
import hr.algebra.azul.model.UserStatus;
import hr.algebra.azul.network.GameMessage;
import hr.algebra.azul.network.GameServer;
import hr.algebra.azul.network.GameState;
import hr.algebra.azul.network.MessageType;
import hr.algebra.azul.network.lobby.GameLobby;
import hr.algebra.azul.network.lobby.LobbyStatus;

import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClientHandler implements Runnable {
    private static final Logger LOGGER = Logger.getLogger(ClientHandler.class.getName());
    private static final String PING_MESSAGE = "PING";
    private static final String PONG_MESSAGE = "PONG";
    private static final int SOCKET_TIMEOUT = 30000; // 30 seconds
    private static final int MAX_RECONNECT_ATTEMPTS = 3;

    private final Socket socket;
    private final GameServer server;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private final AtomicBoolean running;
    public User user;
    private GameLobby currentLobby;
    private int reconnectAttempts;
    private long lastPingTime;

    public ClientHandler(Socket socket, GameServer server) {
        this.socket = socket;
        this.server = server;
        this.running = new AtomicBoolean(true);
        this.reconnectAttempts = 0;
        this.lastPingTime = System.currentTimeMillis();
        initializeStreams();
    }

    private void initializeStreams() {
        try {
            socket.setSoTimeout(SOCKET_TIMEOUT);
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error creating streams: " + e.getMessage(), e);
            stop();
        }
    }

    @Override
    public void run() {
        try {
            while (running.get()) {
                try {
                    Object message = in.readObject();
                    lastPingTime = System.currentTimeMillis();

                    if (message instanceof String) {
                        handleStringMessage((String) message);
                    } else if (message instanceof GameMessage) {
                        handleGameMessage((GameMessage) message);
                    } else if (message instanceof LobbyMessage) {
                        handleLobbyMessage((LobbyMessage) message);
                    }
                } catch (SocketTimeoutException e) {
                    handleTimeout();
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            handleConnectionError(e);
        } finally {
            cleanup();
        }
    }

    private void handleTimeout() {
        if (System.currentTimeMillis() - lastPingTime > SOCKET_TIMEOUT) {
            if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                attemptReconnect();
            } else {
                LOGGER.warning("Client " + user.getId() + " timed out after max reconnection attempts");
                stop();
            }
        }
    }

    private void attemptReconnect() {
        reconnectAttempts++;
        LOGGER.info("Attempting reconnection for client " + user.getId() + " (Attempt " + reconnectAttempts + ")");

        try {
            socket.close();
            initializeStreams();
            // Notify client about successful reconnection
            sendMessage(new GameMessage(MessageType.SYNC, user.getId(), null, null));
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Reconnection attempt failed: " + e.getMessage(), e);
        }
    }

    private void handleConnectionError(Exception e) {
        LOGGER.log(Level.WARNING, "Connection error for client " + user.getId() + ": " + e.getMessage(), e);
        if (currentLobby != null) {
            handlePlayerLeave(new LobbyMessage(LobbyMessageType.PLAYER_LEFT, currentLobby));
        }
    }

    private void handleStringMessage(String message) {
        try {
            switch (message) {
                case PING_MESSAGE -> {
                    sendMessage(PONG_MESSAGE);
                    lastPingTime = System.currentTimeMillis();
                }
                case PONG_MESSAGE -> lastPingTime = System.currentTimeMillis();
                default -> LOGGER.warning("Unknown string message: " + message);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error handling string message: " + e.getMessage(), e);
        }
    }

    private void handleGameMessage(GameMessage message) {
        try {
            switch (message.getType()) {
                case MOVE -> handleGameMove(message);
                case CHAT -> handleChatMessage(message);
                case JOIN -> handlePlayerJoinGame(message);
                case LEAVE -> handlePlayerLeaveGame(message);
                case SYNC -> handleGameSync(message);
                default -> LOGGER.warning("Unknown game message type: " + message.getType());
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error handling game message: " + e.getMessage(), e);
            sendErrorMessage("Failed to process game action: " + e.getMessage());
        }
    }

    private void handleGameMove(GameMessage message) {
        validateGameState();
        server.broadcast(message, this);
    }

    private void handleChatMessage(GameMessage message) {
        if (currentLobby != null) {
            server.broadcast(message, this);
        }
    }

    private void handlePlayerJoinGame(GameMessage message) {
        if (user != null) {
            user.setStatus(UserStatus.IN_GAME);
            LOGGER.info("User joined game: " + user.getUsername());
            server.broadcast(message, this);
        }
    }

    private void handlePlayerLeaveGame(GameMessage message) {
        LOGGER.info("Player left: " + user.getUsername());
        server.broadcast(message, this);
        if (currentLobby != null) {
            handlePlayerLeave(new LobbyMessage(LobbyMessageType.PLAYER_LEFT, currentLobby));
        }
    }

    private void handleGameSync(GameMessage message) {
        server.broadcast(message, this);
    }

    private void handleLobbyMessage(LobbyMessage message) {
        try {
            switch (message.getType()) {
                case LOBBY_UPDATE -> handleLobbyUpdate(message);
                case PLAYER_JOINED -> handlePlayerJoin(message);
                case PLAYER_LEFT -> handlePlayerLeave(message);
                case GAME_START -> handleGameStart(message);
                case LOBBY_CLOSED -> handleLobbyClosed(message);
                case ERROR -> handleError(message);
                default -> LOGGER.warning("Unknown lobby message type: " + message.getType());
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error handling lobby message: " + e.getMessage());
            sendErrorMessage("Failed to process lobby action: " + e.getMessage());
        }
    }

    private void handleLobbyUpdate(LobbyMessage message) {
        GameLobby lobby = message.getLobby();
        if (lobby != null) {
            try {
                // This will create the lobby if it doesn't exist, or update it if it does
                server.getLobbyManager().updateLobby(lobby);

                // Broadcast the update to all clients
                server.getLobbyManager().broadcastLobbyUpdate(lobby);

                LOGGER.info("Lobby updated: " + lobby.getLobbyId() +
                        " players: " + lobby.getPlayers().size());
            } catch (Exception e) {
                LOGGER.severe("Error updating lobby: " + e.getMessage());
                sendErrorMessage("Failed to update lobby: " + e.getMessage());
            }
        }
    }

    private void handlePlayerJoin(LobbyMessage message) {
        GameLobby lobby = message.getLobby();
        if (lobby == null) {
            sendErrorMessage("Invalid lobby data");
            return;
        }

        try {
            User joiningUser = this.user; // Use the client's user object
            server.getLobbyManager().addPlayerToLobby(
                    lobby.getLobbyId(),
                    this,
                    joiningUser
            );

            // Get the updated lobby after adding the player
            GameLobby updatedLobby = server.getLobbyManager().getLobby(lobby.getLobbyId());

            if (updatedLobby != null) {
                // Broadcast the updated lobby state to ALL clients
                LobbyMessage updateMessage = new LobbyMessage(
                        LobbyMessageType.LOBBY_UPDATE,
                        updatedLobby
                );
                server.getClients().forEach(client -> client.sendMessage(updateMessage));
            }

            LOGGER.info("Player " + joiningUser.getUsername() + " successfully joined lobby " + lobby.getLobbyId());

        } catch (IllegalStateException e) {
            LOGGER.warning("Failed to join lobby: " + e.getMessage());
            sendErrorMessage("Failed to join lobby: " + e.getMessage());
        }
    }

    private void handlePlayerLeave(LobbyMessage message) {
        GameLobby lobby = message.getLobby();
        if (lobby != null) {
            server.getLobbyManager().removePlayerFromLobby(
                    lobby.getLobbyId(),
                    user.getId()
            );
            currentLobby = null;
            broadcastLobbyUpdate(lobby);
        }
    }

    private void handleGameStart(LobbyMessage message) {
        GameLobby lobby = message.getLobby();
        if (lobby != null && lobby.canStart()) {
            lobby.setStatus(LobbyStatus.STARTING);
            GameState gameState = message.getGameState();

            // Broadcast game start to all players in lobby
            LobbyMessage startMessage = new LobbyMessage(
                    LobbyMessageType.GAME_START,
                    lobby,
                    gameState
            );
            // TODO: Brodcat to lobby not working
            server.getLobbyManager().broadcastToLobby(lobby.getLobbyId(), startMessage);
        }
    }

    private void handleLobbyClosed(LobbyMessage message) {
        GameLobby lobby = message.getLobby();
        if (lobby != null) {
            server.getLobbyManager().closeLobby(lobby.getLobbyId());
            currentLobby = null;
        }
    }

    private void handleError(LobbyMessage message) {
        LOGGER.warning("Received error message: " + message.getGameState());
    }

    private void validateGameState() {
        if (currentLobby == null || currentLobby.getStatus() != LobbyStatus.IN_PROGRESS) {
            throw new IllegalStateException("Invalid game state");
        }
    }

    private void sendErrorMessage(String errorMessage) {
        LOGGER.warning("Sending error message: " + errorMessage);
        // sendMessage(new GameMessage(MessageType.ERROR, playerId, null, null));
//        sendMessage(new LobbyMessage(
//                LobbyMessageType.ERROR,
//                currentLobby,
//                errorMessage
//        ));
    }

    private void broadcastLobbyUpdate(GameLobby lobby) {
        LobbyMessage updateMessage = new LobbyMessage(
                LobbyMessageType.LOBBY_UPDATE,
                lobby
        );
        server.getClients().forEach(client -> client.sendMessage(updateMessage));
    }

    public synchronized void sendMessage(Object message) {
        try {
            out.writeObject(message);
            out.flush();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error sending message: " + e.getMessage(), e);
            handleConnectionError(e);
        }
    }

    public void stop() {
        running.set(false);
        cleanup();
    }

    private void cleanup() {
        try {
            if (currentLobby != null) {
                handlePlayerLeave(new LobbyMessage(LobbyMessageType.PLAYER_LEFT, currentLobby));
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            if (out != null) {
                out.close();
            }
            if (in != null) {
                in.close();
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error during cleanup: " + e.getMessage(), e);
        } finally {
            server.removeClient(this);
        }
    }

    public GameLobby getCurrentLobby() {
        return currentLobby;
    }
}