package hr.algebra.azul.network;

import hr.algebra.azul.model.Game;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

public class GameState implements Serializable {
    public enum GamePhase {
        WAITING,
        IN_PROGRESS,
        FINISHED
    }

    private final Game game;
    private GamePhase currentPhase;
    private String currentPlayerId;
    private final Set<String> connectedPlayers;
    private int version;

    public GameState(Game game) {
        this.game = game;
        this.currentPhase = GamePhase.WAITING;
        this.connectedPlayers = new HashSet<>();
        this.version = 0;
    }

    public Game getGame() {
        return game;
    }

    public GamePhase getCurrentPhase() {
        return currentPhase;
    }

    public void setCurrentPhase(GamePhase currentPhase) {
        this.currentPhase = currentPhase;
    }

    public String getCurrentPlayerId() {
        return currentPlayerId;
    }

    public void setCurrentPlayerId(String currentPlayerId) {
        this.currentPlayerId = currentPlayerId;
    }

    public Set<String> getConnectedPlayers() {
        return connectedPlayers;
    }

    public void addPlayer(String playerId) {
        connectedPlayers.add(playerId);
        version++;
    }

    public void removePlayer(String playerId) {
        connectedPlayers.remove(playerId);
        version++;
    }

    public int getVersion() {
        return version;
    }

    public void incrementVersion() {
        version++;
    }
}
