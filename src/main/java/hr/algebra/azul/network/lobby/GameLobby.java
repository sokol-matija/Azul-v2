package hr.algebra.azul.network.lobby;

import hr.algebra.azul.network.GameState;
import java.io.Serializable;
import java.util.*;

public class GameLobby implements Serializable {
    private static final long serialVersionUID = 1L;

    private String lobbyId;
    private String hostId;
    private Map<String, PlayerInfo> players;
    private LobbyStatus status;
    private GameSettings settings;

    public GameLobby(String hostId) {
        this.lobbyId = UUID.randomUUID().toString();
        this.hostId = hostId;
        this.players = new HashMap<>();
        this.status = LobbyStatus.WAITING;
        this.settings = new GameSettings();

        // Add host as first player
        addPlayer(hostId, "Host");
    }

    public void addPlayer(String playerId, String playerName) {
        if (players.size() >= settings.getMaxPlayers()) {
            throw new IllegalStateException("Lobby is full");
        }
        players.put(playerId, new PlayerInfo(playerId, playerName));
    }

    public void removePlayer(String playerId) {
        players.remove(playerId);
        if (playerId.equals(hostId) && !players.isEmpty()) {
            // Transfer host if original host leaves
            hostId = players.keySet().iterator().next();
        }
    }

    public boolean isHost(String playerId) {
        return hostId.equals(playerId);
    }

    public boolean canStart() {
        return players.size() >= settings.getMinPlayers() &&
                players.size() <= settings.getMaxPlayers();
    }

    // Getters and setters
    public String getLobbyId() { return lobbyId; }
    public String getHostId() { return hostId; }
    public Map<String, PlayerInfo> getPlayers() { return Collections.unmodifiableMap(players); }
    public LobbyStatus getStatus() { return status; }
    public void setStatus(LobbyStatus status) { this.status = status; }
    public GameSettings getSettings() { return settings; }
}