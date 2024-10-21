package hr.algebra.azul.model;
import java.util.ArrayList;
import java.util.List;


public class PatternLines {
    private List<List<Tile>> lines;

    public PatternLines() {
        lines = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            lines.add(new ArrayList<>());
        }
    }

    public boolean canAddTiles(TileColor color, int lineIndex) {
        List<Tile> line = lines.get(lineIndex);
        return line.size() < lineIndex + 1 && (line.isEmpty() || line.get(0).getColor() == color);
    }

    public List<Tile> addTiles(List<Tile> tiles, int lineIndex) {
        List<Tile> line = lines.get(lineIndex);
        List<Tile> overflow = new ArrayList<>();
        TileColor lineColor = line.isEmpty() ? tiles.get(0).getColor() : line.get(0).getColor();

        for (Tile tile : tiles) {
            if (line.size() < lineIndex + 1 && tile.getColor() == lineColor) {
                line.add(tile);
            } else {
                overflow.add(tile);
            }
        }

        return overflow;
    }

    public List<Tile> getLine(int index) {
        return lines.get(index);
    }

    public void clearLine(int index) {
        lines.get(index).clear();
    }

}
