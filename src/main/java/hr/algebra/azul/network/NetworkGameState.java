        package hr.algebra.azul.network;

import hr.algebra.azul.model.*;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class NetworkGameState implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final ConcurrentHashMap<String, NetworkGameState> activeStates = new ConcurrentHashMap<>();

    private final String gameId;
    private final Map<String, PlayerState> playerStates;
    private final GamePhase currentPhase;
    private final String currentPlayerId;
    private final long lastUpdateTimestamp;
    private final int currentRound;
    private final boolean gameStarted;
    private final Game game;

    private NetworkGameState(Builder builder) {
        this.gameId = builder.gameId;
        this.playerStates = builder.playerStates;
        this.currentPhase = builder.currentPhase;
        this.currentPlayerId = builder.currentPlayerId;
        this.lastUpdateTimestamp = System.currentTimeMillis();
        this.currentRound = builder.currentRound;
        this.gameStarted = builder.gameStarted;
        this.game = builder.game;
    }

    public static NetworkGameState getCurrentState(String gameId) {
        return activeStates.get(gameId);
    }

    public static void updateState(String gameId, NetworkGameState state) {
        activeStates.put(gameId, state);
    }

    public Game getGame() {
        return game;
    }

    public String getGameId() {
        return gameId;
    }

    public Map<String, PlayerState> getPlayerStates() {
        return new HashMap<>(playerStates);
    }

    public GamePhase getCurrentPhase() {
        return currentPhase;
    }

    public String getCurrentPlayerId() {
        return currentPlayerId;
    }

    public long getLastUpdateTimestamp() {
        return lastUpdateTimestamp;
    }

    public int getCurrentRound() {
        return currentRound;
    }

    public boolean isGameStarted() {
        return gameStarted;
    }

    public static class Builder {
        private final String gameId;
        private final Map<String, PlayerState> playerStates = new ConcurrentHashMap<>();
        private GamePhase currentPhase = GamePhase.WAITING_FOR_PLAYERS;
        private String currentPlayerId;
        private int currentRound = 0;
        private boolean gameStarted = false;
        private Game game;

        public Builder(String gameId) {
            this.gameId = gameId;
        }

        public Builder addPlayer(String playerId, Player player) {
            playerStates.put(playerId, new PlayerState(player));
            return this;
        }

        public Builder setCurrentPhase(GamePhase phase) {
            this.currentPhase = phase;
            return this;
        }

        public Builder setCurrentPlayer(String playerId) {
            this.currentPlayerId = playerId;
            return this;
        }

        public Builder setCurrentRound(int round) {
            this.currentRound = round;
            return this;
        }

        public Builder setGameStarted(boolean started) {
            this.gameStarted = started;
            return this;
        }

        public Builder setGame(Game game) {
            this.game = game;
            return this;
        }

        public NetworkGameState build() {
            return new NetworkGameState(this);
        }
    }

    public static class PlayerState implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String name;
        private final int score;
        private final WallState wallState;
        private final PatternLinesState patternLinesState;
        private final List<TileColor> negativeLine;
        private final Map<TileColor, Integer> hand;

        public PlayerState(Player player) {
            this.name = player.getName();
            this.score = player.getScore();
            this.wallState = new WallState(player.getWall());
            this.patternLinesState = new PatternLinesState(player.getPatternLines());
            this.negativeLine = player.getNegativeLine().stream()
                    .map(Tile::getColor)
                    .toList();
            this.hand = new HashMap<>(player.getHand());
        }

        public void applyTo(Player player) {
            wallState.applyTo(player.getWall());
            patternLinesState.applyTo(player.getPatternLines());

            player.clearNegativeLine();
            negativeLine.forEach(color ->
                    player.addTilesToNegativeLine(List.of(new Tile(color))));

            player.setScore(score);
        }

        public String getName() {
            return name;
        }

        public int getScore() {
            return score;
        }

        public List<TileColor> getNegativeLine() {
            return new ArrayList<>(negativeLine);
        }

        public Map<TileColor, Integer> getHand() {
            return new HashMap<>(hand);
        }
    }

    public static class WallState implements Serializable {
        private static final long serialVersionUID = 1L;
        private final boolean[][] tiles;
        private final TileColor[][] colors;

        public WallState(Wall wall) {
            tiles = new boolean[5][5];
            colors = new TileColor[5][5];

            for (int i = 0; i < 5; i++) {
                for (int j = 0; j < 5; j++) {
                    tiles[i][j] = wall.hasTile(i, j);
                    colors[i][j] = wall.getTileColor(i, j);
                }
            }
        }

        public void applyTo(Wall wall) {
            for (int i = 0; i < 5; i++) {
                for (int j = 0; j < 5; j++) {
                    if (tiles[i][j] && colors[i][j] != null) {
                        wall.placeTile(new Tile(colors[i][j]), i);
                    }
                }
            }
        }
    }

    public static class PatternLinesState implements Serializable {
        private static final long serialVersionUID = 1L;
        private final List<List<TileColor>> lines;

        public PatternLinesState(PatternLines patternLines) {
            lines = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                List<TileColor> line = patternLines.getLine(i).stream()
                        .map(Tile::getColor)
                        .toList();
                lines.add(new ArrayList<>(line));
            }
        }

        public void applyTo(PatternLines patternLines) {
            for (int i = 0; i < lines.size(); i++) {
                List<Tile> tilesToAdd = lines.get(i).stream()
                        .map(Tile::new)
                        .toList();
                patternLines.addTiles(tilesToAdd, i);
            }
        }
    }

    public enum GamePhase {
        WAITING_FOR_PLAYERS,
        IN_PROGRESS,
        ROUND_END,
        GAME_END
    }

    @Override
    public String toString() {
        return "NetworkGameState{" +
                "gameId='" + gameId + '\'' +
                ", currentPhase=" + currentPhase +
                ", currentPlayerId='" + currentPlayerId + '\'' +
                ", currentRound=" + currentRound +
                ", gameStarted=" + gameStarted +
                '}';
    }
}
