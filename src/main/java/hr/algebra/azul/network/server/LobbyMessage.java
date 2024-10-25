package hr.algebra.azul.network.server;

import hr.algebra.azul.network.GameState;
import hr.algebra.azul.network.lobby.GameLobby;
import java.io.Serializable;
import java.util.List;

public class LobbyMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private final LobbyMessageType type;
    private final String playerId;
    private final GameLobby lobby;
    private final List<GameLobby> lobbies;
    private final GameState gameState;
    private final String errorMessage;

    public LobbyMessage(Builder builder) {
        this.type = builder.type;
        this.playerId = builder.playerId;
        this.lobby = builder.lobby;
        this.lobbies = builder.lobbies;
        this.gameState = builder.gameState;
        this.errorMessage = builder.errorMessage;
    }

    // Getters
    public LobbyMessageType getType() { return type; }
    public String getPlayerId() { return playerId; }
    public GameLobby getLobby() { return lobby; }
    public List<GameLobby> getLobbies() { return lobbies; }
    public GameState getGameState() { return gameState; }
    public String getErrorMessage() { return errorMessage; }

    // Builder class for constructing messages
    public static class Builder {
        private final LobbyMessageType type;
        private String playerId;
        private GameLobby lobby;
        private List<GameLobby> lobbies;
        private GameState gameState;
        private String errorMessage;

        public Builder(LobbyMessageType type) {
            this.type = type;
        }

        public Builder playerId(String playerId) {
            this.playerId = playerId;
            return this;
        }

        public Builder lobby(GameLobby lobby) {
            this.lobby = lobby;
            return this;
        }

        public Builder lobbies(List<GameLobby> lobbies) {
            this.lobbies = lobbies;
            return this;
        }

        public Builder gameState(GameState gameState) {
            this.gameState = gameState;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public LobbyMessage build() {
            return new LobbyMessage(this);
        }
    }
}