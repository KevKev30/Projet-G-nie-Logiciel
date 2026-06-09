package com.episim.models.entities;

public class User {
    public enum Role {
        ADMIN,
        LAMBDA
    }

    private String username;
    private Role role;

    public User(String username, Role role) {
        this.username = username;
        this.role = role;
    }

    public String getUsername() { return username; }
    public Role getRole() { return role; }
    public boolean isAdmin() { return role == Role.ADMIN; }
}