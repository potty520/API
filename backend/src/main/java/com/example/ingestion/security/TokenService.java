package com.example.ingestion.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TokenService {
    private record Session(SessionPrincipal principal, Instant expiresAt) {}
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final Duration ttl;

    public TokenService(@Value("${app.token-hours:8}") long hours) {
        ttl = Duration.ofHours(hours);
    }

    public String issue(SessionPrincipal principal) {
        byte[] bytes = new byte[36];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessions.put(token, new Session(principal, Instant.now().plus(ttl)));
        return token;
    }

    public SessionPrincipal resolve(String token) {
        Session session = sessions.get(token);
        if (session == null) return null;
        if (session.expiresAt().isBefore(Instant.now())) {
            sessions.remove(token);
            return null;
        }
        return session.principal();
    }

    public void revoke(String token) {
        sessions.remove(token);
    }

    public int activeSessions() {
        return sessions.size();
    }

    public void revokeAll() {
        sessions.clear();
    }
}