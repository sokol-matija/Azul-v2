package hr.algebra.azul.network.game;

import hr.algebra.azul.network.GameClient;
import hr.algebra.azul.network.GameState;
import hr.algebra.azul.network.GameMessage;
import hr.algebra.azul.network.MessageType;
import java.util.logging.Logger;

public class GameStateManager {
    private static final Logger LOGGER = Logger.getLogger(GameStateManager.class.getName());

    private final GameClient gameClient;
    private final String playerId;
    private GameState currentState;

    public GameStateManager(GameClient gameClient, String playerId) {
        this.gameClient = gameClient;
        this.playerId = playerId;
    }

    public void updateGameState(GameState newState) {
        this.currentState = newState;
        broadcastGameState();
    }

    public GameState getCurrentState() {
        return currentState;
    }

    private void broadcastGameState() {
        if (currentState == null) return;

        GameMessage stateUpdate = new GameMessage(
            MessageType.SYNC,
            playerId,
            currentState,
            null
        );
        gameClient.sendGameMessage(stateUpdate);
    }

    public void cleanup() {
        currentState = null;
    }
}
