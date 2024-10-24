package hr.algebra.azul.network;

import hr.algebra.azul.model.Game;

import java.util.ArrayList;
import java.util.List;

public class MultiplayerGameInitializer {
    private final GameClient gameClient;
    private final String playerId;
    private final List<String> playerOrder;
    private volatile boolean isInitialized;

    public MultiplayerGameInitializer(GameClient gameClient, String playerId, GameLobby lobby) {
        this.gameClient = gameClient;
        this.playerId = playerId;
        this.playerOrder = new ArrayList<>(lobby.getPlayers().keySet());
        Collections.sort(this.playerOrder); // Ensure consistent order across clients
    }

    public CompletableFuture<NetworkGameState> initializeGame() {
        CompletableFuture<NetworkGameState> future = new CompletableFuture<>();

        if (isHost()) {
            // Host initializes the game
            Game game = new Game(playerOrder.size());
            game.startGame();

            // Assign players in order
            for (int i = 0; i < playerOrder.size(); i++) {
                String pId = playerOrder.get(i);
                game.getPlayers().get(i).setName(pId);
            }

            GameState gameState = new GameState(game);
            gameState.setCurrentPlayerId(playerOrder.get(0)); // First player starts

            // Broadcast initial state
            GameMessage initMessage = new GameMessage(
                    MessageType.SYNC,
                    playerId,
                    null,
                    gameState
            );
            gameClient.sendGameMessage(initMessage);

            NetworkGameState networkState = new NetworkGameState(gameState);
            future.complete(networkState);
        } else {
            // Non-host players wait for initial state
            gameClient.setGameHandler(new GameStateUpdateHandler() {
                @Override
                public void onGameStateUpdate(GameState state) {
                    if (!isInitialized) {
                        isInitialized = true;
                        NetworkGameState networkState = new NetworkGameState(state);
                        future.complete(networkState);
                    }
                }

                // Other handler methods...
            });
        }

        return future;
    }

    private boolean isHost() {
        return playerOrder.get(0).equals(playerId);
    }
}