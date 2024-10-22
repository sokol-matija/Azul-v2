package hr.algebra.azul;

import hr.algebra.azul.network.GameServer;
import java.util.logging.Logger;

public class LocalGameServer {
    private static final Logger LOGGER = Logger.getLogger(LocalGameServer.class.getName());

    public static void main(String[] args) {
        GameServer server = new GameServer();
        LOGGER.info("Starting local game server...");
        server.start();
    }
}