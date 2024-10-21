package hr.algebra.azul.model;

import java.util.ArrayList;
import java.util.List;

public class Player {
    private String name;
    private int score;
    private PatternLines patternLines;
    private Wall wall;
    private List<Tile> floorLine;
    private static final int MAX_FLOOR_LINE = 7;

    public Player(String name) {
        this.name = name;
        this.score = 0;
        this.patternLines = new PatternLines();
        this.wall = new Wall();
        this.floorLine = new ArrayList<>();
    }

    public void addTilesToPatternLine(List<Tile> tiles, int lineIndex) {
        if (lineIndex == -1) {
            addTilesToFloorLine(tiles);
        } else {
            List<Tile> overflow = patternLines.addTiles(tiles, lineIndex);
            addTilesToFloorLine(overflow);
        }
    }

    public void addTilesToFloorLine(List<Tile> tiles) {
        for (Tile tile : tiles) {
            if (floorLine.size() < MAX_FLOOR_LINE) {
                floorLine.add(tile);
            }
            // Excess tiles are returned to the bag (handled by Game class)
        }
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
                    addTilesToFloorLine(line);
                }
            }
        }
    }

    public void calculateScore() {
        score += wall.calculateScore();
        applyFloorLinePenalty();
    }

    private void applyFloorLinePenalty() {
        int[] penalties = {-1, -1, -2, -2, -2, -3, -3};
        for (int i = 0; i < floorLine.size(); i++) {
            score += penalties[Math.min(i, penalties.length - 1)];
        }
        score = Math.max(score, 0); // Score cannot go below zero
    }

    public List<Tile> clearFloorLine() {
        List<Tile> clearedTiles = new ArrayList<>(floorLine);
        floorLine.clear();
        return clearedTiles;
    }

    public boolean canAddTilesToPatternLine(TileColor color, int lineIndex) {
        return patternLines.canAddTiles(color, lineIndex);
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

    public List<Tile> getFloorLine() {
        return new ArrayList<>(floorLine);
    }



    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Player: ").append(name).append("\n");
        sb.append("Score: ").append(score).append("\n");
        sb.append("Pattern Lines:\n");
        for (int i = 0; i < 5; i++) {
            sb.append(i + 1).append(": ").append(patternLines.getLine(i)).append("\n");
        }
        sb.append("Wall:\n").append(wall.toString());
        sb.append("Floor Line: ").append(floorLine).append("\n");
        return sb.toString();
    }
}