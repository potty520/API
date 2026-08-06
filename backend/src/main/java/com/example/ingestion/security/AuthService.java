package com.example.ingestion.security;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.ingestion.common.ApiException;
import com.example.ingestion.entity.SysUser;
import com.example.ingestion.entity.AlertRecord;
import com.example.ingestion.mapper.AlertRecordMapper;
import com.example.ingestion.mapper.SysUserMapper;
import com.example.ingestion.service.AlertNotifier;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {
    private static final Map<String, List<String>> ROLE_PERMISSIONS = Map.of(
            "管理员", List.of("*"),
            "配置员", List.of("dashboard", "datasources", "tasks", "execute", "full", "logs", "data", "settings"),
            "执行员", List.of("dashboard", "execute", "logs", "data"),
            "查看员", List.of("dashboard", "logs", "data")
    );

    private static final int MAX_FAILED_ATTEMPTS = 10;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(30);

    private record FailedAttempts(int count, Instant lockedUntil) {}

    private final Map<String, FailedAttempts> failures = new ConcurrentHashMap<>();
    private final SysUserMapper users;
    private final AlertRecordMapper alerts;
    private final AlertNotifier notifier;
    private final PasswordEncoder encoder;

    public AuthService(SysUserMapper users, AlertRecordMapper alerts, AlertNotifier notifier, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
        this.alerts = alerts;
        this.notifier = notifier;
    }

    public SessionPrincipal login(String username, String password, String ip) {
        String key = (username == null ? "" : username.trim().toLowerCase()) + "|" + (ip == null ? "" : ip);
        FailedAttempts record = failures.get(key);
        if (record != null && record.lockedUntil() != null && record.lockedUntil().isAfter(Instant.now())) {
            throw new ApiException(423, "登录失败次数过多，账号已临时锁定，请 30 分钟后再试");
        }
        SysUser user = users.selectOne(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, username));
        boolean ok = user != null && Boolean.TRUE.equals(user.getActive()) && encoder.matches(password, user.getPasswordHash());
        if (!ok) {
            int count = record == null ? 1 : record.count() + 1;
            boolean newlyLocked = count >= MAX_FAILED_ATTEMPTS && (record == null || record.lockedUntil() == null);
            Instant lockedUntil = count >= MAX_FAILED_ATTEMPTS ? Instant.now().plus(LOCK_DURATION) : null;
            failures.put(key, new FailedAttempts(count, lockedUntil));
            if (newlyLocked) {
                AlertRecord alert = new AlertRecord();
                alert.setLevelName("高危");
                alert.setTitle("账号登录失败触发锁定");
                alert.setMessage("账号 " + username + " 连续失败 " + count + " 次，已临时锁定 30 分钟（来源 " + ip + "）");
                alert.setStatus("未处理");
                alert.setCreatedAt(LocalDateTime.now());
                alerts.insert(alert);
                notifier.notify(alert);
            }
            throw new ApiException(401, "账号或密码错误");
        }
        failures.remove(key);
        return new SessionPrincipal(user.getId(), user.getUsername(), user.getDisplayName(), user.getRoleName(), ROLE_PERMISSIONS.getOrDefault(user.getRoleName(), List.of()));
    }

    public SessionPrincipal current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof SessionPrincipal principal)) {
            throw new ApiException(401, "请先登录或登录已超时");
        }
        return principal;
    }

    public SessionPrincipal require(String permission) {
        SessionPrincipal principal = current();
        if (!principal.permissions().contains("*") && !principal.permissions().contains(permission)) {
            throw new ApiException(403, "当前角色没有该操作权限");
        }
        return principal;
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8 || !password.matches(".*[A-Za-z].*") || !password.matches(".*\\d.*")) {
            throw new ApiException(422, "新密码至少 8 位，且必须同时包含字母和数字");
        }
    }

    public void changePassword(SessionPrincipal principal, String oldPassword, String newPassword) {
        validatePassword(newPassword);
        SysUser user = users.selectById(principal.id());
        if (user == null) throw new ApiException(404, "用户不存在");
        if (!encoder.matches(oldPassword == null ? "" : oldPassword, user.getPasswordHash())) {
            throw new ApiException(422, "原密码不正确");
        }
        user.setPasswordHash(encoder.encode(newPassword));
        user.setUpdatedAt(LocalDateTime.now());
        users.updateById(user);
    }

    public void resetPassword(SessionPrincipal admin, String username, String newPassword) {
        validatePassword(newPassword);
        if (username == null || username.isBlank()) throw new ApiException(422, "用户名不能为空");
        SysUser user = users.selectOne(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, username.trim()));
        if (user == null) throw new ApiException(404, "用户不存在");
        user.setPasswordHash(encoder.encode(newPassword));
        user.setUpdatedAt(LocalDateTime.now());
        users.updateById(user);
    }
}