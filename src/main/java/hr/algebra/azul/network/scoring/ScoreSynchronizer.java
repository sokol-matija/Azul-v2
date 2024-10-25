package hr.algebra.azul.network.scoring;

import hr.algebra.azul.model.*;
import hr.algebra.azul.network.*;
import javafx.application.Platform;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

public class ScoreSynchronizer {
    private static final Logger LOGGER = Logger.getLogger(ScoreSynchronizer.class.getName());
    private static final int SYNC_INTERVAL_MS = 5000;
    private static final int MAX_SCORE_DISCREPANCY = 5;
    private static final int MAX_SYNC_RETRIES = 3;

    private final String gameId;
    private final GameClient gameClient;
    private final Map<String, PlayerScoreState> scoreStates;
    private final ScheduledExecutorService syncExecutor;
    private final Queue<ScoreUpdate> pendingUpdates;
    private final Queue<ScoreSync> syncHistory;
    private ScoreUpdateHandler updateHandler;
    private volatile boolean isRunning;

    public ScoreSynchronizer(String gameId, GameClient gameClient) {
        this.gameId = gameId;
        this.gameClient = gameClient;
        this.scoreStates = new ConcurrentHashMap<>();
        this.pendingUpdates = new ConcurrentLinkedQueue<>();
        this.syncHistory = new ConcurrentLinkedQueue<>();
        this.syncExecutor = Executors.newSingleThreadScheduledExecutor();
        this.isRunning = true;
    }

    public void startSync() {
        syncExecutor.scheduleAtFixedRate(
                this::performScoreSync,
                SYNC_INTERVAL_MS,
                SYNC_INTERVAL_MS,
                TimeUnit.MILLISECONDS
        );
    }

    public void handleScoreUpdate(String playerId, int newScore, ScoreType type, String reason) {
        ScoreUpdate update = new ScoreUpdate(
                playerId,
                newScore,
                type,
                reason,
                System.currentTimeMillis()
        );

        PlayerScoreState state = scoreStates.computeIfAbsent(
                playerId,
                id -> new PlayerScoreState(id)
        );

        if (validateScoreUpdate(state, update)) {
            state.addUpdate(update);
            pendingUpdates.offer(update);
            broadcastScoreUpdate(update);
        } else {
            requestScoreReconciliation(playerId);
        }
    }

    private boolean validateScoreUpdate(PlayerScoreState state, ScoreUpdate update) {
        if (state.getLastUpdate() == null) {
            return true;
        }

        int scoreDiff = update.score() - state.getLastUpdate().score();
        if (Math.abs(scoreDiff) > MAX_SCORE_DISCREPANCY) {
            if (state.getValidationFailures() < MAX_SYNC_RETRIES) {
                state.incrementValidationFailures();
                return true;
            }
            LOGGER.warning("Large score discrepancy detected for player: " +
                    update.playerId());
            return false;
        }

        return true;
    }

    private void broadcastScoreUpdate(ScoreUpdate update) {
        GameMessage message = new GameMessage(
                MessageType.SCORE_UPDATE,
                update.playerId(),
                null,
                null,
                new ScoreUpdateMessage(update)
        );
        gameClient.sendGameMessage(message);
    }

    private void requestScoreReconciliation(String playerId) {
        GameMessage message = new GameMessage(
                MessageType.SCORE_RECONCILIATION_REQUEST,
                playerId,
                null,
                null
        );
        gameClient.sendGameMessage(message);
    }

    public void handleReconciliationRequest(String playerId) {
        PlayerScoreState state = scoreStates.get(playerId);
        if (state != null) {
            List<ScoreUpdate> updates = state.getRecentUpdates();
            GameMessage message = new GameMessage(
                    MessageType.SCORE_RECONCILIATION_RESPONSE,
                    playerId,
                    null,
                    null,
                    new ScoreReconciliationMessage(updates)
            );
            gameClient.sendGameMessage(message);
        }
    }

    public void handleReconciliationResponse(String playerId, List<ScoreUpdate> updates) {
        PlayerScoreState state = scoreStates.get(playerId);
        if (state != null) {
            reconcileScores(state, updates);
        }
    }

    private void reconcileScores(PlayerScoreState state, List<ScoreUpdate> updates) {
        updates.sort(Comparator.comparingLong(ScoreUpdate::timestamp));

        state.clearUpdates();
        for (ScoreUpdate update : updates) {
            state.addUpdate(update);
            notifyScoreUpdate(update);
        }

        state.resetValidationFailures();
    }

