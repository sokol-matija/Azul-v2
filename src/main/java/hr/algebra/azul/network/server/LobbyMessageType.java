package hr.algebra.azul.network.server;

public enum LobbyMessageType {
    LOBBY_UPDATE,
    PLAYER_JOINED,
    PLAYER_LEFT,
    GAME_START,
    LOBBY_CLOSED,
    ERROR,
    JOIN_SUCCESS,    // Add these new types
    JOIN_FAILED,
    READY_STATE_CHANGED,
    HOST_CHANGED,
    SETTINGS_UPDATED,
    LOBBY_LIST_UPDATE
}