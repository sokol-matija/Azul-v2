package hr.algebra.azul.model;

import java.io.Serializable;
import java.util.UUID;

public class User implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String id;
    private String username;
    private boolean online;
    private UserStatus status;
    private boolean isReady;

    public User(String username) {
        this.id = UUID.randomUUID().toString();
        this.username = username;
        this.online = true;
        this.status = UserStatus.AVAILABLE;
        this.isReady = false;
    }

    // Getters and setters
    public String getId() { return id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public boolean isOnline() { return online; }
    public void setOnline(boolean online) { this.online = online; }
    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }
    public boolean isReady() { return isReady; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return id.equals(user.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    public void setReady(boolean b) {
        isReady = b;
    }
}