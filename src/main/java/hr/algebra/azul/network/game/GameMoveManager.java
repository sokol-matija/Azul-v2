package hr.algebra.azul.network.game;

import hr.algebra.azul.model.*;
import hr.algebra.azul.network.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

public class GameMoveManager {
    private static final Logger LOGGER = Logger.getLogger(GameMoveManager.class.getName());

    private final String gameId;
    private final GameClient gameClient;
    private final Map<String, PlayerMoveState> playerStates;
    private final Queue<GameMove> moveHistory;
    private final Object moveLock = new Object();
    private final GameStateManager stateManager;
    private volatile String currentPlayerId;

    public GameMoveManager(String gameId, GameClient gameClient, GameStateManager stateManager) {
        this.gameId = gameId;
        this.gameClient = gameClient;
        this.stateManager = stateManager;
        this.playerStates = new ConcurrentHashMap<>();
        this.moveHistory = new ConcurrentLinkedQueue<>();
    }

    public boolean validateAndProcessMove(GameMove move) {
        synchronized (moveLock) {
            if (!isValidMove(move)) {
                LOGGER.warning("Invalid move attempted: " + move);
                return false;
            }

            try {
                processMove(move);
                moveHistory.add(move);
                return true;
            } catch (Exception e) {
                LOGGER.severe("Error processing move: " + e.getMessage());
                rollbackLastMove();
                return false;
            }
        }
    }

    private boolean isValidMove(GameMove move) {
        if (!move.getPlayerId().equals(currentPlayerId)) {
            return false;
        }

            PlayerMoveState playerState = playerStates.get(move.getPlayerId());
        if (playerState == null) {
            return false;
        }

        return switch (move.getType()) {
            case SELECT_TILES -> validateTileSelection(move, playerState);
            case PLACE_TILES -> validateTilePlacement(move, playerState);
            case END_TURN -> validateEndTurn(playerState);
        };
    }

    private boolean validateTileSelection(GameMove move, PlayerMoveState playerState) {
        if (playerState.hasSelectedTiles()) {
            return false;
        }

        TileSelection selection = move.getTileSelection();
        if (selection == null) {
            return false;
        }

        Game game = stateManager.getCurrentState().toGame();
        if (selection.factoryIndex() >= 0) {
            if (selection.factoryIndex() >= game.getFactories().size()) {
                return false;
            }
            Factory factory = game.getFactories().get(selection.factoryIndex());
            return factory.getTiles().stream()
                    .anyMatch(tile -> tile.getColor() == selection.color());
        } else {
            return game.getCentralArea().getTiles().stream()
                    .anyMatch(tile -> tile.getColor() == selection.color());
        }
    }

    private boolean validateTilePlacement(GameMove move, PlayerMoveState playerState) {
        if (!playerState.hasSelectedTiles()) {
            return false;
        }

        TilePlacement placement = move.getTilePlacement();
        if (placement == null) {
            return false;
        }

        Game game = stateManager.getCurrentState().toGame();
        Player currentPlayer = game.getCurrentPlayer();

        if (!currentPlayer.getHand().containsKey(placement.color())) {
            return false;
        }

        return currentPlayer.canAddTilesToPatternLine(
                placement.color(),
                placement.patternLineIndex()
        );
    }

    private boolean validateEndTurn(PlayerMoveState playerState) {
        return !playerState.hasSelectedTiles() ||
                playerState.getSelectedTiles().isEmpty();
    }

    private void processMove(GameMove move) {
        PlayerMoveState playerState = playerStates.get(move.getPlayerId());
        Game game = stateManager.getCurrentState().toGame();

        switch (move.getType()) {
            case SELECT_TILES -> processTileSelection(move, playerState, game);
            case PLACE_TILES -> processTilePlacement(move, playerState, game);
            case END_TURN -> processEndTurn(game);
        }

        stateManager.updateGameState(NetworkGameState.fromGame(
                game,
                stateManager.getPlayerMapping())
        );
    }

    private void processTileSelection(GameMove move, PlayerMoveState playerState, Game game) {
        TileSelection selection = move.getTileSelection();
        Player currentPlayer = game.getCurrentPlayer();

        List<Tile> selectedTiles;
        if (selection.factoryIndex() >= 0) {
            Factory factory = game.getFactories().get(selection.factoryIndex());
            selectedTiles = factory.takeTiles(selection.color());
            game.getCentralArea().addTiles(factory.getRemainingTiles());
        } else {
            selectedTiles = game.getCentralArea().takeTiles(selection.color());
        }

        playerState.setSelectedTiles(selectedTiles);
        currentPlayer.addTilesToHand(selectedTiles);
    }

    private void processTilePlacement(GameMove move, PlayerMoveState playerState, Game game) {
        TilePlacement placement = move.getTilePlacement();
        Player currentPlayer = game.getCurrentPlayer();

        boolean placed = currentPlayer.placeTilesFromHand(
                placement.color(),
                placement.patternLineIndex()
        );

        if (placed) {
            playerState.clearSelectedTiles();
        }
    }

    private void processEndTurn(Game game) {
        game.endTurn();
        updateCurrentPlayer(game.getCurrentPlayer().getName());
    }

    private void rollbackLastMove() {
        GameMove lastMove = moveHistory.poll();
        if (lastMove != null) {
            LOGGER.info("Rolling back move: " + lastMove);
        }
    }

    public void updateCurrentPlayer(String playerId) {
        this.currentPlayerId = playerId;
    }

    public record GameMove(
            String playerId,
            GameMoveType type,
            TileSelection tileSelection,
            TilePlacement tilePlacement
    ) {
        public static GameMove selectTiles(String playerId, int factoryIndex, TileColor color) {
            return new GameMove(
                    playerId,
                    GameMoveType.SELECT_TILES,
                    new TileSelection(factoryIndex, color),
                    null
            );
        }

        public static GameMove placeTiles(String playerId, TileColor color, int patternLineIndex) {
            return new GameMove(
                    playerId,
                    GameMoveType.PLACE_TILES,
                    null,
                    new TilePlacement(color, patternLineIndex)
            );
        }

        public static GameMove endTurn(String playerId) {
            return new GameMove(
                    playerId,
                    GameMoveType.END_TURN,
                    null,
                    null
            );
        }
    }

    public record TileSelection(int factoryIndex, TileColor color) {}
    public record TilePlacement(TileColor color, int patternLineIndex) {}

    public enum GameMoveType {
        SELECT_TILES,
        PLACE_TILES,
        END_TURN
    }

    private static class PlayerMoveState {
        private final String playerId;
        private final List<Tile> selectedTiles;
        private boolean hasTurn;

        public PlayerMoveState(String playerId) {
            this.playerId = playerId;
            this.selectedTiles = new ArrayList<>();
            this.hasTurn = false;
        }

        public boolean hasSelectedTiles() {
            return !selectedTiles.isEmpty();
        }

        public void setSelectedTiles(List<Tile> tiles) {
            selectedTiles.clear();
            selectedTiles.addAll(tiles);
        }

        public void clearSelectedTiles() {
            selectedTiles.clear();
        }

        public List<Tile> getSelectedTiles() {
            return new ArrayList<>(selectedTiles);
        }

        public boolean hasTurn() {
            return hasTurn;
        }

        public void setHasTurn(boolean hasTurn) {
            this.hasTurn = hasTurn;
        }
    }

    public void clean() {
        playerStates.clear();
        moveHistory.clear();
    }
}
