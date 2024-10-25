package hr.algebra.azul.network.recovery;

import hr.algebra.azul.model.*;
import hr.algebra.azul.network.*;
import hr.algebra.azul.network.persistence.GameStatePersistenceManager;
import hr.algebra.azul.network.persistence.GameStatePersistenceManager.SaveTrigger;
import javafx.application.Platform;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

public class NetworkRecoveryManager {
    private static final Logger LOGGER = Logger.getLogger(NetworkRecoveryManager.class.getName());
    private static final int MAX_RECONNECTION_ATTEMPTS = 3;
    private static final int RECONNECTION_TIMEOUT_SECONDS = 30;
    private static final long RETRY_DELAY_MS = 2000;
    private static final int STATE_SYNC_BATCH_SIZE = 10;

    private final GameClient gameClient;
    private final String playerId;
    private final Map<String, PlayerConnectionState> connectionStates;
    private final ScheduledExecutorService recoveryExecutor;
    private final BlockingQueue<RecoveryTask> recoveryQueue;
    private final GameStatePersistenceManager persistenceManager;
    private final Map<String, ReconnectionAttempt> reconnectionAttempts;
    private final Object recoveryLock = new Object();
    private RecoveryHandler recoveryHandler;
    private volatile boolean isRecovering = false;

    public NetworkRecoveryManager(GameClient gameClient, String playerId,
                                  GameStatePersistenceManager persistenceManager) {
        this.gameClient = gameClient;
        this.playerId = playerId;
        this.persistenceManager = persistenceManager;
        this.connectionStates = new ConcurrentHashMap<>();
        this.recoveryExecutor = Executors.newScheduledThreadPool(2);
        this.recoveryQueue = new LinkedBlockingQueue<>();
        this.reconnectionAttempts = new ConcurrentHashMap<>();

        startRecoveryProcessor();
    }

