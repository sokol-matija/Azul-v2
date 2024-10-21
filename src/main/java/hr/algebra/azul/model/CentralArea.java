package hr.algebra.azul.model;

import java.util.ArrayList;
import java.util.List;

public class CentralArea {
    private List<Tile> tiles;

    public CentralArea() {
        tiles = new ArrayList<>();
    }

    public void addTiles(List<Tile> newTiles) {
        tiles.addAll(newTiles);
    }

    public List<Tile> takeTiles(TileColor color) {
        List<Tile> takenTiles = new ArrayList<>();
        tiles.removeIf(tile -> {
            if (tile.getColor() == color) {
                takenTiles.add(tile);
                return true;
            }
            return false;
        });
        return takenTiles;
    }

    public boolean isEmpty() {
        return tiles.isEmpty();
    }

    public List<Tile> getTiles() {
        return new ArrayList<>(tiles); // Return a copy to prevent external modifications
    }

    public int getSize() {
        return tiles.size();
    }

    @Override
    public String toString() {
        return "CentralArea{" +
                "tiles=" + tiles +
                '}';
    }
}