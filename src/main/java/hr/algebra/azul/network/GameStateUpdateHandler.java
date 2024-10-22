// File: src/main/java/hr/algebra/azul/network/GameStateUpdateHandler.java
package hr.algebra.azul.network;

/**
 * Interface for handling game state updates received from the network.
 */
public interface GameStateUpdateHandler {
    /**
     * Called when a new game state is received from the server.
     * @param newState The updated game state
     */
    void onGameStateUpdate(GameState newState);

    /**
     * Called when a game move is received from another player.
     * @param action The game action performed
     */
    void onGameMove(GameAction action);

    /**
     * Called when a chat message is received.
     * @param playerId The ID of the player who sent the message
     * @param message The chat message content
     */
    void onChatMessage(String playerId, String message);

    /**
     * Called when a player joins or leaves the game.
     * @param playerId The ID of the player who joined/left
     * @param joined true if the player joined, false if they left
     */
    void onPlayerStatusChange(String playerId, boolean joined);

    /**
     * Get the current player's ID.
     * @return The current player's ID
     */
    String getCurrentPlayerId();
}

