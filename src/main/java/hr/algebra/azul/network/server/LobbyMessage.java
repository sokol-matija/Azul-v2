package hr.algebra.azul.network.server;

import hr.algebra.azul.network.GameState;
import hr.algebra.azul.network.lobby.GameLobby;
import java.io.Serializable;

public class LobbyMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private LobbyMessageType type;
    private GameLobby lobby;
    private GameState gameState;
    private String errorMessage;

    public LobbyMessage(LobbyMessageType type, GameLobby lobby) {
        this.type = type;
        this.lobby = lobby;
    }

    public LobbyMessage(LobbyMessageType type, GameLobby lobby, GameState gameState) {
        this.type = type;
        this.lobby = lobby;
        this.gameState = gameState;
    }

    public LobbyMessage(LobbyMessageType type, GameLobby lobby, String errorMessage) {
        this.type = type;
        this.lobby = lobby;
        this.errorMessage = errorMessage;
    }
    // Getters
    public LobbyMessageType getType() { return type; }
    public GameLobby getLobby() { return lobby; }
    public GameState getGameState() { return gameState; }
    public String getErrorMessage() { return errorMessage; }
}