    private void startRecoveryProcessor() {
        CompletableFuture.runAsync(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    RecoveryTask task = recoveryQueue.take();
                    processRecoveryTask(task);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    LOGGER.severe("Error processing recovery task: " + e.getMessage());
                }
            }
        }, recoveryExecutor);
    }

    public CompletableFuture<Boolean> handleDisconnection(String disconnectedPlayerId) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        synchronized (recoveryLock) {
            try {
                PlayerConnectionState state = connectionStates.computeIfAbsent(
                        disconnectedPlayerId,
                        id -> new PlayerConnectionState(id)
                );

                state.setDisconnected();
                recoveryQueue.offer(new DisconnectionTask(disconnectedPlayerId, future));

                // Schedule timeout for reconnection
                scheduleReconnectionTimeout(disconnectedPlayerId);

            } catch (Exception e) {
                LOGGER.severe("Error handling disconnection: " + e.getMessage());
                future.complete(false);
            }
        }

        return future;
    }

    private void scheduleReconnectionTimeout(String playerId) {
        recoveryExecutor.schedule(() -> {
            PlayerConnectionState state = connectionStates.get(playerId);
            if (state != null && state.isDisconnected()) {
                handleReconnectionTimeout(playerId);
            }
        }, RECONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    public CompletableFuture<Boolean> handleReconnection(String playerId) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        synchronized (recoveryLock) {
            try {
                PlayerConnectionState state = connectionStates.get(playerId);
                if (state == null || !state.isDisconnected()) {
                    future.complete(false);
                    return future;
                }

                ReconnectionAttempt attempt = reconnectionAttempts.computeIfAbsent(
                        playerId,
                        id -> new ReconnectionAttempt(id)
                );

                if (attempt.getAttempts() >= MAX_RECONNECTION_ATTEMPTS) {
                    handleReconnectionFailure(playerId, future);
                    return future;
                }

                attempt.incrementAttempts();
                recoveryQueue.offer(new ReconnectionTask(playerId, future));

            } catch (Exception e) {
                LOGGER.severe("Error handling reconnection: " + e.getMessage());
                future.complete(false);
            }
        }

        return future;
    }

    private void processRecoveryTask(RecoveryTask task) {
        try {
            if (task instanceof DisconnectionTask disconnection) {
                handleDisconnectionRecovery(disconnection);
            } else if (task instanceof ReconnectionTask reconnection) {
                handleReconnectionRecovery(reconnection);
            }
        } catch (Exception e) {
            LOGGER.severe("Error processing recovery task: " + e.getMessage());
            task.getFuture().complete(false);
        }
    }

    private void handleDisconnectionRecovery(DisconnectionTask task) {
        try {
            // Save current game state
            persistenceManager.saveGameState(SaveTrigger.DISCONNECTION)
                    .thenAccept(saved -> {
                        if (saved) {
                            notifyPlayersOfDisconnection(task.playerId());
                            task.getFuture().complete(true);
                        } else {
                            task.getFuture().complete(false);
                        }
                    });
        } catch (Exception e) {
            LOGGER.severe("Disconnection recovery failed: " + e.getMessage());
            task.getFuture().complete(false);
        }
    }

    private void handleReconnectionRecovery(ReconnectionTask task) {
        try {
            AtomicInteger attempts = new AtomicInteger(0);
            attemptReconnection(task, attempts);
        } catch (Exception e) {
            LOGGER.severe("Reconnection recovery failed: " + e.getMessage());
            task.getFuture().complete(false);
        }
    }

    private void attemptReconnection(ReconnectionTask task, AtomicInteger attempts) {
        if (attempts.get() >= MAX_RECONNECTION_ATTEMPTS) {
            handleReconnectionFailure(task.playerId(), task.getFuture());
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                // Load latest game state
                Optional<NetworkGameState> savedState =
                        persistenceManager.loadLatestState().get();

                if (savedState.isPresent()) {
                    synchronizeGameState(task.playerId(), savedState.get());
                    task.getFuture().complete(true);
                } else {
                    // Retry after delay
                    Thread.sleep(RETRY_DELAY_MS);
                    attempts.incrementAndGet();
                    attemptReconnection(task, attempts);
                }
            } catch (Exception e) {
                LOGGER.warning("Reconnection attempt " + attempts.get() +
                        " failed: " + e.getMessage());
                attempts.incrementAndGet();
                attemptReconnection(task, attempts);
            }
        });
    }

    private void synchronizeGameState(String playerId, NetworkGameState state) {
        GameMessage syncMessage = new GameMessage(
                MessageType.SYNC,
                "system",
                null,
                new GameState(state.getGame())
        );

        gameClient.sendGameMessage(syncMessage);
        notifyPlayersOfReconnection(playerId);

        PlayerConnectionState connectionState = connectionStates.get(playerId);
        if (connectionState != null) {
            connectionState.setConnected();
        }
    }

    private void handleReconnectionTimeout(String playerId) {
        PlayerConnectionState state = connectionStates.get(playerId);
        if (state != null) {
            state.setFailed();
            notifyPlayersOfPermanentDisconnection(playerId);
        }
    }

    private void handleReconnectionFailure(String playerId, CompletableFuture<Boolean> future) {
        PlayerConnectionState state = connectionStates.get(playerId);
        if (state != null) {
            state.setFailed();
        }

        notifyPlayersOfPermanentDisconnection(playerId);
        future.complete(false);
    }

    private void notifyPlayersOfDisconnection(String playerId) {
        GameMessage message = new GameMessage(
                MessageType.PLAYER_DISCONNECTED,
                playerId,
                null,
                null,
                "Player temporarily disconnected"
        );
        gameClient.sendGameMessage(message);
    }

    private void notifyPlayersOfReconnection(String playerId) {
        GameMessage message = new GameMessage(
                MessageType.PLAYER_RECONNECTED,
                playerId,
                null,
                null,
                "Player reconnected"
        );
        gameClient.sendGameMessage(message);
    }

    private void notifyPlayersOfPermanentDisconnection(String playerId) {
        GameMessage message = new GameMessage(
                MessageType.PLAYER_LEFT,
                playerId,
                null,
                null,
                "Player permanently disconnected"
        );
        gameClient.sendGameMessage(message);
    }

    public void setRecoveryHandler(RecoveryHandler handler) {
        this.recoveryHandler = handler;
    }

    public void cleanup() {
        recoveryExecutor.shutdownNow();
        connectionStates.clear();
        reconnectionAttempts.clear();
    }

    // Inner classes for state management
    private static class PlayerConnectionState {
        private final String playerId;
        private ConnectionStatus status;
        private long lastStatusChange;

        public PlayerConnectionState(String playerId) {
            this.playerId = playerId;
            this.status = ConnectionStatus.CONNECTED;
            this.lastStatusChange = System.currentTimeMillis();
        }

        public void setDisconnected() {
            this.status = ConnectionStatus.DISCONNECTED;
            this.lastStatusChange = System.currentTimeMillis();
        }

        public void setConnected() {
            this.status = ConnectionStatus.CONNECTED;
            this.lastStatusChange = System.currentTimeMillis();
        }

        public void setFailed() {
            this.status = ConnectionStatus.FAILED;
            this.lastStatusChange = System.currentTimeMillis();
        }

        public boolean isDisconnected() {
            return status == ConnectionStatus.DISCONNECTED;
        }
    }

    private static class ReconnectionAttempt {
        private final String playerId;
        private int attempts;
        private final long firstAttemptTime;

        public ReconnectionAttempt(String playerId) {
            this.playerId = playerId;
            this.attempts = 0;
            this.firstAttemptTime = System.currentTimeMillis();
        }

        public void incrementAttempts() {
            this.attempts++;
        }

        public int getAttempts() {
            return attempts;
        }

        public boolean hasTimedOut() {
            return System.currentTimeMillis() - firstAttemptTime >
                    RECONNECTION_TIMEOUT_SECONDS * 1000L;
        }
    }

    private enum ConnectionStatus {
        CONNECTED,
        DISCONNECTED,
        FAILED
    }

    // Task definitions
    private interface RecoveryTask {
        CompletableFuture<Boolean> getFuture();
    }

    private record DisconnectionTask(
            String playerId,
            CompletableFuture<Boolean> future
    ) implements RecoveryTask {
        @Override
        public CompletableFuture<Boolean> getFuture() {
            return future;
        }
    }

    private record ReconnectionTask(
            String playerId,
            CompletableFuture<Boolean> future
    ) implements RecoveryTask {
        @Override
        public CompletableFuture<Boolean> getFuture() {
            return future;
        }
    }

    public interface RecoveryHandler {
        void onPlayerDisconnected(String playerId);
        void onPlayerReconnected(String playerId);
        void onPlayerPermanentlyDisconnected(String playerId);
        void onRecoveryError(String message);
    }
}
