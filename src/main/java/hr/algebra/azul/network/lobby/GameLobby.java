package hr.algebra.azul.network.lobby;

import hr.algebra.azul.model.User;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GameLobby implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String lobbyId;
    private final Map<String, User> players;
    private LobbyStatus status;
    private GameSettings settings;
    private User host;

    public GameLobby(User host) {
        this.lobbyId = UUID.randomUUID().toString();
        this.players = new ConcurrentHashMap<>();
        this.status = LobbyStatus.WAITING;
        this.settings = new GameSettings();
        this.host = host;

        // Add host and set them as ready
        host.setReady(true);
        addPlayer(host);
    }

    public void addPlayer(User user) {
        if (players.size() >= settings.getMaxPlayers()) {
            throw new IllegalStateException("Lobby is full");
        }
        players.put(user.getId(), user);
    }

    public void removePlayer(String userId) {
        players.remove(userId);

        // If host leaves, assign new host
        if (isHost(userId) && !players.isEmpty()) {
            // Get first remaining player as new host
            host = players.values().iterator().next();
            host.setReady(true);
        }
    }

    public boolean isHost(String userId) {
        return host.getId().equals(userId);
    }

    public User getHost() {
        return host;
    }

    public void setHost(User newHost) {
        if (!players.containsKey(newHost.getId())) {
            throw new IllegalArgumentException("New host must be a player in the lobby");
        }
        this.host = newHost;
        newHost.setReady(true);
    }

    public boolean canStart() {
        return players.size() >= settings.getMinPlayers() &&
                players.size() <= settings.getMaxPlayers() &&
                players.values().stream().allMatch(User::isReady);
    }

    public boolean updateSettings(GameSettings newSettings) {
        if (newSettings.getMaxPlayers() < players.size() ||
                newSettings.getMinPlayers() < 2 ||
                newSettings.getMaxPlayers() > 4) {
            return false;
        }
        this.settings = newSettings;
        return true;
    }

    public void resetReadyStatus() {
        players.values().forEach(user -> {
            user.setReady(isHost(user.getId()));
        });
    }

    // Getters
    public String getLobbyId() { return lobbyId; }
    public Map<String, User> getPlayers() { return players; }
    public LobbyStatus getStatus() { return status; }
    public void setStatus(LobbyStatus status) { this.status = status; }
    public GameSettings getSettings() { return settings; }

    @Override
    public String toString() {
        return "GameLobby{" +
                "lobbyId='" + lobbyId + '\'' +
                ", host=" + host.getUsername() +
                ", players=" + players.size() +
                ", status=" + status +
                '}';
    }
}