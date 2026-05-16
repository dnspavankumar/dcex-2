package com.exchange.auth.session;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {

    private final Map<String, Session> activeSessions = new ConcurrentHashMap<>();

    public Session createSession(String userId, String email) {
        // Default session duration of 24 hours
        Session session = new Session(userId, email, 24 * 60 * 60 * 1000L);
        activeSessions.put(session.getSessionId(), session);
        return session;
    }

    public Optional<Session> getSession(String sessionId) {
        Session session = activeSessions.get(sessionId);
        if (session != null) {
            if (session.isExpired()) {
                activeSessions.remove(sessionId);
                return Optional.empty();
            }
            return Optional.of(session);
        }
        return Optional.empty();
    }

    public void invalidateSession(String sessionId) {
        activeSessions.remove(sessionId);
    }
}
