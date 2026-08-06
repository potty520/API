package com.example.ingestion.web;

import com.example.ingestion.ingest.SyncEngine;
import com.example.ingestion.scheduler.TaskSchedulerService;
import com.example.ingestion.security.AuthService;
import com.example.ingestion.security.SessionPrincipal;
import com.example.ingestion.security.TokenService;
import com.example.ingestion.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AuthController {
    private final AuthService auth;
    private final TokenService tokens;
    private final TaskSchedulerService scheduler;
    private final SyncEngine engine;
    private final AuditService audit;

    public AuthController(AuthService auth, TokenService tokens, TaskSchedulerService scheduler, SyncEngine engine, AuditService audit) {
        this.auth = auth;
        this.tokens = tokens;
        this.scheduler = scheduler;
        this.engine = engine;
        this.audit = audit;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("ok", true, "time", LocalDateTime.now(), "scheduler", scheduler.globallyEnabled(), "runningTasks", engine.runningTaskIds().size());
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String ip = ip(request);
        String username = String.valueOf(body.getOrDefault("username", ""));
        try {
            SessionPrincipal principal = auth.login(username, String.valueOf(body.getOrDefault("password", "")), ip);
            audit.record(principal, "登录系统", "系统安全", "登录成功", ip);
            return Map.of("token", tokens.issue(principal), "user", principal);
        } catch (com.example.ingestion.common.ApiException error) {
            if (error.getStatus() == 401 || error.getStatus() == 423) {
                audit.record(new SessionPrincipal(null, username, "", "", java.util.List.of()), "登录失败", "系统安全", error.getMessage(), ip);
            }
            throw error;
        }
    }

    @GetMapping("/session")
    public Map<String, Object> session() {
        return Map.of("user", auth.current());
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletRequest request) {
        SessionPrincipal user = auth.current();
        String value = request.getHeader("Authorization");
        if (value != null && value.regionMatches(true, 0, "Bearer ", 0, 7)) tokens.revoke(value.substring(7).trim());
        audit.record(user, "退出登录", "系统安全", user.username(), ip(request));
        return Map.of("ok", true);
    }

    @PostMapping("/password/change")
    public Map<String, Object> changePassword(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        SessionPrincipal user = auth.current();
        auth.changePassword(user, String.valueOf(body.getOrDefault("oldPassword", "")), String.valueOf(body.getOrDefault("newPassword", "")));
        audit.record(user, "修改密码", "系统安全", user.username(), ip(request));
        return Map.of("ok", true);
    }

    @PostMapping("/password/reset")
    public Map<String, Object> resetPassword(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        SessionPrincipal user = auth.require("settings");
        String username = String.valueOf(body.getOrDefault("username", ""));
        auth.resetPassword(user, username, String.valueOf(body.getOrDefault("newPassword", "")));
        audit.record(user, "重置用户密码", "系统安全", username, ip(request));
        return Map.of("ok", true);
    }

    private String ip(HttpServletRequest request) {
        // 服务仅绑定 127.0.0.1，不信任客户端可伪造的 X-Forwarded-For，避免绕过登录锁定
        return request.getRemoteAddr();
    }
}