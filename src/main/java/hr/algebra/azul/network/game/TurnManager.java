        package hr.algebra.azul.network.game;

import hr.algebra.azul.model.*;
import hr.algebra.azul.network.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;
import javafx.application.Platform;

public class TurnManager {
    private static final Logger LOGGER = Logger.getLogger(TurnManager.class.getName());
    private static final int TURN_TIMEOUT_SECONDS = 60;
    private static final int MAX_SKIPPED_TURNS = 2;
    private static final int TURN_WARNING_SECONDS = 10;

    private final String gameId;
    private final GameClient gameClient;
    private final Map<String, PlayerTurnState> playerTurnStates;
    private final ScheduledExecutorService turnExecutor;
    private final Object turnLock = new Object();
    private volatile String currentPlayerId;
    private TurnUpdateHandler turnHandler;
    private ScheduledFuture<?> turnTimeout;
    private ScheduledFuture<?> turnWarning;

    public TurnManager(String gameId, GameClient gameClient) {
        this.gameId = gameId;
        this.gameClient = gameClient;
        this.playerTurnStates = new ConcurrentHashMap<>();
        this.turnExecutor = Executors.newSingleThreadScheduledExecutor();
    }

    public void startTurn(String playerId) {
        synchronized (turnLock) {
            currentPlayerId = playerId;
            PlayerTurnState state = playerTurnStates.computeIfAbsent(
                    playerId,
                    id -> new PlayerTurnState(id)
            );

            state.startTurn();
            scheduleTurnTimeout(playerId);
            scheduleTurnWarning(playerId);
            notifyTurnStart(playerId);
        }
    }

    private void scheduleTurnTimeout(String playerId) {
        cancelCurrentTimeout();
        turnTimeout = turnExecutor.schedule(
                () -> handleTurnTimeout(playerId),
                TURN_TIMEOUT_SECONDS,
                TimeUnit.SECONDS
        );
    }

    private void scheduleTurnWarning(String playerId) {
        cancelCurrentWarning();
        turnWarning = turnExecutor.schedule(
                () -> notifyTurnWarning(playerId),
                TURN_TIMEOUT_SECONDS - TURN_WARNING_SECONDS,
                TimeUnit.SECONDS
        );
    }

    public void handlePlayerDisconnection(String playerId) {
        synchronized (turnLock) {
            PlayerTurnState state = playerTurnStates.get(playerId);
            if (state != null) {
                state.setDisconnected(true);

                if (isCurrentPlayer(playerId)) {
                    handleCurrentPlayerDisconnection(playerId);
                }
            }
        }
    }

    public void handlePlayerReconnection(String playerId) {
        synchronized (turnLock) {
            PlayerTurnState state = playerTurnStates.get(playerId);
            if (state != null) {
                state.setDisconnected(false);
                state.resetSkippedTurns();

                if (isCurrentPlayer(playerId)) {
                    resumeTurn(playerId);
                }
            }
        }
    }

    private void handleCurrentPlayerDisconnection(String playerId) {
        PlayerTurnState state = playerTurnStates.get(playerId);
        if (state != null && state.getTurnStartTime() != null) {
            long turnDuration = System.currentTimeMillis() -
                    state.getTurnStartTime().getTime();

            if (turnDuration < TURN_TIMEOUT_SECONDS * 1000L / 2) {
                scheduleDisconnectionTimeout(playerId);
            } else {
                skipTurn(playerId);
            }
        }
    }

    private void scheduleDisconnectionTimeout(String playerId) {
        turnTimeout = turnExecutor.schedule(
                () -> skipTurn(playerId),
                TURN_TIMEOUT_SECONDS / 2,
                TimeUnit.SECONDS
        );
    }

    private void skipTurn(String playerId) {
        PlayerTurnState state = playerTurnStates.get(playerId);
        if (state != null) {
            state.incrementSkippedTurns();

            if (state.getSkippedTurns() >= MAX_SKIPPED_TURNS) {
                handlePlayerRemoval(playerId);
            } else {
                advanceToNextTurn();
            }
        }
    }

    private void handlePlayerRemoval(String playerId) {
        playerTurnStates.remove(playerId);
        notifyPlayerRemoved(playerId);
        advanceToNextTurn();
    }

    private void resumeTurn(String playerId) {
        cancelCurrentTimeout();

        PlayerTurnState state = playerTurnStates.get(playerId);
        if (state != null) {
            state.resetTurnTimer();
            scheduleTurnTimeout(playerId);
            scheduleTurnWarning(playerId);
            notifyTurnResumed(playerId);
        }
    }

