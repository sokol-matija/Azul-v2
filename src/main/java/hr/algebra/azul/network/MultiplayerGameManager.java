package hr.algebra.azul.network;

import hr.algebra.azul.model.Game;
import javafx.application.Platform;

import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

public class MultiplayerGameManager {
    private static final Logger LOGGER = Logger.getLogger(MultiplayerGameManager.class.getName());
    private static final int TURN_DURATION = 30000; // 30 seconds
    private static final int STATE_SYNC_INTERVAL = 5000; // 5 seconds

    private final GameClient gameClient;
    private final String playerId;
    private final List<String> playerOrder;
    private final Map<String, Long> lastHeartbeat;
    private final ScheduledExecutorService scheduler;
    private NetworkGameState gameState;
    private SynchronizedTurnManager turnManager;
    private volatile boolean isRunning;
    private final Object stateLock = new Object();

    private Consumer<GameState> onStateUpdate;
    private Consumer<String> onPlayerTimeout;
    private Consumer<Integer> onTurnTick;
    private Consumer<Void> onTurnTimeout;

    public MultiplayerGameManager(GameClient gameClient, String playerId, List<String> players) {
        this.gameClient = gameClient;
        this.playerId = playerId;
        this.playerOrder = new ArrayList<>(players);
        Collections.sort(this.playerOrder); // Ensure consistent order
        this.lastHeartbeat = new ConcurrentHashMap<>();
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.isRunning = false;
    }

    public void start() {
        isRunning = true;
        initializeGame();
        startHeartbeat();
        startStateSynchronization();
    }

    private void initializeGame() {
        if (isHost()) {
            // Host initializes the game
            Game game = new Game(playerOrder.size());
            game.startGame();

            // Assign players
            for (int i = 0; i < playerOrder.size(); i++) {
                game.getPlayers().get(i).setName(playerOrder.get(i));
            }

            gameState = new NetworkGameState(new GameState(game));
            gameState.startTurn(playerOrder.get(0)); // First player starts
            broadcastGameState();
        }

        turnManager = new SynchronizedTurnManager(
                gameClient,
                playerId,
                () -> gameState,
                onTurnTick,
                onTurnTimeout
        );
    }

    private void startHeartbeat() {
        scheduler.scheduleAtFixedRate(() -> {
            if (!isRunning) return;

            // Send heartbeat
            GameMessage heartbeat = new GameMessage(
                    MessageType.PING,
                    playerId,
                    null,
                    null
            );
            gameClient.sendGameMessage(heartbeat);

            // Check for timeouts
            long now = System.currentTimeMillis();
            lastHeartbeat.forEach((pid, lastBeat) -> {
                if (now - lastBeat > TURN_DURATION * 2) {
                    handlePlayerTimeout(pid);
                }
            });
        }, 0, 1000, TimeUnit.MILLISECONDS);
    }

    private void startStateSynchronization() {
        scheduler.scheduleAtFixedRate(() -> {
            if (!isRunning || !isHost()) return;
            broadcastGameState();
        }, 0, STATE_SYNC_INTERVAL, TimeUnit.MILLISECONDS);
    }

    public void handleMessage(GameMessage message) {
        lastHeartbeat.put(message.getPlayerId(), System.currentTimeMillis());

        switch (message.getType()) {
            case MOVE:
                handleGameMove(message);
                break;
            case SYNC:
                handleGameSync(message);
                break;
            case PING:
                handleHeartbeat(message);
                break;
            case STATE_REQUEST:
                if (isHost()) {
                    broadcastGameState();
                }
                break;
        }
    }

    private void handleGameMove(GameMessage message) {
        synchronized (stateLock) {
            if (gameState.isValidMove(message.getPlayerId(), message.getAction())) {
                gameState.applyMove(message.getAction());
                turnManager.handleMove(message.getAction());
                updateGameState();
            }
        }
    }

    private void handleGameSync(GameMessage message) {
        synchronized (stateLock) {
            if (message.getGameState() != null &&
                    message.getGameState().getVersion() > gameState.getGameState().getVersion()) {
                gameState = new NetworkGameState(message.getGameState());
                updateGameState();
            }
        }
    }

    private void handleHeartbeat(GameMessage message) {
        // Send acknowledgment
        GameMessage ack = new GameMessage(
                MessageType.PING,
                playerId,
                null,
                null
        );
        gameClient.sendGameMessage(ack);
    }

    private void handlePlayerTimeout(String timeoutPlayerId) {
        if (onPlayerTimeout != null) {
            Platform.runLater(() -> onPlayerTimeout.accept(timeoutPlayerId));
        }

        if (isHost()) {
            // Remove player from game
            synchronized (stateLock) {
                gameState.removePlayer(timeoutPlayerId);
                if (gameState.getCurrentPlayerId().equals(timeoutPlayerId)) {
                    // End current turn if it's the disconnected player's turn
                    turnManager.forceTurnEnd();
                }
                broadcastGameState();
            }
        }
    }

    private void broadcastGameState() {
        GameMessage stateMessage = new GameMessage(
                MessageType.SYNC,
                playerId,
                null,
                gameState.getGameState()
        );
        gameClient.sendGameMessage(stateMessage);
    }

    private void updateGameState() {
        if (onStateUpdate != null) {
            Platform.runLater(() -> onStateUpdate.accept(gameState.getGameState()));
        }
    }

    public boolean isHost() {
        return playerOrder.get(0).equals(playerId);
    }

    public boolean canPlayerAct() {
        return gameState != null &&
                gameState.getCurrentPlayerId().equals(playerId) &&
                isRunning;
    }

    public void makeMove(GameAction action) {
        if (canPlayerAct()) {
            GameMessage moveMessage = new GameMessage(
                    MessageType.MOVE,
                    playerId,
                    action,
                    gameState.getGameState()
            );
            gameClient.sendGameMessage(moveMessage);
            handleGameMove(moveMessage);
        }
    }

    public void shutdown() {
        isRunning = false;
        if (turnManager != null) {
            turnManager.cleanup();
        }
        scheduler.shutdownNow();
    }

    // Setters for callbacks
    public void setOnStateUpdate(Consumer<GameState> onStateUpdate) {
        this.onStateUpdate = onStateUpdate;
    }

    public void setOnPlayerTimeout(Consumer<String> onPlayerTimeout) {
        this.onPlayerTimeout = onPlayerTimeout;
    }

    public void setOnTurnTick(Consumer<Integer> onTurnTick) {
        this.onTurnTick = onTurnTick;
    }

    public void setOnTurnTimeout(Consumer<Void> onTurnTimeout) {
        this.onTurnTimeout = onTurnTimeout;
    }
}