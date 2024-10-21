package hr.algebra.azul.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Game {
    private List<Player> players;
    private List<Factory> factories;
    private CentralArea centralArea;
    private int currentPlayerIndex;
    private boolean gameEnded;
    private List<Tile> tileBag;
    private List<Tile> discardPile;
    private static final int TILES_PER_COLOR = 20;
    private static final int FACTORY_SIZE = 4;

    public Game(int numberOfPlayers) {
        if (numberOfPlayers < 2 || numberOfPlayers > 4) {
            throw new IllegalArgumentException("Number of players must be between 2 and 4");
        }
        initializePlayers(numberOfPlayers);
        initializeFactories(numberOfPlayers);
        centralArea = new CentralArea();
        currentPlayerIndex = 0;
        gameEnded = false;
        initializeTileBag();
        discardPile = new ArrayList<>();
    }

    private void initializePlayers(int numberOfPlayers) {
        players = new ArrayList<>();
        for (int i = 0; i < numberOfPlayers; i++) {
            players.add(new Player("Player " + (i + 1)));
        }
    }

    private void initializeFactories(int numberOfPlayers) {
        factories = new ArrayList<>();
        int numberOfFactories = numberOfPlayers * 2 + 1;
        for (int i = 0; i < numberOfFactories; i++) {
            factories.add(new Factory());
        }
    }

    private void initializeTileBag() {
        tileBag = new ArrayList<>();
        for (TileColor color : TileColor.values()) {
            for (int i = 0; i < TILES_PER_COLOR; i++) {
                tileBag.add(new Tile(color));
            }
        }
    }

    public void startGame() {
        shuffleTileBag();
        fillFactories();
    }

    private void shuffleTileBag() {
        Random random = new Random();
        for (int i = tileBag.size() - 1; i > 0; i--) {
            int index = random.nextInt(i + 1);
            Tile temp = tileBag.get(index);
            tileBag.set(index, tileBag.get(i));
            tileBag.set(i, temp);
        }
    }

    private void fillFactories() {
        for (Factory factory : factories) {
            List<Tile> factoryTiles = new ArrayList<>();
            for (int i = 0; i < FACTORY_SIZE; i++) {
                if (tileBag.isEmpty()) {
                    refillTileBag();
                }
                if (!tileBag.isEmpty()) {
                    factoryTiles.add(tileBag.remove(tileBag.size() - 1));
                }
            }
            factory.fillFactory(factoryTiles);
        }
    }

    private void refillTileBag() {
        tileBag.addAll(discardPile);
        discardPile.clear();
        shuffleTileBag();
    }

    public boolean takeTurn(Player player, Factory factory, TileColor color, int patternLineIndex) {
        if (player != getCurrentPlayer()) {
            return false;
        }

        List<Tile> takenTiles;

        if (factory == null) {
            takenTiles = centralArea.takeTiles(color);
        } else {
            takenTiles = factory.takeTiles(color);
            centralArea.addTiles(factory.getRemainingTiles());
        }

        if (takenTiles.isEmpty()) {
            return false;
        }

        boolean tilePlaced = false;
        if (patternLineIndex >= 0 && patternLineIndex < 5) {
            tilePlaced = player.addTilesToPatternLine(takenTiles, patternLineIndex);
        }

        if (!tilePlaced) {
            player.addTilesToFloorLine(takenTiles);
        }

        if (isRoundEnd()) {
            endRound();
        } else {
            nextPlayer();
        }

        return true;
    }

    public boolean takeTurnFromCentralArea(Player player, TileColor color, int patternLineIndex) {
        return takeTurn(player, null, color, patternLineIndex);
    }

    public void endTurn() {
        nextPlayer();
    }

    private boolean isRoundEnd() {
        return factories.stream().allMatch(Factory::isEmpty) && centralArea.isEmpty();
    }

    private void endRound() {
        for (Player player : players) {
            player.transferTilesToWall();
            int floorLinePenalty = player.calculateFloorLinePenalty();
            player.setScore(player.getScore() + floorLinePenalty);
            discardPile.addAll(player.clearFloorLine());
        }
        if (isGameEnd()) {
            gameEnded = true;
            calculateFinalScores();
        } else {
            fillFactories();
        }
    }

    private boolean isGameEnd() {
        return players.stream().anyMatch(Player::hasCompletedRow);
    }

    private void calculateFinalScores() {
        for (Player player : players) {
            int finalScore = player.getScore();
            finalScore += player.getWall().calculateScore();
            player.setScore(finalScore);
        }
    }

    private void nextPlayer() {
        currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
    }

    public Player getCurrentPlayer() {
        return players.get(currentPlayerIndex);
    }

    public List<Factory> getFactories() {
        return factories;
    }

    public CentralArea getCentralArea() {
        return centralArea;
    }

    public List<Player> getPlayers() {
        return players;
    }

    public boolean isGameEnded() {
        return gameEnded;
    }

    public Player getWinner() {
        if (!gameEnded) {
            return null;
        }
        return players.stream().max((p1, p2) -> Integer.compare(p1.getScore(), p2.getScore())).orElse(null);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Game State:\n");
        sb.append("Current Player: ").append(getCurrentPlayer().getName()).append("\n");
        sb.append("Factories:\n");
        for (int i = 0; i < factories.size(); i++) {
            sb.append("Factory ").append(i).append(": ").append(factories.get(i)).append("\n");
        }
        sb.append("Central Area: ").append(centralArea).append("\n");
        sb.append("Players:\n");
        for (Player player : players) {
            sb.append(player).append("\n");
        }
        sb.append("Game Ended: ").append(gameEnded).append("\n");
        return sb.toString();
    }
}