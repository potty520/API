package com.example.ingestion.security;

import com.example.ingestion.common.ApiException;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class TokenService {
    /** 会话上限, 防止无界增长; 触顶时先清理过期会话。 */
    private static final int MAX_SESSIONS = 20_000;

    private record Session(SessionPrincipal principal, Instant expiresAt) {}
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final Duration ttl;
    private final ScheduledExecutorService sweeper;

    public TokenService(@Value("${app.token-hours:8}") long hours) {
        ttl = Duration.ofHours(hours);
        sweeper = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "token-session-sweeper");
            thread.setDaemon(true);
            return thread;
        });
        sweeper.scheduleWithFixedDelay(this::evictExpired, 10, 10, TimeUnit.MINUTES);
    }

    public String issue(SessionPrincipal principal) {
        if (sessions.size() >= MAX_SESSIONS) {
            evictExpired();
            if (sessions.size() >= MAX_SESSIONS) throw new ApiException(503, "在线会话数已达上限，请稍后重试");
        }
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

    /** 撤销某个用户的全部会话(改密/重置密码/停用账号后必须调用)。 */
    public int revokeUser(Long userId) {
        if (userId == null) return 0;
        List<String> doomed = new ArrayList<>();
        sessions.forEach((token, session) -> {
            if (userId.equals(session.principal().id())) doomed.add(token);
        });
        doomed.forEach(sessions::remove);
        return doomed.size();
    }

    private void evictExpired() {
        Instant now = Instant.now();
        sessions.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    public int activeSessions() {
        return sessions.size();
    }

    public void revokeAll() {
        sessions.clear();
    }

    @PreDestroy
    void shutdown() {
        sweeper.shutdownNow();
    }
}
