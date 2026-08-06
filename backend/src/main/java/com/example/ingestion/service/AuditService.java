package com.example.ingestion.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.ingestion.common.Hashing;
import com.example.ingestion.entity.AuditLog;
import com.example.ingestion.mapper.AuditLogMapper;
import com.example.ingestion.security.SessionPrincipal;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class AuditService {
    private final AuditLogMapper mapper;
    private static final DateTimeFormatter CHAIN_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    public AuditService(AuditLogMapper mapper) {
        this.mapper = mapper;
    }

    public synchronized void record(SessionPrincipal user, String action, String module, String detail, String ip) {
        AuditLog log = new AuditLog();
        log.setUsername(user == null ? "system" : user.username());
        log.setActionName(action);
        log.setModuleName(module);
        log.setDetailText(detail == null ? "" : detail);
        log.setIpAddress(ip == null ? "" : ip);
        // 去掉纳秒: 避免 MySQL DATETIME(0) 四舍五入进位导致哈希链与存储不一致
        log.setCreatedAt(LocalDateTime.now().withNano(0));
        String previous = lastRowHash();
        log.setPrevHash(previous);
        log.setRowHash(chainHash(previous, log));
        mapper.insert(log);
    }

    /** 校验审计链完整性。返回 null 表示完整，否则返回断裂点描述。 */
    public synchronized String verifyChain() {
        List<AuditLog> logs = mapper.selectList(Wrappers.<AuditLog>lambdaQuery().orderByAsc(AuditLog::getId));
        String expectedPrev = "";
        String previousRowHash = "";
        for (AuditLog log : logs) {
            if (log.getPrevHash() == null || !log.getPrevHash().equals(expectedPrev)) {
                return "哈希链断裂于审计记录 id=" + log.getId() + "(prev_hash 不匹配)";
            }
            if (log.getRowHash() == null || !log.getRowHash().equals(chainHash(expectedPrev, log))) {
                return "哈希链断裂于审计记录 id=" + log.getId() + "(row_hash 与内容不符)";
            }
            expectedPrev = log.getRowHash();
            previousRowHash = log.getRowHash();
        }
        return null;
    }

    /** 为存量数据补齐哈希链(启动时调用)。 */
    public synchronized int backfillChain() {
        List<AuditLog> logs = mapper.selectList(Wrappers.<AuditLog>lambdaQuery().orderByAsc(AuditLog::getId));
        int updated = 0;
        String previousRowHash = "";
        for (AuditLog log : logs) {
            boolean hasPrev = log.getPrevHash() != null && !log.getPrevHash().isBlank();
            boolean hasRow = log.getRowHash() != null && !log.getRowHash().isBlank();
            if (hasPrev && hasRow) {
                if (log.getPrevHash().equals(previousRowHash)) {
                    previousRowHash = log.getRowHash();
                    continue;
                }
            }
            log.setPrevHash(previousRowHash);
            log.setRowHash(chainHash(previousRowHash, log));
            mapper.updateById(log);
            previousRowHash = log.getRowHash();
            updated++;
        }
        return updated;
    }

    public static String chainHash(String prevHash, AuditLog log) {
        StringBuilder raw = new StringBuilder();
        raw.append(prevHash == null ? "" : prevHash);
        raw.append('|').append(nz(log.getUsername()));
        raw.append('|').append(nz(log.getActionName()));
        raw.append('|').append(nz(log.getModuleName()));
        raw.append('|').append(nz(log.getDetailText()));
        raw.append('|').append(nz(log.getIpAddress()));
        raw.append('|').append(log.getCreatedAt() == null ? "" : log.getCreatedAt().format(CHAIN_TIME));
        return Hashing.sha256(raw.toString());
    }

    private String lastRowHash() {
        AuditLog last = mapper.selectOne(Wrappers.<AuditLog>lambdaQuery().orderByDesc(AuditLog::getId).last("LIMIT 1"));
        return last == null || last.getRowHash() == null ? "" : last.getRowHash();
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }
}