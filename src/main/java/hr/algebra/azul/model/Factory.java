package hr.algebra.azul.model;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Factory {
    private static final int FACTORY_SIZE = 4;
    private List<Tile> tiles;

    public Factory() {
        this.tiles = new ArrayList<>(FACTORY_SIZE);
    }

    public void fillFactory(List<Tile> newTiles) {
        if (newTiles.size() != FACTORY_SIZE) {
            throw new IllegalArgumentException("Factory must be filled with exactly " + FACTORY_SIZE + " tiles");
        }
        this.tiles.clear();
        this.tiles.addAll(newTiles);
    }

    public List<Tile> takeTiles(TileColor color) {
        List<Tile> takenTiles = tiles.stream()
                .filter(tile -> tile.getColor() == color)
                .collect(Collectors.toList());

        tiles.removeAll(takenTiles);

        return takenTiles;
    }

    public List<Tile> getRemainingTiles() {
        List<Tile> remainingTiles = new ArrayList<>(tiles);
        tiles.clear();
        return remainingTiles;
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
        return "Factory{" +
                "tiles=" + tiles +
                '}';
    }
}