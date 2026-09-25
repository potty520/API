package com.example.ingestion.web;

import com.example.ingestion.common.ClientIp;
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
    private final AuditService audit;

    public AuthController(AuthService auth, TokenService tokens, AuditService audit) {
        this.auth = auth;
        this.tokens = tokens;
        this.audit = audit;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        // 免鉴权端点只暴露存活探针所需的最小信息, 运行态指标走 /api/dashboard(需登录)
        return Map.of("ok", true, "time", LocalDateTime.now());
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String ip = ip(request);
        String username = String.valueOf(body.getOrDefault("username", ""));
        try {
            SessionPrincipal principal = auth.login(username, String.valueOf(body.getOrDefault("password", "")), ip);
            audit.record(principal, "登录系统", "系统安全", "登录成功", ip);
            return Map.of("token", tokens.issue(principal), "user", principal,
                    "mustChangePassword", principal.mustChangePassword());
        } catch (com.example.ingestion.common.ApiException error) {
            if (error.getStatus() == 401 || error.getStatus() == 423) {
                audit.record(SessionPrincipal.anonymous(username), "登录失败", "系统安全", error.getMessage(), ip);
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
        // 改密会吊销全部旧会话, 这里为当前用户重新签发, 前端替换本地 token 即可继续使用
        SessionPrincipal refreshed = new SessionPrincipal(user.id(), user.username(), user.displayName(),
                user.role(), user.permissions(), false);
        return Map.of("ok", true, "token", tokens.issue(refreshed), "user", refreshed);
    }

    @PostMapping("/password/reset")
    public Map<String, Object> resetPassword(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        SessionPrincipal user = auth.requireAdmin();
        String username = String.valueOf(body.getOrDefault("username", ""));
        auth.resetPassword(user, username, String.valueOf(body.getOrDefault("newPassword", "")));
        audit.record(user, "重置用户密码", "系统安全", username, ip(request));
        return Map.of("ok", true);
    }

    private String ip(HttpServletRequest request) {
        // 统一走 ClientIp: 只有本机反代场景才采信 X-Forwarded-For, 避免伪造绕过登录锁定
        return ClientIp.of(request);
    }
}
