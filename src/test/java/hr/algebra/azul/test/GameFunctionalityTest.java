package hr.algebra.azul.test;

import hr.algebra.azul.model.*;
import hr.algebra.azul.network.*;
import org.junit.jupiter.api.*;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

public class GameFunctionalityTest {

    private Game game;
    private GameClient client1;
    private GameClient client2;

    @BeforeEach
    void setup() {
        // Initialize game with 2 players
        game = new Game(2);
        game.startGame();

        // Initialize test clients
        client1 = new GameClient("localhost", NetworkConfig.DEFAULT_PORT);
        client2 = new GameClient("localhost", NetworkConfig.DEFAULT_PORT);
    }

    @Test
    void testBasicGameFlow() {
        // Test initial game state
        assertNotNull(game);
        assertEquals(2, game.getPlayers().size());
        assertFalse(game.isGameEnded());

        // Test factory initialization
        assertFalse(game.getFactories().isEmpty());
        assertEquals(5, game.getFactories().size()); // 2 players * 2 + 1

        // Test turn management
        Player currentPlayer = game.getCurrentPlayer();
        assertNotNull(currentPlayer);

        // Test tile selection
        Factory firstFactory = game.getFactories().get(0);
        TileColor selectedColor = firstFactory.getTiles().get(0).getColor();
        assertTrue(game.takeTurn(currentPlayer, firstFactory, selectedColor, -1));

        // Verify tiles were moved correctly
        assertTrue(firstFactory.isEmpty());
        assertFalse(currentPlayer.getHand().isEmpty());

        // Test tile placement
        assertTrue(currentPlayer.placeTilesFromHand(selectedColor, 0));
        assertTrue(currentPlayer.getHand().isEmpty());
    }

    @Test
    void testNetworkConnection() {
        CompletableFuture<Boolean> client1Connected = client1.connect();
        CompletableFuture<Boolean> client2Connected = client2.connect();

        // Wait for connections
        assertTrue(client1Connected.join());
        assertTrue(client2Connected.join());

        // Test message sending
        GameMessage testMessage = new GameMessage(
                MessageType.CHAT,
                "test_player",
                null,
                null,
                "Test message"
        );

        client1.sendGameMessage(testMessage);

        // Sleep briefly to allow message processing
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Clean up
        client1.disconnect();
        client2.disconnect();
    }

    @Test
    void testScoring() {
        Player player = game.getCurrentPlayer();

        // Place some tiles
        Wall wall = player.getWall();
        wall.placeTile(new Tile(TileColor.BLUE), 0);
        wall.placeTile(new Tile(TileColor.RED), 0);

        // Test scoring
        int score = wall.calculateScore();
        assertTrue(score > 0);

        // Test row completion bonus
    }

    @AfterEach
    void cleanup() {
        if (client1 != null) client1.disconnect();
        if (client2 != null) client2.disconnect();
    }
}
