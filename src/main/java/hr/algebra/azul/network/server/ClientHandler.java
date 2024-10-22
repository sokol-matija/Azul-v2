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
                case LOBBY_UPDATE:
                    // Create new lobby
                    if (message.getLobby().getHostId().equals(playerId)) {
                        GameLobby newLobby = server.getLobbyManager().createLobby(playerId);
                        // Broadcast to all clients
                        broadcastLobbyUpdate(newLobby);
                    }
                    break;
                case PLAYER_JOINED:
                    handlePlayerJoinLobby(message);
                    break;
                // ... other cases
            }
        } catch (Exception e) {
            LOGGER.severe("Error handling lobby message: " + e.getMessage());
        }
    }

    private void broadcastLobbyUpdate(GameLobby lobby) {
        LobbyMessage updateMessage = new LobbyMessage(
                LobbyMessageType.LOBBY_UPDATE,
                lobby
        );
        // Broadcast to all connected clients
        server.getClients().forEach(client ->
                client.sendMessage(updateMessage)
        );
    }

    private void handlePlayerJoinLobby(LobbyMessage message) {
        GameLobby lobby = message.getLobby();
        server.getLobbyManager().addPlayerToLobby(
                lobby.getLobbyId(),
                this,
                playerId
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