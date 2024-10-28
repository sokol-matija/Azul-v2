package hr.algebra.azul.network;

import hr.algebra.azul.model.User;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class UserManager {
    private final Map<String, User> onlineUsers = new ConcurrentHashMap<>();

    public void addUser(User user) {
        onlineUsers.put(user.getId(), user);
    }

    public void removeUser(String userId) {
        onlineUsers.remove(userId);
    }

    public User getUser(String userId) {
        return onlineUsers.get(userId);
    }

    public boolean isUsernameTaken(String username) {
        return onlineUsers.values().stream()
                .anyMatch(user -> user.getUsername().equalsIgnoreCase(username));
    }

    public Map<String, User> getOnlineUsers() {
        return new ConcurrentHashMap<>(onlineUsers);
    }
}