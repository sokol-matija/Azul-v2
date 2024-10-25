package hr.algebra.azul.network.server;

public enum LobbyMessageType {
    LOBBY_CREATE,
    LOBBY_UPDATE,
    LOBBY_LIST_UPDATE,
    PLAYER_JOINED,
    PLAYER_LEFT,
    PLAYER_READY,
    GAME_START,
    LOBBY_CLOSED,
    ERROR
}