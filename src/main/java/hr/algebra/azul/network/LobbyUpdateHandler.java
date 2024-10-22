package hr.algebra.azul.network;

import hr.algebra.azul.network.lobby.GameLobby;

public interface LobbyUpdateHandler {
    void onLobbyUpdate(GameLobby lobby);
    void onGameStart(GameLobby lobby, GameState gameState);
    void onLobbyClosed(GameLobby lobby);
}