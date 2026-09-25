package com.example.ingestion.security;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.ingestion.common.ApiException;
import com.example.ingestion.entity.SysUser;
import com.example.ingestion.entity.AlertRecord;
import com.example.ingestion.mapper.AlertRecordMapper;
import com.example.ingestion.mapper.SysUserMapper;
import com.example.ingestion.service.AlertNotifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
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
    /** 失败计数表容量上限, 防止用随机用户名+IP 灌请求撑爆内存。 */
    private static final int MAX_TRACKED_KEYS = 10_000;

    private record FailedAttempts(int count, Instant lockedUntil, Instant lastAttempt) {}

    private final Map<String, FailedAttempts> failures = new ConcurrentHashMap<>();
    private final SysUserMapper users;
    private final AlertRecordMapper alerts;
    private final AlertNotifier notifier;
    private final PasswordEncoder encoder;
    private final TokenService tokens;
    private final String timingHash;

    public AuthService(SysUserMapper users, AlertRecordMapper alerts, AlertNotifier notifier, PasswordEncoder encoder,
                       TokenService tokens) {
        this.users = users;
        this.encoder = encoder;
        this.alerts = alerts;
        this.notifier = notifier;
        this.tokens = tokens;
        this.timingHash = encoder.encode("timing-equalizer-not-a-real-password");
    }

    public SessionPrincipal login(String username, String password, String ip) {
        String normalized = username == null ? "" : username.trim();
        String key = normalized.toLowerCase() + "|" + (ip == null ? "" : ip);
        Instant now = Instant.now();
        FailedAttempts record = failures.get(key);
        if (record != null && record.lockedUntil() != null) {
            if (record.lockedUntil().isAfter(now)) {
                throw new ApiException(423, "登录失败次数过多，账号已临时锁定，请 30 分钟后再试");
            }
            // 锁定期已过, 重新计数, 避免一次失败即再次锁死 30 分钟
            record = null;
        }
        SysUser user = users.selectOne(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, normalized));
        // 用户不存在时也执行一次哈希比对, 消除用户名枚举的时间差
        String hash = user == null ? timingHash : user.getPasswordHash();
        boolean ok = user != null && Boolean.TRUE.equals(user.getActive()) && encoder.matches(password, hash);
        if (!ok) {
            int count = record == null ? 1 : record.count() + 1;
            boolean newlyLocked = count >= MAX_FAILED_ATTEMPTS;
            Instant lockedUntil = newlyLocked ? now.plus(LOCK_DURATION) : null;
            failures.put(key, new FailedAttempts(count, lockedUntil, now));
            pruneFailures();
            if (newlyLocked) {
                AlertRecord alert = new AlertRecord();
                alert.setLevelName("高危");
                alert.setTitle("账号登录失败触发锁定");
                alert.setMessage("账号 " + normalized + " 连续失败 " + count + " 次，已临时锁定 30 分钟（来源 " + ip + "）");
                alert.setStatus("未处理");
                alert.setCreatedAt(LocalDateTime.now());
                alerts.insert(alert);
                notifier.notify(alert);
            }
            throw new ApiException(401, "账号或密码错误");
        }
        failures.remove(key);
        return new SessionPrincipal(user.getId(), user.getUsername(), user.getDisplayName(), user.getRoleName(),
                ROLE_PERMISSIONS.getOrDefault(user.getRoleName(), List.of()),
                Boolean.TRUE.equals(user.getMustChangePassword()));
    }

    /** 失败计数表淘汰: 先清过期锁, 仍超限则按最后尝试时间淘汰最早的一半, 保证内存有界。 */
    private void pruneFailures() {
        if (failures.size() < MAX_TRACKED_KEYS) return;
        Instant cutoff = Instant.now().minus(LOCK_DURATION);
        failures.entrySet().removeIf(entry -> entry.getValue().lastAttempt().isBefore(cutoff));
        if (failures.size() < MAX_TRACKED_KEYS) return;
        List<String> oldest = failures.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.comparing(FailedAttempts::lastAttempt)))
                .limit(Math.max(1, failures.size() / 2))
                .map(Map.Entry::getKey)
                .toList();
        oldest.forEach(failures::remove);
        log.warn("登录失败计数表达到上限 {}，已淘汰 {} 条最早的记录", MAX_TRACKED_KEYS, oldest.size());
    }

    public SessionPrincipal current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof SessionPrincipal principal)) {
            throw new ApiException(401, "请先登录或登录已超时");
        }
        return principal;
    }

    /** 仅要求已登录且已完成初始改密, 不校验具体权限。 */
    public SessionPrincipal authenticated() {
        SessionPrincipal principal = current();
        rejectIfMustChangePassword(principal);
        return principal;
    }

    /** 校验单个权限; 初始密码未修改时, 除改密接口外一律拒绝。 */
    public SessionPrincipal require(String permission) {
        SessionPrincipal principal = current();
        rejectIfMustChangePassword(principal);
        if (!hasPermission(principal, permission)) throw new ApiException(403, "当前角色没有该操作权限");
        return principal;
    }

    /** 校验任一权限, 语义同 require。 */
    public SessionPrincipal requireAny(String... permissions) {
        SessionPrincipal principal = current();
        rejectIfMustChangePassword(principal);
        for (String permission : permissions) {
            if (hasPermission(principal, permission)) return principal;
        }
        throw new ApiException(403, "当前角色没有该操作权限");
    }

    /** 仅管理员(持有通配权限)可执行; 用于密码重置、审计链、用户清单等高危操作。 */
    public SessionPrincipal requireAdmin() {
        SessionPrincipal principal = current();
        rejectIfMustChangePassword(principal);
        if (!principal.permissions().contains("*")) throw new ApiException(403, "该操作仅管理员可执行");
        return principal;
    }

    private boolean hasPermission(SessionPrincipal principal, String permission) {
        return principal.permissions().contains("*") || principal.permissions().contains(permission);
    }

    private void rejectIfMustChangePassword(SessionPrincipal principal) {
        if (principal.mustChangePassword()) {
            throw new ApiException(403, "MUST_CHANGE_PASSWORD", "初始密码尚未修改，请先修改密码后再操作");
        }
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
        if (encoder.matches(newPassword, user.getPasswordHash())) {
            throw new ApiException(422, "新密码不能与原密码相同");
        }
        user.setPasswordHash(encoder.encode(newPassword));
        user.setMustChangePassword(false);
        user.setUpdatedAt(LocalDateTime.now());
        users.updateById(user);
        // 改密后吊销该用户全部旧会话, 由调用方重新签发当前会话
        tokens.revokeUser(user.getId());
    }

    public void resetPassword(SessionPrincipal admin, String username, String newPassword) {
        if (!admin.permissions().contains("*")) throw new ApiException(403, "该操作仅管理员可执行");
        validatePassword(newPassword);
        String target = username == null ? "" : username.trim();
        if (target.isBlank()) throw new ApiException(422, "用户名不能为空");
        SysUser user = users.selectOne(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, target));
        if (user == null) throw new ApiException(404, "用户不存在");
        if (user.getId() != null && user.getId().equals(admin.id())) {
            throw new ApiException(422, "不能通过重置接口修改自己的密码，请使用修改密码功能");
        }
        user.setPasswordHash(encoder.encode(newPassword));
        user.setMustChangePassword(true);
        user.setUpdatedAt(LocalDateTime.now());
        users.updateById(user);
        tokens.revokeUser(user.getId());
    }
}
