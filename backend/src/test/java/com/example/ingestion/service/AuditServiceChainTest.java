package com.example.ingestion.service;

import com.example.ingestion.common.Hashing;
import com.example.ingestion.entity.AuditLog;
import com.example.ingestion.mapper.AuditLogMapper;
import com.example.ingestion.security.CryptoService;
import com.example.ingestion.security.SessionPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 审计哈希链的追加、校验、篡改检测与重建(含 v1 历史记录升级)。 */
class AuditServiceChainTest {

    private static final SessionPrincipal ADMIN = SessionPrincipal.anonymous("admin");

    private final List<AuditLog> store = new ArrayList<>();
    private AuditService service;

    @BeforeEach
    void setUp() {
        AuditLogMapper mapper = mock(AuditLogMapper.class);
        CryptoService crypto = mock(CryptoService.class);
        // 用确定性哈希代替真实 HMAC: 本测试验证链结构与篡改检测, 不验证密钥派生
        when(crypto.hmacSha256Hex(anyString())).thenAnswer(call -> Hashing.sha256((String) call.getArgument(0)));
        when(mapper.selectLastForUpdate()).thenAnswer(call -> store.isEmpty() ? null : store.get(store.size() - 1));
        when(mapper.insert(any(AuditLog.class))).thenAnswer(call -> {
            AuditLog entry = call.getArgument(0);
            entry.setId((long) (store.size() + 1));
            store.add(entry);
            return 1;
        });
        when(mapper.selectAllOrdered()).thenAnswer(call -> new ArrayList<>(store));
        service = new AuditService(mapper, crypto);
    }

    @Test
    void freshChainVerifiesClean() {
        service.record(ADMIN, "登录", "认证", "登录成功", "127.0.0.1");
        service.record(ADMIN, "新增", "任务", "任务 1", "127.0.0.1");
        service.record(null, "调度", "任务", "任务 1 触发", null);

        assertEquals(3, store.size());
        assertNull(service.verifyChain());
        assertEquals("", store.get(0).getPrevHash());
        assertEquals(store.get(0).getRowHash(), store.get(1).getPrevHash());
        assertEquals(store.get(1).getRowHash(), store.get(2).getPrevHash());
        assertEquals(AuditService.CHAIN_VERSION, store.get(0).getChainVersion().intValue());
        assertEquals("system", store.get(2).getUsername());
        assertEquals("", store.get(2).getIpAddress());
    }

    @Test
    void detectsTamperedContent() {
        service.record(ADMIN, "新增", "任务", "任务 1", "127.0.0.1");
        service.record(ADMIN, "删除", "任务", "任务 2", "127.0.0.1");
        store.get(1).setDetailText("任务 1");

        String broken = service.verifyChain();
        assertNotNull(broken);
        assertTrue(broken.contains("id=2"), broken);
    }

    @Test
    void detectsBrokenLink() {
        service.record(ADMIN, "新增", "任务", "任务 1", "127.0.0.1");
        service.record(ADMIN, "删除", "任务", "任务 2", "127.0.0.1");
        store.get(1).setPrevHash("0000");

        String broken = service.verifyChain();
        assertNotNull(broken);
        assertTrue(broken.contains("prev_hash"), broken);
    }

    @Test
    void rechainRepairsTamperedRows() {
        service.record(ADMIN, "新增", "任务", "任务 1", "127.0.0.1");
        service.record(ADMIN, "删除", "任务", "任务 2", "127.0.0.1");
        service.record(ADMIN, "修改", "任务", "任务 3", "127.0.0.1");
        store.get(1).setDetailText("被改过");
        assertNotNull(service.verifyChain());

        assertEquals(2, service.rechain());
        assertNull(service.verifyChain());
    }

    @Test
    void legacyChainVerifiesAndIsUpgradedByRechain() {
        AuditLog legacy = legacyEntry("", "admin", "登录", "认证", "升级前的历史记录", "127.0.0.1",
                LocalDateTime.of(2024, 1, 1, 8, 0, 0));
        legacy.setId(1L);
        store.add(legacy);
        // v1 记录仍按旧算法校验, 不会被误报为篡改
        assertNull(service.verifyChain());

        service.record(ADMIN, "新增", "任务", "任务 1", "127.0.0.1");
        assertNull(service.verifyChain());
        assertEquals(1, store.get(0).getChainVersion().intValue());

        assertEquals(2, service.rechain());
        assertNull(service.verifyChain());
        assertEquals(AuditService.CHAIN_VERSION, store.get(0).getChainVersion().intValue());
        assertEquals(AuditService.CHAIN_VERSION, store.get(1).getChainVersion().intValue());
    }

    @Test
    void countToleratesNullResult() {
        assertEquals(0L, service.count());
    }

    private static AuditLog legacyEntry(String prevHash, String username, String action, String module,
                                        String detail, String ip, LocalDateTime createdAt) {
        AuditLog entry = new AuditLog();
        entry.setUsername(username);
        entry.setActionName(action);
        entry.setModuleName(module);
        entry.setDetailText(detail);
        entry.setIpAddress(ip);
        entry.setCreatedAt(createdAt);
        entry.setPrevHash(prevHash);
        entry.setChainVersion(1);
        entry.setRowHash(AuditService.legacyChainHash(prevHash, entry));
        return entry;
    }
}
