package com.example.ingestion.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.ingestion.common.Hashing;
import com.example.ingestion.entity.AuditLog;
import com.example.ingestion.mapper.AuditLogMapper;
import com.example.ingestion.security.CryptoService;
import com.example.ingestion.security.SessionPrincipal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 审计日志哈希链。
 *
 * <p>v2 起 row_hash 使用 HMAC-SHA256(主密钥派生子密钥, 规范化字段串), 因此:
 * <ul>
 *   <li>只有拿到 data/.secret 才能重算出合法哈希, 单纯的库写权限无法伪造整条链;</li>
 *   <li>字段采用长度前缀编码, 内容里出现分隔符也不会与其它记录规范化成同一个串。</li>
 * </ul>
 *
 * <p>历史 v1 记录仍按旧的 SHA-256(|分隔) 算法校验, 不会被误报为篡改;
 * 执行 POST /api/audit/rechain 可把它们升级到 v2。
 *
 * <p>注意: 补链/重链会抹掉篡改痕迹, 因此绝不在启动时自动执行,
 * 只能由管理员显式调用接口触发, 并且该动作本身也会写入审计。
 */
@Slf4j
@Service
public class AuditService {
    /** 当前链算法版本。 */
    public static final int CHAIN_VERSION = 2;

    private final AuditLogMapper mapper;
    private final CryptoService crypto;
    private static final DateTimeFormatter CHAIN_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    public AuditService(AuditLogMapper mapper, CryptoService crypto) {
        this.mapper = mapper;
        this.crypto = crypto;
    }

    /**
     * 追加一条审计记录。
     * 事务内先 FOR UPDATE 锁住链尾, 再计算 prev_hash, 保证多实例并发写入不会分叉。
     */
    @Transactional
    public void record(SessionPrincipal user, String action, String module, String detail, String ip) {
        AuditLog entry = new AuditLog();
        entry.setUsername(user == null || user.username() == null ? "system" : user.username());
        entry.setActionName(action);
        entry.setModuleName(module);
        entry.setDetailText(detail == null ? "" : detail);
        entry.setIpAddress(ip == null ? "" : ip);
        // 去掉纳秒: 避免 MySQL DATETIME(0) 四舍五入进位导致哈希链与存储不一致
        entry.setCreatedAt(LocalDateTime.now().withNano(0));
        AuditLog tail = mapper.selectLastForUpdate();
        String previous = tail == null || tail.getRowHash() == null ? "" : tail.getRowHash();
        entry.setPrevHash(previous);
        entry.setChainVersion(CHAIN_VERSION);
        entry.setRowHash(crypto.hmacSha256Hex(canonical(previous, entry)));
        mapper.insert(entry);
    }

    /** 校验审计链完整性。返回 null 表示完整, 否则返回断裂点描述。 */
    public String verifyChain() {
        List<AuditLog> entries = mapper.selectList(Wrappers.<AuditLog>lambdaQuery().orderByAsc(AuditLog::getId));
        String expectedPrev = "";
        for (AuditLog entry : entries) {
            String actualPrev = entry.getPrevHash() == null ? "" : entry.getPrevHash();
            if (!actualPrev.equals(expectedPrev)) {
                return "哈希链断裂于审计记录 id=" + entry.getId() + "(prev_hash 不匹配)";
            }
            String expected = hashOf(entry, expectedPrev);
            if (entry.getRowHash() == null || !entry.getRowHash().equals(expected)) {
                return "哈希链断裂于审计记录 id=" + entry.getId() + "(row_hash 与内容不符)";
            }
            expectedPrev = entry.getRowHash();
        }
        return null;
    }

    public long count() {
        Long total = mapper.selectCount(null);
        return total == null ? 0L : total;
    }

    /**
     * 重建哈希链: 重写所有校验不通过的记录, 并把历史 v1 记录升级到 v2。
     * 该操作会抹掉篡改痕迹, 只能由管理员显式触发, 生产环境需双人确认。
     */
    public synchronized int rechain() {
        List<AuditLog> entries = mapper.selectList(Wrappers.<AuditLog>lambdaQuery().orderByAsc(AuditLog::getId));
        int updated = 0;
        String previous = "";
        for (AuditLog entry : entries) {
            String actualPrev = entry.getPrevHash() == null ? "" : entry.getPrevHash();
            boolean intact = actualPrev.equals(previous)
                    && entry.getRowHash() != null
                    && entry.getRowHash().equals(hashOf(entry, previous));
            if (intact && !isLegacy(entry)) {
                previous = entry.getRowHash();
                continue;
            }
            entry.setPrevHash(previous);
            entry.setChainVersion(CHAIN_VERSION);
            entry.setRowHash(crypto.hmacSha256Hex(canonical(previous, entry)));
            mapper.updateById(entry);
            previous = entry.getRowHash();
            updated++;
        }
        if (updated > 0) log.warn("审计哈希链已重建, 重写 {} 条记录; 若非计划内操作请立即排查篡改来源", updated);
        return updated;
    }

    private String hashOf(AuditLog entry, String prevHash) {
        return isLegacy(entry) ? legacyChainHash(prevHash, entry) : crypto.hmacSha256Hex(canonical(prevHash, entry));
    }

    private boolean isLegacy(AuditLog entry) {
        return entry.getChainVersion() == null || entry.getChainVersion() < CHAIN_VERSION;
    }

    /** 长度前缀编码, 保证不同字段组合不会规范化成同一个串。 */
    private static String canonical(String prevHash, AuditLog entry) {
        StringBuilder raw = new StringBuilder("v2|");
        appendField(raw, prevHash);
        appendField(raw, entry.getUsername());
        appendField(raw, entry.getActionName());
        appendField(raw, entry.getModuleName());
        appendField(raw, entry.getDetailText());
        appendField(raw, entry.getIpAddress());
        appendField(raw, entry.getCreatedAt() == null ? "" : entry.getCreatedAt().format(CHAIN_TIME));
        return raw.toString();
    }

    private static void appendField(StringBuilder builder, String value) {
        String text = value == null ? "" : value;
        builder.append(text.length()).append(':').append(text).append(';');
    }

    /** v1 算法, 仅用于校验升级前的历史记录。 */
    public static String legacyChainHash(String prevHash, AuditLog entry) {
        StringBuilder raw = new StringBuilder();
        raw.append(prevHash == null ? "" : prevHash);
        raw.append('|').append(nz(entry.getUsername()));
        raw.append('|').append(nz(entry.getActionName()));
        raw.append('|').append(nz(entry.getModuleName()));
        raw.append('|').append(nz(entry.getDetailText()));
        raw.append('|').append(nz(entry.getIpAddress()));
        raw.append('|').append(entry.getCreatedAt() == null ? "" : entry.getCreatedAt().format(CHAIN_TIME));
        return Hashing.sha256(raw.toString());
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }
}
