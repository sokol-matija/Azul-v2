package hr.algebra.azul.network;

public enum MessageType {
    MOVE,
    CHAT,
    SYNC,
    JOIN,
    LEAVE,
    PLAYER_REMOVED, TURN_RESUMED, TURN_WARNING, TURN_START, SCORE_SYNC, SCORE_RECONCILIATION_RESPONSE, SCORE_RECONCILIATION_REQUEST, SCORE_UPDATE, PLAYER_LEFT, PLAYER_RECONNECTED, PING, PLAYER_DISCONNECTED, NEW_GAME_REQUEST, GAME_END, GAME_RESULT;


}