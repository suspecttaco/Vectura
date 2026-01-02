package com.vectura.server.db;


import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class User {
    private final StringProperty username;
    private final StringProperty homeDir;
    private final BooleanProperty active;

    public User(String username, String homeDir, boolean active) {
        this.username = new SimpleStringProperty(username);
        this.homeDir = new SimpleStringProperty(homeDir);
        this.active = new SimpleBooleanProperty(active);
    }

    // Getters
    public StringProperty usernameProperty() { return username; }
    public StringProperty homeDirProperty() { return homeDir; }
    public BooleanProperty activeProperty() { return active; }
    public String getUsername() { return username.get(); }
    public String getHomeDir() { return homeDir.get(); }
    public boolean isActive() { return active.get(); }
}