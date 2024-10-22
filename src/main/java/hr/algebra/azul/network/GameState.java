// File: src/main/java/hr/algebra/azul/network/GameState.java
package hr.algebra.azul.network;

import hr.algebra.azul.model.Game;
import hr.algebra.azul.model.Player;
import java.io.Serializable;
import java.util.List;
import java.util.ArrayList;

public class GameState implements Serializable {
    private static final long serialVersionUID = 1L;

    private Game game;
    private List<String> connectedPlayers;
    private String currentPlayerId;
    private GamePhase currentPhase;

    public enum GamePhase {
        WAITING_FOR_PLAYERS,
        IN_PROGRESS,
        ROUND_END,
        GAME_END
    }

    public GameState(Game game) {
        this.game = game;
        this.connectedPlayers = new ArrayList<>();
        this.currentPhase = GamePhase.WAITING_FOR_PLAYERS;

        // Initialize connected players from the game state
        for (Player player : game.getPlayers()) {
            connectedPlayers.add(player.getName());
        }

        // Set current player
        if (game.getCurrentPlayer() != null) {
            this.currentPlayerId = game.getCurrentPlayer().getName();
        }
    }

    // Getters and setters
    public Game getGame() {
        return game;
    }

    public void setGame(Game game) {
        this.game = game;
    }

    public List<String> getConnectedPlayers() {
        return new ArrayList<>(connectedPlayers);
    }

    public void addPlayer(String playerId) {
        if (!connectedPlayers.contains(playerId)) {
            connectedPlayers.add(playerId);
        }
    }

    public void removePlayer(String playerId) {
        connectedPlayers.remove(playerId);
    }

    public String getCurrentPlayerId() {
        return currentPlayerId;
    }

    public void setCurrentPlayerId(String currentPlayerId) {
        this.currentPlayerId = currentPlayerId;
    }

    public GamePhase getCurrentPhase() {
        return currentPhase;
    }

    public void setCurrentPhase(GamePhase currentPhase) {
        this.currentPhase = currentPhase;
    }

    @Override
    public String toString() {
        return "GameState{" +
                "connectedPlayers=" + connectedPlayers +
                ", currentPlayerId='" + currentPlayerId + '\'' +
                ", currentPhase=" + currentPhase +
                '}';
    }
}

