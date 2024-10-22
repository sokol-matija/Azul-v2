package hr.algebra.azul.network;

import hr.algebra.azul.model.TileColor;
import java.io.Serializable;

public class GameAction implements Serializable {
    private static final long serialVersionUID = 1L;

    private ActionType type;
    private int factoryIndex;
    private TileColor selectedColor;
    private int patternLineIndex;

    public enum ActionType {
        SELECT_TILES,      // Player selects tiles from factory/central
        PLACE_TILES,       // Player places tiles in pattern line
        END_TURN          // Player ends their turn
    }

    // Constructor
    public GameAction(ActionType type, int factoryIndex, TileColor selectedColor, int patternLineIndex) {
        this.type = type;
        this.factoryIndex = factoryIndex;
        this.selectedColor = selectedColor;
        this.patternLineIndex = patternLineIndex;
    }

    // Getters and setters
    public ActionType getType() { return type; }
    public void setType(ActionType type) { this.type = type; }

    public int getFactoryIndex() { return factoryIndex; }
    public void setFactoryIndex(int factoryIndex) { this.factoryIndex = factoryIndex; }

    public TileColor getSelectedColor() { return selectedColor; }
    public void setSelectedColor(TileColor selectedColor) { this.selectedColor = selectedColor; }

    public int getPatternLineIndex() { return patternLineIndex; }
    public void setPatternLineIndex(int patternLineIndex) { this.patternLineIndex = patternLineIndex; }
}