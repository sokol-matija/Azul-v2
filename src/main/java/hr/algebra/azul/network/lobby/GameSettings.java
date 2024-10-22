package hr.algebra.azul.network.lobby;

import java.io.Serializable;

public class GameSettings implements Serializable {
    private static final long serialVersionUID = 1L;

    private int minPlayers = 2;
    private int maxPlayers = 4;
    private boolean allowSpectators = false;
    private int timePerTurn = 60; // seconds

    // Getters and setters
    public int getMinPlayers() { return minPlayers; }
    public void setMinPlayers(int minPlayers) { this.minPlayers = minPlayers; }
    public int getMaxPlayers() { return maxPlayers; }
    public void setMaxPlayers(int maxPlayers) { this.maxPlayers = maxPlayers; }
    public boolean isAllowSpectators() { return allowSpectators; }
    public void setAllowSpectators(boolean allowSpectators) { this.allowSpectators = allowSpectators; }
    public int getTimePerTurn() { return timePerTurn; }
    public void setTimePerTurn(int timePerTurn) { this.timePerTurn = timePerTurn; }
}