    private void performScoreSync() {
        try {
            Map<String, Integer> currentScores = new HashMap<>();
            for (Map.Entry<String, PlayerScoreState> entry : scoreStates.entrySet()) {
                PlayerScoreState state = entry.getValue();
                if (state.getLastUpdate() != null) {
                    currentScores.put(entry.getKey(), state.getLastUpdate().score());
                }
            }

            ScoreSync sync = new ScoreSync(
                    currentScores,
                    System.currentTimeMillis()
            );

            syncHistory.offer(sync);
            if (syncHistory.size() > 10) {
                syncHistory.poll();
            }

            GameMessage message = new GameMessage(
                    MessageType.SCORE_SYNC,
                    "system",
                    null,
                    null,
                    new ScoreSyncMessage(sync)
            );
            gameClient.sendGameMessage(message);

        } catch (Exception e) {
            LOGGER.severe("Error during score sync: " + e.getMessage());
        }
    }

    public void handleScoreSync(ScoreSync sync) {
        sync.scores().forEach((playerId, score) -> {
            PlayerScoreState state = scoreStates.get(playerId);
            if (state != null &&
                    (state.getLastUpdate() == null ||
                            state.getLastUpdate().score() != score)) {

                handleScoreDiscrepancy(playerId, score);
            }
        });
    }

    private void handleScoreDiscrepancy(String playerId, int serverScore) {
        PlayerScoreState state = scoreStates.get(playerId);
        if (state != null) {
            int localScore = state.getLastUpdate() != null ?
                    state.getLastUpdate().score() : 0;

            if (Math.abs(localScore - serverScore) > MAX_SCORE_DISCREPANCY) {
                requestScoreReconciliation(playerId);
            }
        }
    }

    private void notifyScoreUpdate(ScoreUpdate update) {
        if (updateHandler != null) {
            Platform.runLater(() ->
                    updateHandler.onScoreUpdated(
                            update.playerId(),
                            update.score(),
                            update.type(),
                            update.reason()
                    ));
        }
    }

    public void handlePlayerDisconnection(String playerId) {
        PlayerScoreState state = scoreStates.get(playerId);
        if (state != null) {
            state.setDisconnected(true);
        }
    }

    public void handlePlayerReconnection(String playerId) {
        PlayerScoreState state = scoreStates.get(playerId);
        if (state != null) {
            state.setDisconnected(false);
            requestScoreReconciliation(playerId);
        }
    }

    public void setUpdateHandler(ScoreUpdateHandler handler) {
        this.updateHandler = handler;
    }

    public void cleanup() {
        isRunning = false;
        syncExecutor.shutdownNow();
        scoreStates.clear();
        pendingUpdates.clear();
        syncHistory.clear();
    }

    private static class PlayerScoreState {
        private final String playerId;
        private final List<ScoreUpdate> updates;
        private boolean disconnected;
        private int validationFailures;

        public PlayerScoreState(String playerId) {
            this.playerId = playerId;
            this.updates = new ArrayList<>();
            this.disconnected = false;
            this.validationFailures = 0;
        }

        public void addUpdate(ScoreUpdate update) {
            updates.add(update);
        }

        public ScoreUpdate getLastUpdate() {
            return updates.isEmpty() ? null : updates.get(updates.size() - 1);
        }

        public List<ScoreUpdate> getRecentUpdates() {
            return new ArrayList<>(updates);
        }

        public void clearUpdates() {
            updates.clear();
        }

        public void setDisconnected(boolean disconnected) {
            this.disconnected = disconnected;
        }

        public void incrementValidationFailures() {
            this.validationFailures++;
        }

        public void resetValidationFailures() {
            this.validationFailures = 0;
        }

        public int getValidationFailures() {
            return validationFailures;
        }
    }

    public record ScoreUpdate(
            String playerId,
            int score,
            ScoreType type,
            String reason,
            long timestamp
    ) implements java.io.Serializable {}

    public record ScoreSync(
            Map<String, Integer> scores,
            long timestamp
    ) implements java.io.Serializable {}

    public record ScoreUpdateMessage(
            ScoreUpdate update
    ) implements java.io.Serializable {}

    public record ScoreReconciliationMessage(
            List<ScoreUpdate> updates
    ) implements java.io.Serializable {}

    public record ScoreSyncMessage(
            ScoreSync sync
    ) implements java.io.Serializable {}

    public enum ScoreType {
        TILE_PLACEMENT,
        ROW_COMPLETION,
        COLUMN_COMPLETION,
        COLOR_SET_COMPLETION,
        PENALTY,
        BONUS,
        ADJUSTMENT
    }

    public interface ScoreUpdateHandler {
        void onScoreUpdated(String playerId, int newScore, ScoreType type, String reason);
        void onScoreReconciliation(String playerId);
        void onScoreError(String message);
    }
}
