package hr.algebra.azul.network.game;

import hr.algebra.azul.model.*;
import hr.algebra.azul.network.*;
import javafx.application.Platform;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class GameEndManager {
    private static final Logger LOGGER = Logger.getLogger(GameEndManager.class.getName());
    private static final int SCORING_TIMEOUT_SECONDS = 10;
    private static final int MIN_PLAYERS_FOR_GAME = 2;
    private static final int SCORE_CONFIRMATION_RETRIES = 3;

    private final String gameId;
    private final GameClient gameClient;
    private final Map<String, PlayerScore> finalScores;
    private final CountDownLatch scoringLatch;
    private final Object scoringLock = new Object();
    private final ScheduledExecutorService endGameExecutor;
    private volatile boolean isGameEnding = false;
    private GameEndHandler endHandler;
    private final Map<String, ScoreConfirmation> scoreConfirmations;

    public GameEndManager(String gameId, GameClient gameClient) {
        this.gameId = gameId;
        this.gameClient = gameClient;
        this.finalScores = new ConcurrentHashMap<>();
        this.scoringLatch = new CountDownLatch(1);
        this.endGameExecutor = Executors.newScheduledThreadPool(2);
        this.scoreConfirmations = new ConcurrentHashMap<>();
    }

    public void checkGameEnd(Game game) {
        if (isGameEnding) return;

        if (shouldEndGame(game)) {
            synchronized (scoringLock) {
                if (!isGameEnding) {
                    isGameEnding = true;
                    handleGameEnd(game);
                }
            }
        }
    }

    private boolean shouldEndGame(Game game) {
        // Primary end condition - completed row
        if (game.isGameEnded()) {
            return true;
        }

        // Check if enough players remain
        long activePlayers = game.getPlayers().stream()
                .filter(Player::isConnected)
                .count();

        return activePlayers < MIN_PLAYERS_FOR_GAME;
    }

    private void handleGameEnd(Game game) {
        CompletableFuture.runAsync(() -> {
            try {
                // Calculate and verify final scores
                Map<String, Integer> scores = calculateFinalScores(game);

                // Broadcast end game notification
                broadcastGameEnd(scores);

                // Wait for score confirmations
                if (waitForScoreConfirmations()) {
                    FinalGameResult result = createFinalResult(scores);
                    broadcastFinalResult(result);
                } else {
                    handleScoringTimeout();
                }
            } catch (Exception e) {
                LOGGER.severe("Error handling game end: " + e.getMessage());
                handleGameEndError(e);
            }
        }, endGameExecutor);
    }

    private Map<String, Integer> calculateFinalScores(Game game) {
        Map<String, Integer> scores = new HashMap<>();

        for (Player player : game.getPlayers()) {
            // Calculate base score
            int baseScore = player.getScore();

            // Calculate wall bonuses
            Wall wall = player.getWall();
            int wallScore = wall.calculateScore();

            // Calculate row/column bonuses
            int rowBonus = calculateRowBonus(wall);
            int columnBonus = calculateColumnBonus(wall);
            int colorBonus = calculateColorSetBonus(wall);

            // Calculate total score
            int totalScore = baseScore + wallScore + rowBonus +
                    columnBonus + colorBonus;

            scores.put(player.getName(), totalScore);

            // Create detailed score record
            ScoreDetails details = new ScoreDetails(
                    baseScore,
                    wallScore,
                    rowBonus,
                    columnBonus,
                    colorBonus
            );

            finalScores.put(player.getName(), new PlayerScore(
                    totalScore,
                    details,
                    game.getPlayers().size()
            ));
        }

        return scores;
    }

    private int calculateRowBonus(Wall wall) {
        int bonus = 0;
        for (int row = 0; row < 5; row++) {
            if (isRowComplete(wall, row)) {
                bonus += 2;
            }
        }
        return bonus;
    }

    private int calculateColumnBonus(Wall wall) {
        int bonus = 0;
        for (int col = 0; col < 5; col++) {
            if (isColumnComplete(wall, col)) {
                bonus += 7;
            }
        }
        return bonus;
    }

    private int calculateColorSetBonus(Wall wall) {
        Map<TileColor, Integer> colorCounts = new EnumMap<>(TileColor.class);

        // Count tiles of each color
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                if (wall.hasTile(i, j)) {
                    TileColor color = wall.getTileColor(i, j);
                    colorCounts.merge(color, 1, Integer::sum);
                }
            }
        }

        // Award bonus for completed color sets
        return (int) colorCounts.values().stream()
                .filter(count -> count == 5)
                .count() * 10;
    }

    private boolean isRowComplete(Wall wall, int row) {
        for (int col = 0; col < 5; col++) {
            if (!wall.hasTile(row, col)) {
                return false;
            }
        }
        return true;
    }

    private boolean isColumnComplete(Wall wall, int col) {
        for (int row = 0; row < 5; row++) {
            if (!wall.hasTile(row, col)) {
                return false;
            }
        }
        return true;
    }

    private void broadcastGameEnd(Map<String, Integer> scores) {
        // Convert GameEndMessage to String representation for the chat content
        String messageContent = "Game End - Scores: " + scores.toString();

        gameClient.sendGameMessage(new GameMessage(
                MessageType.GAME_END,
                "system",
                null,
                null,
                messageContent
        ));
    }

    private boolean waitForScoreConfirmations() {
        try {
            return scoringLatch.await(SCORING_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private FinalGameResult createFinalResult(Map<String, Integer> scores) {
        List<PlayerFinalScore> playerScores = scores.entrySet().stream()
                .map(entry -> {
                    PlayerScore fullScore = finalScores.get(entry.getKey());
                    return new PlayerFinalScore(
                            entry.getKey(),
                            entry.getValue(),
                            fullScore != null ? fullScore.details() : null
                    );
                })
                .sorted((p1, p2) -> p2.score() - p1.score())
                .toList();

        return new FinalGameResult(
                gameId,
                playerScores,
                System.currentTimeMillis()
        );
    }

    private void broadcastFinalResult(FinalGameResult result) {
        String resultContent = "Final Results: " +
                result.playerScores().stream()
                        .map(ps -> ps.playerId() + ": " + ps.score())
                        .collect(Collectors.joining(", "));

        GameMessage resultMessage = new GameMessage(
                MessageType.GAME_RESULT,
                "system",
                null,
                null,
                resultContent
        );

        gameClient.sendGameMessage(resultMessage);

        if (endHandler != null) {
            Platform.runLater(() -> endHandler.onGameEnd(result));
        }
    }

    private void handleScoringTimeout() {
        LOGGER.warning("Scoring confirmation timeout - using available scores");
        Map<String, Integer> availableScores = finalScores.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().score()
                ));

        FinalGameResult result = createFinalResult(availableScores);
        broadcastFinalResult(result);
    }

    public void handleScoreConfirmation(String playerId, ScoreConfirmation confirmation) {
        scoreConfirmations.put(playerId, confirmation);

        // Check if we have all confirmations
        if (scoreConfirmations.size() == finalScores.size() &&
                validateScoreConfirmations()) {
            scoringLatch.countDown();
        }
    }

    private boolean validateScoreConfirmations() {
        for (Map.Entry<String, ScoreConfirmation> entry :
                scoreConfirmations.entrySet()) {
            PlayerScore serverScore = finalScores.get(entry.getKey());
            ScoreConfirmation clientScore = entry.getValue();

            if (!serverScore.equals(clientScore.score())) {
                handleScoreDiscrepancy(entry.getKey(), serverScore,
                        clientScore.score());
                return false;
            }
        }
        return true;
    }

    private void handleScoreDiscrepancy(String playerId,
                                        PlayerScore serverScore, PlayerScore clientScore) {
        LOGGER.warning("Score discrepancy detected for player: " + playerId);

        // Request score reconciliation
        GameMessage reconciliationRequest = new GameMessage(
                MessageType.SCORE_RECONCILIATION_REQUEST,
                playerId,
                null,
                null
        );
        gameClient.sendGameMessage(reconciliationRequest);
    }

    private void handleGameEndError(Exception e) {
        LOGGER.severe("Game end error: " + e.getMessage());
        if (endHandler != null) {
            Platform.runLater(() ->
                    endHandler.onGameEndError("Failed to end game: " + e.getMessage())
            );
        }
    }

    public void setEndHandler(GameEndHandler handler) {
        this.endHandler = handler;
    }

    public void cleanup() {
        endGameExecutor.shutdownNow();
        finalScores.clear();
        scoreConfirmations.clear();
    }

    // Record classes for game end data
    public record PlayerScore(
            int score,
            ScoreDetails details,
            int totalPlayers
    ) {}

    public record ScoreDetails(
            int baseScore,
            int wallScore,
            int rowBonus,
            int columnBonus,
            int colorBonus
    ) {}

    public record ScoreConfirmation(
            PlayerScore score,
            int retryCount
    ) {}

    public record PlayerFinalScore(
            String playerId,
            int score,
            ScoreDetails details
    ) {}

    public record FinalGameResult(
            String gameId,
            List<PlayerFinalScore> playerScores,
            long timestamp
    ) {}

    public record GameEndMessage(
            String gameId,
            Map<String, Integer> scores,
            long timestamp
    ) {}

    public interface GameEndHandler {
        void onGameEnd(FinalGameResult result);
        void onGameEndError(String message);
    }
}
