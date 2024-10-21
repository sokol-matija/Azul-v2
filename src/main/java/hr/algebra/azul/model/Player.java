package hr.algebra.azul.model;

import java.util.ArrayList;
import java.util.List;

public class Player {
    private String name;
    private int score;
    private PatternLines patternLines;
    private Wall wall;

    public Player(String name) {
        this.name = name;
        this.score = 0;
        this.patternLines = new PatternLines();
        this.wall = new Wall();
    }

    public void addTilesToPatternLine(List<Tile> tiles, int lineIndex) {
        patternLines.addTiles(tiles, lineIndex);
    }

    public void transferTilesToWall() {
        for (int i = 0; i < 5; i++) {
            List<Tile> line = patternLines.getLine(i);
            if (line.size() == i + 1) {
                Tile tile = line.get(0);
                if (wall.canPlaceTile(tile.getColor(), i)) {
                    wall.placeTile(tile, i);
                    patternLines.clearLine(i);
                    // TODO: Add scoring logic here
                }
            }
        }
    }

    public void calculateScore() {
        // TODO: Implement full scoring logic
        int wallScore = wall.calculateScore();
        score += wallScore;
    }

    public String getName() {
        return name;
    }

    public int getScore() {
        return score;
    }

    public PatternLines getPatternLines() {
        return patternLines;
    }

    public Wall getWall() {
        return wall;
    }

    public boolean hasCompletedRow() {
        return wall.hasCompletedRow();
    }
}

