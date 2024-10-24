package hr.algebra.azul.network;

import hr.algebra.azul.model.Game;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class NetworkGameState {
    private GameState gameState;
    private String currentPlayerId;
    private final AtomicLong version;
    private volatile boolean turnInProgress;

    public NetworkGameState(GameState initialState) {
        this.gameState = initialState;
        this.currentPlayerId = initialState.getCurrentPlayerId();
        this.version = new AtomicLong(0);
        this.turnInProgress = false;
    }

    public boolean isValidMove(String playerId, GameAction action) {
        if (!playerId.equals(currentPlayerId) || !turnInProgress) {
            return false;
        }

        // Validate move based on game rules
        Game game = gameState.getGame();
        switch (action.getType()) {
            case SELECT_TILES:
                return game.getCurrentPlayer().getName().equals(playerId) &&
                        !game.getCurrentPlayer().hasSelectedThisTurn();
            case PLACE_TILES:
                return game.getCurrentPlayer().getName().equals(playerId) &&
                        game.getCurrentPlayer().hasSelectedThisTurn() &&
                        !game.getCurrentPlayer().getHand().isEmpty();
            case END_TURN:
                return true;
            default:
                return false;
        }
    }

    public void applyMove(GameAction action) {
        Game game = gameState.getGame();
        switch (action.getType()) {
            case SELECT_TILES:
                if (action.getFactoryIndex() == -1) {
                    game.takeTurn(game.getCurrentPlayer(), null,
                            action.getSelectedColor(), action.getPatternLineIndex());
                } else {
                    game.takeTurn(game.getCurrentPlayer(),
                            game.getFactories().get(action.getFactoryIndex()),
                            action.getSelectedColor(), action.getPatternLineIndex());
                }
                break;
            case PLACE_TILES:
                game.placeTiles(game.getCurrentPlayer(),
                        action.getSelectedColor(),
                        action.getPatternLineIndex());
                break;
            case END_TURN:
                game.endTurn();
                endTurn();
                break;
        }
        version.incrementAndGet();
    }

    public void startTurn(String playerId) {
        this.currentPlayerId = playerId;
        this.turnInProgress = true;
        gameState.setCurrentPlayerId(playerId);
    }

    public void endTurn() {
        this.turnInProgress = false;
        List<String> players = gameState.getConnectedPlayers();
        int currentIndex = players.indexOf(currentPlayerId);
        int nextIndex = (currentIndex + 1) % players.size();
        startTurn(players.get(nextIndex));
    }

    public void removePlayer(String playerId) {
        gameState.removePlayer(playerId);
        version.incrementAndGet();
    }

    public GameState getGameState() {
        return gameState;
    }

    public String getCurrentPlayerId() {
        return currentPlayerId;
    }

    public long getVersion() {
        return version.get();
    }
}