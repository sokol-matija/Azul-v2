package hr.algebra.azul.network.server;

import hr.algebra.azul.network.GameMessage;
import hr.algebra.azul.network.GameServer;
import hr.algebra.azul.network.lobby.GameLobby;

import java.io.*;
import java.net.Socket;
import java.util.logging.Logger;

public class ClientHandler implements Runnable {
    private static final Logger LOGGER = Logger.getLogger(ClientHandler.class.getName());
    private static final String PING_MESSAGE = "PING";
    private static final String PONG_MESSAGE = "PONG";

    private final Socket socket;
    private final GameServer server;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private volatile boolean running = true;
    private String playerId;  // Added playerId field

    public ClientHandler(Socket socket, GameServer server) {
        this.socket = socket;
        this.server = server;
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());
        } catch (IOException e) {
            LOGGER.severe("Error creating streams: " + e.getMessage());
        }
    }

    // Add getter and setter for playerId
    public String getPlayerId() {
        return playerId;
    }

    public void setPlayerId(String playerId) {
        this.playerId = playerId;
        LOGGER.info("Client ID set to: " + playerId);
    }

    @Override
    public void run() {
        try {
            while (running) {
                Object message = in.readObject();
                if (message instanceof String) {
                    handleStringMessage((String) message);
                } else if (message instanceof GameMessage) {
                    handleMessage((GameMessage) message);
                } else if (message instanceof LobbyMessage) {
                    handleLobbyMessage((LobbyMessage) message);
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            LOGGER.warning("Error handling client connection: " + e.getMessage());
        } finally {
            stop();
        }
    }

    private void handleStringMessage(String message) {
        try {
            switch (message) {
                case PING_MESSAGE:
                    sendMessage(PONG_MESSAGE);
                    break;
                case PONG_MESSAGE:
                    // Client is alive, do nothing
                    break;
                default:
                    LOGGER.warning("Unknown string message: " + message);
            }
        } catch (Exception e) {
            LOGGER.severe("Error handling string message: " + e.getMessage());
        }
    }

    private void handleMessage(GameMessage message) {
        switch (message.getType()) {
            case MOVE:
                // Process game move
                server.broadcast(message, this);
                break;
            case CHAT:
                // Forward chat message to all clients
                server.broadcast(message, this);
                break;
            case JOIN:
                // Handle new player joining
                if (playerId == null) {
                    setPlayerId(message.getPlayerId());
                }
                LOGGER.info("Player joined: " + playerId);
                server.broadcast(message, this);
                break;
            case LEAVE:
                // Handle player leaving
                LOGGER.info("Player left: " + playerId);
                server.broadcast(message, this);
                break;
            case SYNC:
                // Handle game state synchronization
                server.broadcast(message, this);
                break;
        }
    }

    private void handleLobbyMessage(LobbyMessage message) {
        try {
            switch (message.getType()) {
                case LOBBY_CREATE -> handleLobbyCreate(message);
                case PLAYER_JOINED -> handlePlayerJoinLobby(message);
                case PLAYER_READY -> handlePlayerReady(message);
                case PLAYER_LEFT -> handlePlayerLeaveLobby(message);
                case GAME_START -> handleGameStart(message);
                default -> LOGGER.warning("Unknown lobby message type: " + message.getType());
            }
        } catch (Exception e) {
            LOGGER.severe("Error handling lobby message: " + e.getMessage());
            sendErrorMessage("Error processing request: " + e.getMessage());
        }
    }

    private void handleLobbyCreate(LobbyMessage message) {
        if (playerId == null || message.getPlayerId() == null) {
            sendErrorMessage("Player ID not set");
            return;
        }

        GameLobby lobby = server.getLobbyManager().createLobby(
                message.getPlayerId(),
                message.getLobby().getPlayers().get(message.getPlayerId()).getPlayerName()
        );

        // Send confirmation to the creator
        sendLobbyUpdateMessage(lobby);
    }

    private void handlePlayerJoinLobby(LobbyMessage message) {
        if (message.getLobby() == null || message.getPlayerId() == null) {
            sendErrorMessage("Invalid join request");
            return;
        }

        String lobbyId = message.getLobby().getLobbyId();
        server.getLobbyManager().addPlayerToLobby(
                lobbyId,
                this,
                message.getLobby().getPlayers().get(message.getPlayerId()).getPlayerName()
        );
    }

    private void handlePlayerReady(LobbyMessage message) {
        if (message.getLobby() == null || message.getPlayerId() == null) {
            sendErrorMessage("Invalid ready status update");
            return;
        }

        server.getLobbyManager().updatePlayerReadyStatus(
                message.getLobby().getLobbyId(),
                message.getPlayerId(),
                message.getLobby().getPlayers().get(message.getPlayerId()).isReady()
        );
    }

    private void handlePlayerLeaveLobby(LobbyMessage message) {
        if (message.getLobby() == null || message.getPlayerId() == null) {
            sendErrorMessage("Invalid leave request");
            return;
        }

        server.getLobbyManager().removePlayerFromLobby(
                message.getLobby().getLobbyId(),
                message.getPlayerId()
        );
    }

    private void handleGameStart(LobbyMessage message) {
        if (message.getLobby() == null || !server.getLobbyManager().isLobbyHost(
                message.getLobby().getLobbyId(),
                message.getPlayerId())) {
            sendErrorMessage("Only the host can start the game");
            return;
        }

        server.getLobbyManager().startGame(message.getLobby().getLobbyId());
    }

    private void sendErrorMessage(String errorMessage) {
        LobbyMessage error = new LobbyMessage.Builder(LobbyMessageType.ERROR)
                .errorMessage(errorMessage)
                .build();
        sendMessage(error);
    }

    private void sendLobbyUpdateMessage(GameLobby lobby) {
        LobbyMessage update = new LobbyMessage.Builder(LobbyMessageType.LOBBY_UPDATE)
                .lobby(lobby)
                .build();
        sendMessage(update);
    }

    private void broadcastLobbyUpdate(GameLobby lobby) {
        LobbyMessage updateMessage = new LobbyMessage.Builder(LobbyMessageType.LOBBY_UPDATE)
                .lobby(lobby)
                .build();
        // Broadcast to all connected clients
        server.getClients().forEach(client ->
                client.sendMessage(updateMessage)
        );
    }

    public void sendMessage(Object message) {
        try {
            out.writeObject(message);
            out.flush();
        } catch (IOException e) {
            LOGGER.severe("Error sending message: " + e.getMessage());
        }
    }

    public void stop() {
        running = false;
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            LOGGER.severe("Error closing client connection: " + e.getMessage());
        }
        server.removeClient(this);
    }
}
