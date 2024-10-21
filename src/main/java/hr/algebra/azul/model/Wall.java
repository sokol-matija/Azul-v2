package hr.algebra.azul.model;

public class Wall {
    private Tile[][] tiles;
    private static final TileColor[][] wallPattern = {
            {TileColor.BLUE, TileColor.YELLOW, TileColor.RED, TileColor.BLACK, TileColor.WHITE},
            {TileColor.WHITE, TileColor.BLUE, TileColor.YELLOW, TileColor.RED, TileColor.BLACK},
            {TileColor.BLACK, TileColor.WHITE, TileColor.BLUE, TileColor.YELLOW, TileColor.RED},
            {TileColor.RED, TileColor.BLACK, TileColor.WHITE, TileColor.BLUE, TileColor.YELLOW},
            {TileColor.YELLOW, TileColor.RED, TileColor.BLACK, TileColor.WHITE, TileColor.BLUE}
    };

    public Wall() {
        tiles = new Tile[5][5];
    }

    public boolean canPlaceTile(TileColor color, int row) {
        if (isColorCompleted(color)) {
            return false;  // Cannot place a tile if its color is already completed
        }
        int col = getColumnForColor(color, row);
        return col != -1 && tiles[row][col] == null;
    }

    public boolean isColorCompleted(TileColor color) {
        int count = 0;
        for (int row = 0; row < 5; row++) {
            for (int col = 0; col < 5; col++) {
                if (tiles[row][col] != null && tiles[row][col].getColor() == color) {
                    count++;
                }
            }
        }
        return count == 5;
    }


    public void placeTile(Tile tile, int row) {
        int col = getColumnForColor(tile.getColor(), row);
        if (col != -1 && tiles[row][col] == null && !isColorCompleted(tile.getColor())) {
            tiles[row][col] = tile;
        } else {
            throw new IllegalArgumentException("Cannot place tile in this position");
        }
    }

    public boolean hasCompletedRow() {
        for (int row = 0; row < 5; row++) {
            if (isRowComplete(row)) {
                return true;
            }
        }
        return false;
    }

    public int calculateScore() {
        return calculateRowsScore() + calculateColumnsScore() + calculateColorSetsScore();
    }

    private int calculateRowsScore() {
        int score = 0;
        for (int row = 0; row < 5; row++) {
            if (isRowComplete(row)) {
                score += 2;
            }
        }
        return score;
    }

    private int calculateColumnsScore() {
        int score = 0;
        for (int col = 0; col < 5; col++) {
            if (isColumnComplete(col)) {
                score += 7;
            }
        }
        return score;
    }

    private int calculateColorSetsScore() {
        int score = 0;
        for (TileColor color : TileColor.values()) {
            if (isColorCompleted(color)) {
                score += 10;
            }
        }
        return score;
    }

    public int calculatePlacementScore(int row, int col) {
        int score = 1; // Minimum score for placement
        score += calculateHorizontalScore(row, col);
        score += calculateVerticalScore(row, col);
        return score;
    }

    private int calculateHorizontalScore(int row, int col) {
        int score = 0;
        // Check left
        for (int c = col - 1; c >= 0 && tiles[row][c] != null; c--) {
            score++;
        }
        // Check right
        for (int c = col + 1; c < 5 && tiles[row][c] != null; c++) {
            score++;
        }
        return score;
    }

    private int calculateVerticalScore(int row, int col) {
        int score = 0;
        // Check up
        for (int r = row - 1; r >= 0 && tiles[r][col] != null; r--) {
            score++;
        }
        // Check down
        for (int r = row + 1; r < 5 && tiles[r][col] != null; r++) {
            score++;
        }
        return score;
    }

    private boolean isRowComplete(int row) {
        for (int col = 0; col < 5; col++) {
            if (tiles[row][col] == null) {
                return false;
            }
        }
        return true;
    }

    private boolean isColumnComplete(int col) {
        for (int row = 0; row < 5; row++) {
            if (tiles[row][col] == null) {
                return false;
            }
        }
        return true;
    }

    public int getColumnForColor(TileColor color, int row) {
        for (int col = 0; col < 5; col++) {
            if (wallPattern[row][col] == color) {
                return col;
            }
        }
        return -1; // Color not found in this row
    }

    public boolean hasTile(int row, int col) {
        return tiles[row][col] != null;
    }

    public TileColor getTileColor(int row, int col) {
        return tiles[row][col] != null ? tiles[row][col].getColor() : null;
    }

    public Tile[][] getTiles() {
        return tiles;
    }

    public static TileColor getWallPatternColor(int row, int col) {
        return wallPattern[row][col];
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int row = 0; row < 5; row++) {
            for (int col = 0; col < 5; col++) {
                if (tiles[row][col] == null) {
                    sb.append("- ");
                } else {
                    sb.append(tiles[row][col].getColor().toString().charAt(0)).append(" ");
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}