    public void endTurn(String playerId) {
        synchronized (turnLock) {
            if (isCurrentPlayer(playerId)) {
                PlayerTurnState state = playerTurnStates.get(playerId);
                if (state != null) {
                    state.endTurn();
                    cancelCurrentTimeout();
                    cancelCurrentWarning();
                    advanceToNextTurn();
                }
            }
        }
    }

    private void handleTurnTimeout(String playerId) {
        synchronized (turnLock) {
            if (isCurrentPlayer(playerId)) {
                skipTurn(playerId);
            }
        }
    }

    private void advanceToNextTurn() {
        String nextPlayerId = getNextValidPlayer();
        if (nextPlayerId != null) {
            startTurn(nextPlayerId);
        }
    }

    private String getNextValidPlayer() {
        List<String> activePlayers = playerTurnStates.entrySet().stream()
                .filter(e -> !e.getValue().isDisconnected())
                .map(Map.Entry::getKey)
                .toList();

        if (activePlayers.isEmpty()) {
            return null;
        }

        int currentIndex = activePlayers.indexOf(currentPlayerId);
        int nextIndex = (currentIndex + 1) % activePlayers.size();
        return activePlayers.get(nextIndex);
    }

    private boolean isCurrentPlayer(String playerId) {
        return playerId.equals(currentPlayerId);
    }

    private void notifyTurnStart(String playerId) {
        if (turnHandler != null) {
            Platform.runLater(() ->
                    turnHandler.onTurnStarted(playerId));
        }

        GameMessage message = new GameMessage(
                MessageType.TURN_START,
                playerId,
                null,
                null
        );
        gameClient.sendGameMessage(message);
    }

    private void notifyTurnWarning(String playerId) {
        if (turnHandler != null) {
            Platform.runLater(() ->
                    turnHandler.onTurnWarning(playerId));
        }

        GameMessage message = new GameMessage(
                MessageType.TURN_WARNING,
                playerId,
                null,
                null,
                "Turn ending in " + TURN_WARNING_SECONDS + " seconds"
        );
        gameClient.sendGameMessage(message);
    }

    private void notifyTurnResumed(String playerId) {
        if (turnHandler != null) {
            Platform.runLater(() ->
                    turnHandler.onTurnResumed(playerId));
        }

        GameMessage message = new GameMessage(
                MessageType.TURN_RESUMED,
                playerId,
                null,
                null
        );
        gameClient.sendGameMessage(message);
    }

    private void notifyPlayerRemoved(String playerId) {
        if (turnHandler != null) {
            Platform.runLater(() ->
                    turnHandler.onPlayerRemoved(playerId));
        }

        GameMessage message = new GameMessage(
                MessageType.PLAYER_REMOVED,
                playerId,
                null,
                null,
                "Player removed due to inactivity"
        );
        gameClient.sendGameMessage(message);
    }

    private void cancelCurrentTimeout() {
        if (turnTimeout != null && !turnTimeout.isDone()) {
            turnTimeout.cancel(false);
        }
    }

    private void cancelCurrentWarning() {
        if (turnWarning != null && !turnWarning.isDone()) {
            turnWarning.cancel(false);
        }
    }

    public void setTurnHandler(TurnUpdateHandler handler) {
        this.turnHandler = handler;
    }

    public void cleanup() {
        turnExecutor.shutdownNow();
        playerTurnStates.clear();
        cancelCurrentTimeout();
        cancelCurrentWarning();
    }

    private static class PlayerTurnState {
        private final String playerId;
        private Date turnStartTime;
        private boolean disconnected;
        private int skippedTurns;

        public PlayerTurnState(String playerId) {
            this.playerId = playerId;
            this.disconnected = false;
            this.skippedTurns = 0;
        }

        public void startTurn() {
            this.turnStartTime = new Date();
        }

        public void endTurn() {
            this.turnStartTime = null;
        }

        public void resetTurnTimer() {
            if (turnStartTime != null) {
                this.turnStartTime = new Date();
            }
        }

        public Date getTurnStartTime() {
            return turnStartTime;
        }

        public void setDisconnected(boolean disconnected) {
            this.disconnected = disconnected;
        }

        public boolean isDisconnected() {
            return disconnected;
        }

        public void incrementSkippedTurns() {
            this.skippedTurns++;
        }

        public void resetSkippedTurns() {
            this.skippedTurns = 0;
        }

        public int getSkippedTurns() {
            return skippedTurns;
        }
    }

    public interface TurnUpdateHandler {
        void onTurnStarted(String playerId);
        void onTurnWarning(String playerId);
        void onTurnResumed(String playerId);
        void onPlayerRemoved(String playerId);
        void onTurnError(String message);
    }
}
