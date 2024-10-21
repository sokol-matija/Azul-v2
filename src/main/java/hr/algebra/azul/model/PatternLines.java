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

    public void addTiles(List<Tile> tiles, int lineIndex) {
        List<Tile> line = lines.get(lineIndex);
        TileColor lineColor = line.isEmpty() ? null : line.get(0).getColor();

        for (Tile tile : tiles) {
            if (line.size() < lineIndex + 1 && (lineColor == null || tile.getColor() == lineColor)) {
                line.add(tile);
                if (lineColor == null) {
                    lineColor = tile.getColor();
                }
            } else {
                // TODO: Handle overflow tiles (they should go to the floor line)
                break;
            }
        }
    }

    public List<Tile> getLine(int index) {
        return lines.get(index);
    }

    public void clearLine(int index) {
        lines.get(index).clear();
    }
}
