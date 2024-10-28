package hr.algebra.azul.network.server;

import hr.algebra.azul.model.Game;
import hr.algebra.azul.model.User;
import hr.algebra.azul.model.UserStatus;
import hr.algebra.azul.network.GameServer;
import hr.algebra.azul.network.GameState;
import hr.algebra.azul.network.lobby.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LobbyManager {
    private static final Logger LOGGER = Logger.getLogger(LobbyManager.class.getName());
    private static final int LOBBY_TIMEOUT_MINUTES = 30;
    private static final int MAX_PLAYERS_PER_LOBBY = 4;
    private static final int MIN_PLAYERS_PER_LOBBY = 2;

    private final Object lobbyLock = new Object();
    private final Map<String, GameLobby> activeLobbies;
    private final Map<String, Set<ClientHandler>> lobbyClients;
    private final Map<String, ScheduledFuture<?>> lobbyTimeouts;
    private final ScheduledExecutorService timeoutExecutor;
    private final GameServer gameServer;

    public LobbyManager(GameServer gameServer) {
        this.gameServer = gameServer;
        this.activeLobbies = new ConcurrentHashMap<>();
        this.lobbyClients = new ConcurrentHashMap<>();
        this.lobbyTimeouts = new ConcurrentHashMap<>();
        this.timeoutExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r);
            thread.setDaemon(true);
            return thread;
        });
    }

    public LobbyManager(GameServer gameServer, ScheduledExecutorService timeoutExecutor) {
        this.gameServer = gameServer;
        this.activeLobbies = new ConcurrentHashMap<>();
        this.lobbyClients = new ConcurrentHashMap<>();
        this.lobbyTimeouts = new ConcurrentHashMap<>();
        this.timeoutExecutor = timeoutExecutor;
    }

    public GameLobby createLobby(User host) {
        synchronized(lobbyLock) {
            // Check if host is already in another lobby
            for (GameLobby existingLobby : activeLobbies.values()) {
                if (existingLobby.getPlayers().containsKey(host.getId())) {
                    throw new IllegalStateException("Player is already in another lobby");
                }
            }

            GameLobby lobby = new GameLobby(host);
            activeLobbies.put(lobby.getLobbyId(), lobby);
            lobbyClients.put(lobby.getLobbyId(), Collections.newSetFromMap(new ConcurrentHashMap<>()));

            // Schedule lobby timeout
            scheduleLobbyTimeout(lobby.getLobbyId());

            LOGGER.info("Created new lobby: " + lobby.getLobbyId() + " with host: " + host.getUsername());
            return lobby;
        }
    }

    private void scheduleLobbyTimeout(String lobbyId) {
        ScheduledFuture<?> timeoutTask = timeoutExecutor.schedule(() -> {
            if (activeLobbies.containsKey(lobbyId)) {
                GameLobby lobby = activeLobbies.get(lobbyId);
                if (lobby.getStatus() == LobbyStatus.WAITING) {
                    LOGGER.info("Closing inactive lobby: " + lobbyId);
                    closeLobby(lobbyId);
                }
            }
        }, LOBBY_TIMEOUT_MINUTES, TimeUnit.MINUTES);

        lobbyTimeouts.put(lobbyId, timeoutTask);
    }

    public void broadcastToLobby(String lobbyId, Object message) {
        Set<ClientHandler> clients = lobbyClients.get(lobbyId);
        if (clients != null) {
            for (ClientHandler client : clients) {
                try {
                    client.sendMessage(message);
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Failed to send message to client: " + client.user.getId(), e);
                    handleClientDisconnection(client);
                }
            }
        }
    }

    public synchronized void addPlayerToLobby(String lobbyId, ClientHandler client, User user) {
        synchronized(lobbyLock) {
            GameLobby lobby = activeLobbies.get(lobbyId);

            // Debug logging
            LOGGER.info("Attempting to add player to lobby: " + lobbyId);
            LOGGER.info("Active lobbies: " + activeLobbies.keySet());

            if (lobby == null) {
                LOGGER.severe("Lobby " + lobbyId + " not found in active lobbies");
                throw new IllegalStateException("Lobby does not exist");
            }

            // Check for duplicate players
            if (lobby.getPlayers().containsKey(user.getId())) {
                LOGGER.warning("Player " + user.getUsername() + " is already in lobby " + lobbyId);
                return;
            }

            // Add player to lobby
            lobby.addPlayer(user);

            // Add client handler to lobby clients
            Set<ClientHandler> clientSet = lobbyClients.computeIfAbsent(lobbyId,
                    k -> Collections.newSetFromMap(new ConcurrentHashMap<>()));
            clientSet.add(client);

            // Reset timeout and update everyone
            resetLobbyTimeout(lobbyId);
            broadcastLobbyUpdate(lobby);

            LOGGER.info("Successfully added player " + user.getUsername() + " to lobby " + lobbyId);
        }
    }


    private void resetLobbyTimeout(String lobbyId) {
        ScheduledFuture<?> existingTimeout = lobbyTimeouts.remove(lobbyId);
        if (existingTimeout != null) {
            existingTimeout.cancel(false);
        }
        scheduleLobbyTimeout(lobbyId);
    }

    private void removePlayerFromAllLobbies(String playerId) {
        for (Map.Entry<String, GameLobby> entry : activeLobbies.entrySet()) {
            if (entry.getValue().getPlayers().containsKey(playerId)) {
                removePlayerFromLobby(entry.getKey(), playerId);
                break;
            }
        }
    }

    public void removePlayerFromLobby(String lobbyId, String userId) {
        synchronized(lobbyLock) {
            GameLobby lobby = activeLobbies.get(lobbyId);
            if (lobby != null) {
                User player = lobby.getPlayers().get(userId);
                if (player != null) {
                    lobby.removePlayer(userId);
//                    lobbyClients.get(lobbyId).removeIf(client ->
//                            client.getUser() != null &&
//                                    client.getUser().getId().equals(userId));

                    if (lobby.getPlayers().isEmpty()) {
                        closeLobby(lobbyId);
                    } else if (lobby.getHost().getId().equals(userId)) {
                        handleHostDisconnection(lobby);
                    } else {
                        broadcastLobbyUpdate(lobby);
                    }

                    LOGGER.info("Player " + player.getUsername() + " left lobby: " + lobbyId);
                }
            }
        }
    }

    private void handleHostDisconnection(GameLobby lobby) {
        if (!lobby.getPlayers().isEmpty()) {
            // Get next player as new host
            User newHost = lobby.getPlayers().values().iterator().next();
            lobby.setHost(newHost);
            newHost.setReady(true); // Host is always ready

            // Notify clients about host change
            LobbyMessage hostChangeMessage = new LobbyMessage(
                    LobbyMessageType.HOST_CHANGED,
                    lobby
            );
            broadcastToLobby(lobby.getLobbyId(), hostChangeMessage);

            LOGGER.info("New host assigned in lobby " + lobby.getLobbyId() + ": " + newHost.getUsername());
        }
    }

    public void updatePlayerReadyState(String lobbyId, User user, boolean ready) {
        synchronized(lobbyLock) {
            GameLobby lobby = activeLobbies.get(lobbyId);
            if (lobby != null && lobby.getPlayers().containsKey(user.getId())) {
                user.setReady(ready);
                broadcastLobbyUpdate(lobby);

                LOGGER.info("Player " + user.getUsername() + " ready state changed to: " + ready);

                // Check if game can start (all players ready)
                if (lobby.canStart()) {
                    LOGGER.info("All players ready in lobby " + lobbyId);
                }
            }
        }
    }

    public void startGame(String lobbyId) {
        synchronized(lobbyLock) {
            GameLobby lobby = activeLobbies.get(lobbyId);
            if (lobby != null && validateGameStart(lobby)) {
                lobby.setStatus(LobbyStatus.STARTING);

                // Cancel timeout for this lobby
                ScheduledFuture<?> timeoutTask = lobbyTimeouts.remove(lobbyId);
                if (timeoutTask != null) {
                    timeoutTask.cancel(false);
                }

                initializeGame(lobby);
                LOGGER.info("Game started in lobby: " + lobbyId);
            }
        }
    }

    private boolean validateGameStart(GameLobby lobby) {
        if (lobby.getPlayers().size() < MIN_PLAYERS_PER_LOBBY) {
            throw new IllegalStateException("Not enough players to start");
        }
        if (!lobby.canStart()) {
            throw new IllegalStateException("Not all players are ready");
        }
        return true;
    }

    private void initializeGame(GameLobby lobby) {
        try {
            Game game = new Game(lobby.getPlayers().size());
            game.startGame();
            GameState gameState = new GameState(game);

            // Add players to game state
            for (Map.Entry<String, User> entry : lobby.getPlayers().entrySet()) {
                gameState.addPlayer(entry.getKey());
            }

            broadcastGameStart(lobby, gameState);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize game for lobby: " + lobby.getLobbyId(), e);
            handleGameStartFailure(lobby);
        }
    }

    private void handleGameStartFailure(GameLobby lobby) {
        lobby.setStatus(LobbyStatus.WAITING);
        LobbyMessage errorMessage = new LobbyMessage(
                LobbyMessageType.ERROR,
                lobby,
                "Failed to start game. Please try again."
        );
        broadcastToLobby(lobby.getLobbyId(), errorMessage);
    }

    public void updateLobby(GameLobby updatedLobby) {
        synchronized(lobbyLock) {
            if (updatedLobby == null) {
                throw new IllegalArgumentException("Updated lobby cannot be null");
            }

            String lobbyId = updatedLobby.getLobbyId();
            LOGGER.info("Updating lobby: " + lobbyId + " with " +
                    updatedLobby.getPlayers().size() + " players");

            // Store or update the lobby
            activeLobbies.put(lobbyId, updatedLobby);

            // Ensure we have a client set for this lobby
            lobbyClients.putIfAbsent(lobbyId,
                    Collections.newSetFromMap(new ConcurrentHashMap<>()));

            // Schedule/reset timeout for this lobby
            resetLobbyTimeout(lobbyId);
        }
    }

    public void closeLobby(String lobbyId) {
        synchronized(lobbyLock) {
            GameLobby lobby = activeLobbies.remove(lobbyId);
            if (lobby != null) {
                // Cancel timeout
                ScheduledFuture<?> timeoutTask = lobbyTimeouts.remove(lobbyId);
                if (timeoutTask != null) {
                    timeoutTask.cancel(false);
                }

                // Notify clients
                LobbyMessage closeMessage = new LobbyMessage(
                        LobbyMessageType.LOBBY_CLOSED,
                        lobby
                );

                Set<ClientHandler> clients = lobbyClients.remove(lobbyId);
                if (clients != null) {
                    for (ClientHandler client : clients) {
                        client.sendMessage(closeMessage);
                    }
                }

                LOGGER.info("Lobby closed: " + lobbyId);
            }
        }
    }

    private void handleClientDisconnection(ClientHandler client) {
        String playerId = client.user.getId();
        for (Map.Entry<String, GameLobby> entry : activeLobbies.entrySet()) {
            if (entry.getValue().getPlayers().containsKey(playerId)) {
                removePlayerFromLobby(entry.getKey(), playerId);
                break;
            }
        }
    }

    public void broadcastLobbyUpdate(GameLobby lobby) {
        if (lobby == null) return;

        LobbyMessage updateMessage = new LobbyMessage(
                LobbyMessageType.LOBBY_UPDATE,
                lobby
        );

        LOGGER.info("Broadcasting lobby update for lobby: " + lobby.getLobbyId() +
                " to " + gameServer.getClients().size() + " clients");

        for (ClientHandler client : gameServer.getClients()) {
            try {
                client.sendMessage(updateMessage);
            } catch (Exception e) {
                LOGGER.warning("Failed to send lobby update to client: " + e.getMessage());
            }
        }
    }

    private void broadcastGameStart(GameLobby lobby, GameState gameState) {
        LobbyMessage message = new LobbyMessage(
                LobbyMessageType.GAME_START,
                lobby,
                gameState
        );
        broadcastToLobby(lobby.getLobbyId(), message);
    }

    public List<GameLobby> getActiveLobbies() {
        return new ArrayList<>(activeLobbies.values());
    }

    public GameLobby getLobby(String lobbyId) {
        return activeLobbies.get(lobbyId);
    }

    private void sendErrorMessage(ClientHandler client, String errorMessage) {
        LobbyMessage errorMsg = new LobbyMessage(
                LobbyMessageType.ERROR,
                client.getCurrentLobby(),
                errorMessage
        );
        client.sendMessage(errorMsg);
    }

    public void shutdown() {
        // Cancel all timeouts
        for (ScheduledFuture<?> timeout : lobbyTimeouts.values()) {
            timeout.cancel(true);
        }
        lobbyTimeouts.clear();

        // Close all lobbies
        for (String lobbyId : new ArrayList<>(activeLobbies.keySet())) {
            closeLobby(lobbyId);
        }

        // Shutdown the executor if we created it
        if (timeoutExecutor != null) {
            timeoutExecutor.shutdown();
            try {
                if (!timeoutExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    timeoutExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                timeoutExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}