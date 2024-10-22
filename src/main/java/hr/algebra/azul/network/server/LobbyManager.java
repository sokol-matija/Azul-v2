package hr.algebra.azul.network.server;

import hr.algebra.azul.model.Game;
import hr.algebra.azul.network.GameState;
import hr.algebra.azul.network.lobby.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class LobbyManager {
    private static final Logger LOGGER = Logger.getLogger(LobbyManager.class.getName());

    private final Map<String, GameLobby> activeLobbies;
    private final Map<String, Set<ClientHandler>> lobbyClients;

    public LobbyManager() {
        this.activeLobbies = new ConcurrentHashMap<>();
        this.lobbyClients = new ConcurrentHashMap<>();
    }

    public GameLobby createLobby(String hostId) {
        GameLobby lobby = new GameLobby(hostId);
        activeLobbies.put(lobby.getLobbyId(), lobby);
        lobbyClients.put(lobby.getLobbyId(), Collections.newSetFromMap(new ConcurrentHashMap<>()));
        return lobby;
    }

    public void addPlayerToLobby(String lobbyId, ClientHandler client, String playerName) {
        GameLobby lobby = activeLobbies.get(lobbyId);
        if (lobby != null) {
            lobby.addPlayer(client.getPlayerId(), playerName);
            lobbyClients.get(lobbyId).add(client);
            broadcastLobbyUpdate(lobby);
        }
    }

    public void removePlayerFromLobby(String lobbyId, String playerId) {
        GameLobby lobby = activeLobbies.get(lobbyId);
        if (lobby != null) {
            lobby.removePlayer(playerId);
            lobbyClients.get(lobbyId).removeIf(client ->
                    client.getPlayerId().equals(playerId));

            if (lobby.getPlayers().isEmpty()) {
                closeLobby(lobbyId);
            } else {
                broadcastLobbyUpdate(lobby);
            }
        }
    }

    public void startGame(String lobbyId) {
        GameLobby lobby = activeLobbies.get(lobbyId);
        if (lobby != null && lobby.canStart()) {
            lobby.setStatus(LobbyStatus.STARTING);
            broadcastLobbyUpdate(lobby);
            // Initialize and start the game
            initializeGame(lobby);
        }
    }

    private void initializeGame(GameLobby lobby) {
        // Create new game instance with lobby players
        GameState gameState = new GameState(
                new Game(lobby.getPlayers().size())
        );

        // Assign players to game
        List<String> playerIds = new ArrayList<>(lobby.getPlayers().keySet());
        for (int i = 0; i < playerIds.size(); i++) {
            String playerId = playerIds.get(i);
            PlayerInfo playerInfo = lobby.getPlayers().get(playerId);
            gameState.addPlayer(playerId);
        }

        // Broadcast game start
        broadcastGameStart(lobby, gameState);
    }

    private void broadcastLobbyUpdate(GameLobby lobby) {
        LobbyMessage message = new LobbyMessage(
                LobbyMessageType.LOBBY_UPDATE,
                lobby
        );

        Set<ClientHandler> clients = lobbyClients.get(lobby.getLobbyId());
        if (clients != null) {
            for (ClientHandler client : clients) {
                //client.sendMessage(message);
            }
        }
    }

    private void broadcastGameStart(GameLobby lobby, GameState gameState) {
        LobbyMessage message = new LobbyMessage(
                LobbyMessageType.GAME_START,
                lobby,
                gameState
        );

        Set<ClientHandler> clients = lobbyClients.get(lobby.getLobbyId());
        if (clients != null) {
            for (ClientHandler client : clients) {
                //client.sendMessage(message);
            }
        }
    }

    public void closeLobby(String lobbyId) {
        activeLobbies.remove(lobbyId);
        lobbyClients.remove(lobbyId);
    }

    public List<GameLobby> getActiveLobbies() {
        return new ArrayList<>(activeLobbies.values());
    }
}