package hr.algebra.azul.network;

import hr.algebra.azul.network.server.ClientHandler;
import hr.algebra.azul.network.server.LobbyManager;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

public class GameServer {
    private static final Logger LOGGER = Logger.getLogger(GameServer.class.getName());
    private static final int PORT = 5000;

    private ServerSocket serverSocket;
    private final List<ClientHandler> clients = new CopyOnWriteArrayList<>();
    private final LobbyManager lobbyManager;
    private volatile boolean running = true;

    public GameServer() {
        this.lobbyManager = new LobbyManager();
    }

    public List<ClientHandler> getClients() {
        return new ArrayList<>(clients);
    }

    public void start() {
        try {
            serverSocket = new ServerSocket(PORT);
            LOGGER.info("Game server started on port " + PORT);

            while (running) {
                Socket clientSocket = serverSocket.accept();
                LOGGER.info("New client connected: " + clientSocket.getInetAddress());

                ClientHandler clientHandler = new ClientHandler(clientSocket, this);
                clients.add(clientHandler);
                new Thread(clientHandler).start();
            }
        } catch (IOException e) {
            LOGGER.severe("Server error: " + e.getMessage());
        }
    }

    public void broadcast(GameMessage message, ClientHandler sender) {
        for (ClientHandler client : clients) {
            if (client != sender) {
                client.sendMessage(message);
            }
        }
    }

    public void removeClient(ClientHandler client) {
        clients.remove(client);
        LOGGER.info("Client disconnected. Remaining clients: " + clients.size());
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            for (ClientHandler client : clients) {
                client.stop();
            }
            clients.clear();
        } catch (IOException e) {
            LOGGER.severe("Error stopping server: " + e.getMessage());
        }
    }

    public LobbyManager getLobbyManager() {
        return lobbyManager;
    }

    public int getConnectedClients() {
        return clients.size();
    }
}