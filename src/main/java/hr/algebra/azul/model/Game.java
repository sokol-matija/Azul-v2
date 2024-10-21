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

    public Game(int numberOfPlayers) {
        players = new ArrayList<>();
        for (int i = 0; i < numberOfPlayers; i++) {
            players.add(new Player("Player " + (i + 1)));
        }
        factories = new ArrayList<>();
        for (int i = 0; i < numberOfPlayers * 2 + 1; i++) {
            factories.add(new Factory());
        }
        centralArea = new CentralArea();
        currentPlayerIndex = 0;
        gameEnded = false;
        initializeTileBag();
    }

    private void initializeTileBag() {
        tileBag = new ArrayList<>();
        for (TileColor color : TileColor.values()) {
            for (int i = 0; i < 20; i++) {
                tileBag.add(new Tile(color));
            }
        }
    }

    public void startGame() {
        fillFactories();
    }

    private void fillFactories() {
        Random random = new Random();
        for (Factory factory : factories) {
            List<Tile> factoryTiles = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                if (!tileBag.isEmpty()) {
                    int index = random.nextInt(tileBag.size());
                    factoryTiles.add(tileBag.remove(index));
                }
            }
            factory.fillFactory(factoryTiles);
        }
    }

    public void takeTurn(Player player, Factory factory, TileColor color) {
        List<Tile> takenTiles = factory.takeTiles(color);
        player.addTilesToPatternLine(takenTiles, choosePatternLine(player, color));
        centralArea.addTiles(factory.getRemainingTiles());

        if (isRoundEnd()) {
            endRound();
        } else {
            nextPlayer();
        }
    }

    public void takeTurnFromCentralArea(Player player, TileColor color) {
        List<Tile> takenTiles = centralArea.takeTiles(color);
        player.addTilesToPatternLine(takenTiles, choosePatternLine(player, color));

        if (isRoundEnd()) {
            endRound();
        } else {
            nextPlayer();
        }
    }

    private int choosePatternLine(Player player, TileColor color) {
        // Implement logic to choose the appropriate pattern line
        // This could be done by the player or automatically
        return 0; // Placeholder
    }

    private boolean isRoundEnd() {
        return factories.stream().allMatch(Factory::isEmpty) && centralArea.isEmpty();
    }

    private void endRound() {
        for (Player player : players) {
            player.transferTilesToWall();
            player.calculateScore();
        }
        if (isGameEnd()) {
            gameEnded = true;
        } else {
            fillFactories();
        }
    }

    private boolean isGameEnd() {
        return players.stream().anyMatch(player -> player.hasCompletedRow());
    }

    private void nextPlayer() {
        currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
    }

    public void endTurn() {
        // This method can be used to forcibly end a turn if needed
        nextPlayer();
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
}