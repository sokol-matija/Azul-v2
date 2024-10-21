package hr.algebra.azul.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;

class PlayerTest {

    private Player player;

    @BeforeEach
    void setUp() {
        player = new Player("TestPlayer");
    }

    @Test
    void testPlayerInitialization() {
        assertEquals("TestPlayer", player.getName());
        assertEquals(0, player.getScore());
        assertNotNull(player.getPatternLines());
        assertNotNull(player.getWall());
    }

    @Test
    void testAddTilesToPatternLine() {
        List<Tile> tiles = Arrays.asList(new Tile(TileColor.BLUE), new Tile(TileColor.BLUE));
        player.addTilesToPatternLine(tiles, 1);
        assertEquals(2, player.getPatternLines().getLine(1).size());
        assertEquals(TileColor.BLUE, player.getPatternLines().getLine(1).get(0).getColor());
    }

    @Test
    void testTransferTilesToWall() {
        List<Tile> tiles = Arrays.asList(new Tile(TileColor.BLUE), new Tile(TileColor.BLUE));
        player.addTilesToPatternLine(tiles, 2);
        player.transferTilesToWall();
        assertTrue(player.getWall().canPlaceTile(TileColor.BLUE, 1));
    }

    @Test
    void testHasCompletedRow() {
        assertFalse(player.hasCompletedRow());
        // TODO: Add logic to complete a row and test again
    }
}