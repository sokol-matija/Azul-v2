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
    private final Map<String, GameState> activeGames;

    public LobbyManager() {
        this.activeLobbies = new ConcurrentHashMap<>();
        this.lobbyClients = new ConcurrentHashMap<>();
        this.activeGames = new ConcurrentHashMap<>();
    }

    public synchronized GameLobby createLobby(String hostId, String hostName) {
        GameLobby lobby = new GameLobby(hostId);
        lobby.addPlayer(hostId, hostName);
        activeLobbies.put(lobby.getLobbyId(), lobby);
        lobbyClients.put(lobby.getLobbyId(), Collections.newSetFromMap(new ConcurrentHashMap<>()));

        // Broadcast lobby creation to all connected clients
        broadcastLobbyListUpdate();
        return lobby;
    }

    public synchronized void addPlayerToLobby(String lobbyId, ClientHandler client, String playerName) {
        GameLobby lobby = activeLobbies.get(lobbyId);
        if (lobby != null && !lobby.isFull()) {
            lobby.addPlayer(client.getPlayerId(), playerName);
            lobbyClients.get(lobbyId).add(client);

            // Broadcast updated lobby state
            broadcastLobbyUpdate(lobby);
            broadcastLobbyListUpdate();
        }
    }

    public synchronized void removePlayerFromLobby(String lobbyId, String playerId) {
        GameLobby lobby = activeLobbies.get(lobbyId);
        if (lobby != null) {
            lobby.removePlayer(playerId);
            lobbyClients.get(lobbyId).removeIf(client ->
                    client.getPlayerId().equals(playerId));

            if (lobby.isEmpty()) {
                closeLobby(lobbyId);
            } else {
                broadcastLobbyUpdate(lobby);
            }
            broadcastLobbyListUpdate();
        }
    }

    public synchronized void updatePlayerReadyStatus(String lobbyId, String playerId, boolean ready) {
        GameLobby lobby = activeLobbies.get(lobbyId);
        if (lobby != null) {
            PlayerInfo player = lobby.getPlayers().get(playerId);
            if (player != null) {
                player.setReady(ready);
                broadcastLobbyUpdate(lobby);
            }
        }
    }

    private void broadcastLobbyUpdate(GameLobby lobby) {
        LobbyMessage updateMessage = new LobbyMessage(
                LobbyMessageType.LOBBY_UPDATE,
                lobby
        );

        Set<ClientHandler> clients = lobbyClients.get(lobby.getLobbyId());
        if (clients != null) {
            for (ClientHandler client : clients) {
                client.sendMessage(updateMessage);
            }
        }
    }

    private void broadcastLobbyListUpdate() {
        // Create a message containing all active lobbies
        List<GameLobby> lobbies = new ArrayList<>(activeLobbies.values());
        for (Map.Entry<String, Set<ClientHandler>> entry : lobbyClients.entrySet()) {
            for (ClientHandler client : entry.getValue()) {
                LobbyMessage message = new LobbyMessage(
                        LobbyMessageType.LOBBY_LIST_UPDATE,
                        lobbies
                );
                client.sendMessage(message);
            }
        }
    }

    public synchronized void startGame(String lobbyId) {
        GameLobby lobby = activeLobbies.get(lobbyId);
        if (lobby != null && lobby.canStart()) {
            // Create new game instance
            Game game = new Game(lobby.getPlayers().size());
            GameState gameState = new GameState(game);

            // Set up initial game state
            for (PlayerInfo player : lobby.getPlayers().values()) {
                gameState.addPlayer(player.getPlayerId());
            }

            // Store game state
            activeGames.put(lobbyId, gameState);

            // Broadcast game start to all players in lobby
            broadcastGameStart(lobby, gameState);

            // Update lobby status
            lobby.setStatus(LobbyStatus.IN_PROGRESS);
            broadcastLobbyUpdate(lobby);
        }
    }

    private void broadcastGameStart(GameLobby lobby, GameState gameState) {
        LobbyMessage startMessage = new LobbyMessage(
                LobbyMessageType.GAME_START,
                lobby,
                gameState
        );

        Set<ClientHandler> clients = lobbyClients.get(lobby.getLobbyId());
        if (clients != null) {
            for (ClientHandler client : clients) {
                client.sendMessage(startMessage);
            }
        }
    }

    public synchronized void closeLobby(String lobbyId) {
        GameLobby lobby = activeLobbies.remove(lobbyId);
        if (lobby != null) {
            Set<ClientHandler> clients = lobbyClients.remove(lobbyId);
            if (clients != null) {
                LobbyMessage closeMessage = new LobbyMessage(
                        LobbyMessageType.LOBBY_CLOSED,
                        lobby
                );
                for (ClientHandler client : clients) {
                    client.sendMessage(closeMessage);
                }
            }
            broadcastLobbyListUpdate();
        }
    }

    public synchronized List<GameLobby> getActiveLobbies() {
        return new ArrayList<>(activeLobbies.values());
    }

    public synchronized GameState getGameState(String lobbyId) {
        return activeGames.get(lobbyId);
    }

    public synchronized boolean isLobbyHost(String lobbyId, String playerId) {
        GameLobby lobby = activeLobbies.get(lobbyId);
        return lobby != null && lobby.isHost(playerId);
    }
}