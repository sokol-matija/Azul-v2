package hr.algebra.azul.network;

import java.io.Serializable;

public class GameMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private MessageType type;
    private String playerId;
    private GameAction action;
    private GameState gameState;
    private String chatContent;  // For chat messages

    public GameMessage(MessageType type, String playerId, GameAction action, GameState gameState) {
        this.type = type;
        this.playerId = playerId;
        this.action = action;
        this.gameState = gameState;
    }

    public GameMessage(MessageType type, String playerId, GameAction action, GameState gameState, String chatContent) {
        this(type, playerId, action, gameState);
        this.chatContent = chatContent;
    }

    // Getters and setters
    public MessageType getType() { return type; }
    public void setType(MessageType type) { this.type = type; }

    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }

    public GameAction getAction() { return action; }
    public void setAction(GameAction action) { this.action = action; }

    public GameState getGameState() { return gameState; }
    public void setGameState(GameState gameState) { this.gameState = gameState; }

    public String getChatContent() { return chatContent; }
    public void setChatContent(String chatContent) { this.chatContent = chatContent; }

    @Override
    public String toString() {
        if (type == MessageType.CHAT) {
            return chatContent;
        }
        return "GameMessage{" +
                "type=" + type +
                ", playerId='" + playerId + '\'' +
                ", action=" + action +
                ", gameState=" + gameState +
                '}';
    }
}