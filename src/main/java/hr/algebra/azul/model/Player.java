package hr.algebra.azul.model;

import java.util.*;

public class Player {
    private String name;
    private int score;
    private PatternLines patternLines;
    private Wall wall;
    private List<Tile> negativeLine;
    private Map<TileColor, Integer> hand;
    private static final int MAX_NEGATIVE_LINE = 7;

    public Player(String name) {
        this.name = name;
        this.score = 0;
        this.patternLines = new PatternLines();
        this.wall = new Wall();
        this.negativeLine = new ArrayList<>();
        this.hand = new EnumMap<>(TileColor.class);
    }

    public void addTilesToHand(List<Tile> tiles) {
        for (Tile tile : tiles) {
            hand.put(tile.getColor(), hand.getOrDefault(tile.getColor(), 0) + 1);
        }
    }

    public boolean placeTilesFromHand(TileColor color, int lineIndex) {
        if (!hand.containsKey(color) || hand.get(color) == 0) {
            return false;
        }

        int tilesInHand = hand.get(color);
        List<Tile> tilesToPlace = new ArrayList<>();
        for (int i = 0; i < tilesInHand; i++) {
            tilesToPlace.add(new Tile(color));
        }

        boolean placed = addTilesToPatternLine(tilesToPlace, lineIndex);
        if (placed) {
            hand.remove(color);
        }
        return placed;
    }

    public Map<TileColor, Integer> getHand() {
        return new EnumMap<>(hand);
    }

    public void clearHand() {
        hand.clear();
    }

    public boolean addTilesToPatternLine(List<Tile> tiles, int lineIndex) {
        if (lineIndex < 0 || lineIndex >= 5) {
            addTilesToNegativeLine(tiles);
            return true;
        }

        List<Tile> overflow = patternLines.addTiles(tiles, lineIndex);
        addTilesToNegativeLine(overflow);
        return true;
    }

    public void addTilesToNegativeLine(List<Tile> tiles) {
        for (Tile tile : tiles) {
            if (negativeLine.size() < MAX_NEGATIVE_LINE) {
                negativeLine.add(tile);
            }
        }
    }

    public int calculateNegativeLinePenalty() {
        int[] penalties = {-1, -1, -2, -2, -2, -3, -3};
        int penalty = 0;
        for (int i = 0; i < negativeLine.size(); i++) {
            penalty += penalties[Math.min(i, penalties.length - 1)];
        }
        return penalty;
    }

    public boolean canAddTilesToPatternLine(TileColor color, int lineIndex) {
        return patternLines.canAddTiles(color, lineIndex) && wall.canPlaceTile(color, lineIndex);
    }

    public void transferTilesToWall() {
        for (int i = 0; i < 5; i++) {
            List<Tile> line = patternLines.getLine(i);
            if (line.size() == i + 1) {
                Tile tile = line.get(0);
                if (wall.canPlaceTile(tile.getColor(), i)) {
                    wall.placeTile(tile, i);
                    score += wall.calculatePlacementScore(i, wall.getColumnForColor(tile.getColor(), i));
                    patternLines.clearLine(i);
                } else {
                    addTilesToNegativeLine(line);
                }
            }
        }
    }

    public List<Tile> clearNegativeLine() {
        List<Tile> clearedTiles = new ArrayList<>(negativeLine);
        negativeLine.clear();
        return clearedTiles;
    }

    public boolean hasCompletedRow() {
        return wall.hasCompletedRow();
    }

    // Getters and setters
    public String getName() {
        return name;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public PatternLines getPatternLines() {
        return patternLines;
    }

    public Wall getWall() {
        return wall;
    }

    public List<Tile> getNegativeLine() {
        return new ArrayList<>(negativeLine);
    }

    @Override
    public String toString() {
        return "Player{" +
                "name='" + name + '\'' +
                ", score=" + score +
                ", hand=" + hand +
                ", negativeLine=" + negativeLine +
                '}';
    }
}