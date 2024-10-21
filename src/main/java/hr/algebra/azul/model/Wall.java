package hr.algebra.azul.model;


class Wall {
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
        for (int col = 0; col < 5; col++) {
            if (wallPattern[row][col] == color && tiles[row][col] == null) {
                return true;
            }
        }
        return false;
    }

    public void placeTile(Tile tile, int row) {
        for (int col = 0; col < 5; col++) {
            if (wallPattern[row][col] == tile.getColor() && tiles[row][col] == null) {
                tiles[row][col] = tile;
                break;
            }
        }
    }

    public boolean hasCompletedRow() {
        for (int i = 0; i < 5; i++) {
            boolean rowComplete = true;
            for (int j = 0; j < 5; j++) {
                if (tiles[i][j] == null) {
                    rowComplete = false;
                    break;
                }
            }
            if (rowComplete) {
                return true;
            }
        }
        return false;
    }

    public int calculateScore() {
        int score = 0;
        // TODO: Implement full scoring logic
        // This should include points for adjacent tiles, completed rows, columns, and color sets
        return score;
    }
}
