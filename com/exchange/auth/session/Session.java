package com.exchange.auth.session;

import java.util.UUID;

public class Session {
    private final String sessionId;
    private final String userId;
    private final String email;
    private final long createdAt;
    private final long expiresAt;

    public Session(String userId, String email, long durationMillis) {
        this.sessionId = UUID.randomUUID().toString();
        this.userId = userId;
        this.email = email;
        this.createdAt = System.currentTimeMillis();
        this.expiresAt = this.createdAt + durationMillis;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAt;
    }
}
