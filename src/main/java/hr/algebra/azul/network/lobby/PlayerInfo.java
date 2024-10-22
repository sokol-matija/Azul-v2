package hr.algebra.azul.network.lobby;

import java.io.Serializable;

public class PlayerInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    private String playerId;
    private String playerName;
    private boolean ready;

    public PlayerInfo(String playerId, String playerName) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.ready = false;
    }

    // Getters and setters
    public String getPlayerId() { return playerId; }
    public String getPlayerName() { return playerName; }
    public boolean isReady() { return ready; }
    public void setReady(boolean ready) { this.ready = ready; }
}