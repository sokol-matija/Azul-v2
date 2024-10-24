package hr.algebra.azul.network;

import javafx.application.Platform;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Logger;

public class SynchronizedTurnManager {
    private static final Logger LOGGER = Logger.getLogger(SynchronizedTurnManager.class.getName());
    private static final int TURN_DURATION = 30000; // 30 seconds

    private final GameClient gameClient;
    private final String playerId;
    private final Supplier<NetworkGameState> gameStateSupplier;
    private final Consumer<Integer> onTurnTick;
    private final Consumer<Void> onTurnTimeout;

    private Timer turnTimer;
    private volatile int remainingTime;
    private volatile boolean isTimerActive;

    public SynchronizedTurnManager(
            GameClient gameClient,
            String playerId,
            Supplier<NetworkGameState> gameStateSupplier,
            Consumer<Integer> onTurnTick,
            Consumer<Void> onTurnTimeout) {
        this.gameClient = gameClient;
        this.playerId = playerId;
        this.gameStateSupplier = gameStateSupplier;
        this.onTurnTick = onTurnTick;
        this.onTurnTimeout = onTurnTimeout;
    }

    public void startTurn(String turnPlayerId) {
        if (turnTimer != null) {
            turnTimer.cancel();
        }

        if (turnPlayerId.equals(playerId)) {
            turnTimer = new Timer(true);
            remainingTime = TURN_DURATION / 1000;
            isTimerActive = true;

            turnTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    if (isTimerActive) {
                        if (remainingTime > 0) {
                            remainingTime--;
                            Platform.runLater(() -> onTurnTick.accept(remainingTime));
                        } else {
                            endTurn();
                        }
                    }
                }
            }, 0, 1000);
        }
    }

    public void handleMove(GameAction move) {
        NetworkGameState state = gameStateSupplier.get();
        if (state != null && move.getType() == GameAction.ActionType.END_TURN) {
            endTurn();
        }
    }

    public void endTurn() {
        if (isTimerActive) {
            isTimerActive = false;
            if (turnTimer != null) {
                turnTimer.cancel();
                turnTimer = null;
            }
            Platform.runLater(() -> onTurnTimeout.accept(null));
        }
    }

    public void forceTurnEnd() {
        endTurn();
        GameAction forceEndAction = new GameAction(
                GameAction.ActionType.END_TURN,
                -1,
                null,
                -1
        );
        gameClient.sendGameMessage(new GameMessage(
                MessageType.MOVE,
                playerId,
                forceEndAction,
                gameStateSupplier.get().getGameState()
        ));
    }

    public void cleanup() {
        if (turnTimer != null) {
            turnTimer.cancel();
            turnTimer = null;
        }
        isTimerActive = false;
    }

    public int getRemainingTime() {
        return remainingTime;
    }

    public boolean isActive() {
        return isTimerActive;
    }
}