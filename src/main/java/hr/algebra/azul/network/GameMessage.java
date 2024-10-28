package hr.algebra.azul.network;

import java.io.Serializable;

public class GameMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private final MessageType type;
    private final String playerId;
    private final GameAction action;
    private final GameState gameState;
    private final String chatContent;

    public GameMessage(MessageType type, String playerId, GameAction action, GameState gameState) {
        this.type = type;
        this.playerId = playerId;
        this.action = action;
        this.gameState = gameState;
        this.chatContent = null;
    }

    public GameMessage(MessageType type, String playerId, String chatContent) {
        this.type = type;
        this.playerId = playerId;
        this.action = null;
        this.gameState = null;
        this.chatContent = chatContent;
    }

    // Getters and setters
    public MessageType getType() { return type; }
    public String getPlayerId() { return playerId; }
    public GameAction getAction() { return action; }
    public GameState getGameState() { return gameState; }
    public String getChatContent() { return chatContent; }

    @Override
    public String toString() {
        if (type == MessageType.CHAT) {
            return chatContent;
        }
        return "GameMessage{" +
                "type=" + type +
                ", User='" + playerId + '\'' +
                ", action=" + action +
                ", gameState=" + gameState +
                '}';
    }
}