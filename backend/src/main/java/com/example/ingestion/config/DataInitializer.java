package com.example.ingestion.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.ingestion.common.Jsons;
import com.example.ingestion.entity.*;
import com.example.ingestion.mapper.*;
import com.example.ingestion.security.CryptoService;
import com.example.ingestion.service.AuditService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Order(1)
@Component
public class DataInitializer implements ApplicationRunner {
    private final SysUserMapper users;
    private final TaskGroupMapper groups;
    private final DataSourceConfigMapper sources;
    private final InterfaceTaskMapper tasks;
    private final SystemSettingMapper settings;
    private final PasswordEncoder encoder;
    private final CryptoService crypto;
    private final Jsons jsons;
    private final AuditService audit;
    private final String demoUrl;

    public DataInitializer(SysUserMapper users, TaskGroupMapper groups, DataSourceConfigMapper sources,
                           InterfaceTaskMapper tasks, SystemSettingMapper settings, PasswordEncoder encoder,
                           CryptoService crypto, Jsons jsons, AuditService audit, @Value("${app.demo-url}") String demoUrl) {
        this.users = users;
        this.groups = groups;
        this.sources = sources;
        this.tasks = tasks;
        this.settings = settings;
        this.encoder = encoder;
        this.crypto = crypto;
        this.jsons = jsons;
        this.demoUrl = demoUrl;
        this.audit = audit;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedUsers();
        seedGroups();
        seedSettings();
        DataSourceConfig source = seedSource();
        seedDemoTask(source);
        int backfilled = audit.backfillChain();
        if (backfilled > 0) System.out.println("审计日志哈希链补链完成: " + backfilled + " 条");
    }

    private void seedUsers() {
        if (users.selectCount(null) > 0) return;
        insertUser("admin", "admin123", "系统管理员", "管理员");
        insertUser("config", "config123", "接口配置员", "配置员");
        insertUser("runner", "runner123", "任务执行员", "执行员");
        insertUser("viewer", "viewer123", "数据查看员", "查看员");
    }

    private void insertUser(String username, String password, String displayName, String role) {
        LocalDateTime now = LocalDateTime.now();
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPasswordHash(encoder.encode(password));
        user.setDisplayName(displayName);
        user.setRoleName(role);
        user.setActive(true);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        users.insert(user);
    }

    private void seedGroups() {
        if (groups.selectCount(null) > 0) return;
        insertGroup("生产接口", "生产、订单与执行类 JSON 接口");
        insertGroup("基础资料", "物料、客户和组织等主数据接口");
    }

    private void insertGroup(String name, String description) {
        TaskGroup group = new TaskGroup();
        group.setName(name);
        group.setDescription(description);
        group.setCreatedAt(LocalDateTime.now());
        groups.insert(group);
    }

    private void seedSettings() {
        Map<String, List<String>> defaults = new LinkedHashMap<>();
        defaults.put("default_retry_count", List.of("2", "默认失败重试次数"));
        defaults.put("default_timeout_sec", List.of("30", "默认接口超时秒数"));
        defaults.put("batch_size", List.of("1000", "批量入库分片大小"));
        defaults.put("log_retention_days", List.of("180", "日志保留天数"));
        defaults.put("scheduler_enabled", List.of("true", "全局定时调度开关"));
        defaults.put("alert_webhook_url", List.of("", "告警推送 Webhook 地址(钉钉/企业微信/飞书群机器人)，留空不推送"));
defaults.put("comment_translate_enabled", List.of("false", "字段中文注释: 词典/自定义映射未命中时调用 Ollama 翻译(生产建议关闭)"));
defaults.put("field_comment_map", List.of("{\"wechat\":\"微信\",\"qq\":\"QQ号\",\"weibo\":\"微博\"}", "自定义字段翻译映射(JSON: 字段名->中文注释)"));
defaults.put("ollama_url", List.of("http://127.0.0.1:11434", "Ollama 服务地址"));
defaults.put("ollama_model", List.of("qwen2.5:14b", "Ollama 翻译模型"));
        defaults.forEach((key, value) -> {
            if (settings.selectById(key) != null) return;
            SystemSetting setting = new SystemSetting();
            setting.setSettingKey(key);
            setting.setSettingValue(value.get(0));
            setting.setDescription(value.get(1));
            setting.setUpdatedAt(LocalDateTime.now());
            settings.insert(setting);
        });
    }

    private DataSourceConfig seedSource() {
        DataSourceConfig existing = sources.selectOne(Wrappers.<DataSourceConfig>lambdaQuery().eq(DataSourceConfig::getCode, "LOCAL_MYSQL"));
        if (existing != null) return existing;
        LocalDateTime now = LocalDateTime.now();
        DataSourceConfig source = new DataSourceConfig();
        source.setName("本地 MySQL 数据仓库");
        source.setCode("LOCAL_MYSQL");
        source.setDbType("mysql");
        source.setHost(System.getenv().getOrDefault("TARGET_DB_HOST", "127.0.0.1"));
        source.setPort(Integer.parseInt(System.getenv().getOrDefault("TARGET_DB_PORT", "3307")));
        source.setDatabaseName(System.getenv().getOrDefault("TARGET_DB_NAME", "json_ingestion_target"));
        source.setSchemaName("");
        source.setServiceName("");
        source.setJdbcUrl("");
        source.setUsername(System.getenv().getOrDefault("TARGET_DB_USER", "json_ingestion"));
        source.setPasswordEnc(crypto.encrypt(System.getenv().getOrDefault("TARGET_DB_PASSWORD", "${META_DB_PASSWORD}")));
        source.setOptionsJson(jsons.write(Map.of("minConnections", 0, "maxConnections", 10, "connectTimeoutMs", 10000)));
        source.setActive(true);
        source.setLastTestStatus("未测试");
        source.setLastTestMessage("");
        source.setCreatedAt(now);
        source.setUpdatedAt(now);
        sources.insert(source);
        return source;
    }

    private void seedDemoTask(DataSourceConfig source) {
        InterfaceTask existing = tasks.selectOne(Wrappers.<InterfaceTask>lambdaQuery().eq(InterfaceTask::getCode, "DEMO_ORDERS"));
        if (existing != null) return;
        TaskGroup group = groups.selectOne(Wrappers.<TaskGroup>lambdaQuery().eq(TaskGroup::getName, "生产接口"));
        LocalDateTime now = LocalDateTime.now();
        InterfaceTask task = new InterfaceTask();
        task.setName("演示订单接口");
        task.setCode("DEMO_ORDERS");
        task.setGroupId(group == null ? null : group.getId());
        task.setUrl(demoUrl);
        task.setMethod("GET");
        task.setDescription("内置 JSON 演示接口，包含嵌套对象和明细数组");
        task.setEnabled(false);
        task.setStatus("未执行");
        task.setHeadersJson("{}");
        task.setQueryJson("{}");
        task.setBodyJson("{}");
        task.setAuthType("none");
        task.setAuthJsonEnc("");
        task.setRootPath("data.list");
        task.setDatasourceId(source.getId());
        task.setTableName("demo_orders");
        task.setUniqueKey("id");
        task.setCronExpr("0/30 * * * * ?");
        task.setRetryCount(2);
        task.setRetryIntervalSec(1);
        task.setTimeoutSec(10);
        task.setSourceLabel("内置演示接口");
        task.setCreatedBy("system");
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        tasks.insert(task);
    }
}
