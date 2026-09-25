package com.example.ingestion.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.ingestion.common.Jsons;
import com.example.ingestion.entity.*;
import com.example.ingestion.mapper.*;
import com.example.ingestion.security.CryptoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Order(1)
@Component
public class DataInitializer implements ApplicationRunner {
    private static final String LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String ALPHABET = LETTERS + DIGITS;

    private final SysUserMapper users;
    private final TaskGroupMapper groups;
    private final DataSourceConfigMapper sources;
    private final InterfaceTaskMapper tasks;
    private final SystemSettingMapper settings;
    private final PasswordEncoder encoder;
    private final CryptoService crypto;
    private final Jsons jsons;
    private final String demoUrl;
    private final SecureRandom random = new SecureRandom();

    public DataInitializer(SysUserMapper users, TaskGroupMapper groups, DataSourceConfigMapper sources,
                           InterfaceTaskMapper tasks, SystemSettingMapper settings, PasswordEncoder encoder,
                           CryptoService crypto, Jsons jsons, @Value("${app.demo-url}") String demoUrl) {
        this.users = users;
        this.groups = groups;
        this.sources = sources;
        this.tasks = tasks;
        this.settings = settings;
        this.encoder = encoder;
        this.crypto = crypto;
        this.jsons = jsons;
        this.demoUrl = demoUrl;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedUsers();
        seedGroups();
        seedSettings();
        DataSourceConfig source = seedSource();
        seedDemoTask(source);
    }

    private void seedUsers() {
        if (users.selectCount(null) > 0) return;
        insertUser("admin", "INGESTION_INIT_ADMIN_PASSWORD", "系统管理员", "管理员");
        insertUser("config", "INGESTION_INIT_CONFIG_PASSWORD", "接口配置员", "配置员");
        insertUser("runner", "INGESTION_INIT_RUNNER_PASSWORD", "任务执行员", "执行员");
        insertUser("viewer", "INGESTION_INIT_VIEWER_PASSWORD", "数据查看员", "查看员");
    }

    /**
     * 初始账号不再内置固定口令: 优先取环境变量, 未指定时生成一次性随机密码并只在本次启动日志输出一次。
     * 无论来源如何, 初始账号一律标记为首次登录必须改密。
     */
    private void insertUser(String username, String passwordEnvKey, String displayName, String role) {
        String configured = System.getenv(passwordEnvKey);
        boolean generated = configured == null || configured.isBlank();
        String password = generated ? generatePassword() : configured;
        LocalDateTime now = LocalDateTime.now();
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPasswordHash(encoder.encode(password));
        user.setDisplayName(displayName);
        user.setRoleName(role);
        user.setActive(true);
        user.setMustChangePassword(true);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        users.insert(user);
        if (generated) {
            log.warn("初始账号 {} 未通过环境变量 {} 指定密码, 已生成一次性初始密码: {} (仅此一次输出, 首次登录必须修改)",
                    username, passwordEnvKey, password);
        } else {
            log.info("初始账号 {} 已按 {} 创建, 首次登录必须修改密码", username, passwordEnvKey);
        }
    }

    private String generatePassword() {
        char[] buffer = new char[16];
        for (int index = 0; index < buffer.length; index++) {
            buffer[index] = ALPHABET.charAt(random.nextInt(ALPHABET.length()));
        }
        buffer[0] = LETTERS.charAt(random.nextInt(LETTERS.length()));
        buffer[buffer.length - 1] = DIGITS.charAt(random.nextInt(DIGITS.length()));
        return new String(buffer);
    }

    private String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private int envInt(String key, int fallback) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) return fallback;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException error) {
            log.warn("环境变量 {} 不是合法端口/整数({}), 回落到 {}", key, value, fallback);
            return fallback;
        }
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
        source.setHost(env("TARGET_DB_HOST", "127.0.0.1"));
        source.setPort(envInt("TARGET_DB_PORT", 3306));
        source.setDatabaseName(env("TARGET_DB_NAME", "json_ingestion_target"));
        source.setSchemaName("");
        source.setServiceName("");
        source.setJdbcUrl("");
        source.setUsername(env("TARGET_DB_USER", "json_ingestion"));
        // 注意: System.getenv 不会解析 Spring 占位符, 这里显式回落到 META_DB_PASSWORD 的真实值
        String password = env("TARGET_DB_PASSWORD", env("META_DB_PASSWORD", ""));
        if (password.isBlank()) log.warn("未设置 TARGET_DB_PASSWORD / META_DB_PASSWORD, 种子目标库 LOCAL_MYSQL 将无法连接, 请在数据源页面重新填写密码");
        source.setPasswordEnc(crypto.encrypt(password));
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
        if (!Boolean.parseBoolean(env("INGESTION_SEED_DEMO", "true"))) return;
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
