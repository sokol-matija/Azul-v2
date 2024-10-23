package hr.algebra.azul.network;

import javafx.application.Platform;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class TurnManager {
    private static final Logger LOGGER = Logger.getLogger(TurnManager.class.getName());
    private static final int TURN_DURATION = 30000; // 30 seconds in milliseconds

    private Timer turnTimer;
    private final Consumer<Void> onTurnTimeout;
    private final Consumer<Integer> onTurnTick;
    private volatile boolean isTimerActive;
    private int remainingTime;

    public TurnManager(Consumer<Void> onTurnTimeout, Consumer<Integer> onTurnTick) {
        this.onTurnTimeout = onTurnTimeout;
        this.onTurnTick = onTurnTick;
    }

    public void startTurn() {
        if (turnTimer != null) {
            turnTimer.cancel();
        }

        turnTimer = new Timer(true);
        remainingTime = TURN_DURATION / 1000; // Convert to seconds
        isTimerActive = true;

        // Schedule the turn timer
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
        }, 0, 1000); // Update every second
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

    public void cancelTimer() {
        isTimerActive = false;
        if (turnTimer != null) {
            turnTimer.cancel();
            turnTimer = null;
        }
    }

    public int getRemainingTime() {
        return remainingTime;
    }

    public boolean isActive() {
        return isTimerActive;
    }
}