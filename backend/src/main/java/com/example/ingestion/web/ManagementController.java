package com.example.ingestion.web;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.ingestion.common.ApiException;
import com.example.ingestion.common.Jsons;
import com.example.ingestion.entity.*;
import com.example.ingestion.ingest.JsonAnalyzer;
import com.example.ingestion.ingest.SyncEngine;
import com.example.ingestion.mapper.*;
import com.example.ingestion.scheduler.CronSupport;
import com.example.ingestion.scheduler.TaskSchedulerService;
import com.example.ingestion.security.AuthService;
import com.example.ingestion.security.CryptoService;
import com.example.ingestion.security.SessionPrincipal;
import com.example.ingestion.service.AuditService;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api")
public class ManagementController {
    private final AuthService auth;
    private final AuditService audit;
    private final CryptoService crypto;
    private final Jsons jsons;
    private final SyncEngine engine;
    private final TaskSchedulerService scheduler;
    private final SysUserMapper users;
    private final TaskGroupMapper groups;
    private final DataSourceConfigMapper sources;
    private final InterfaceTaskMapper tasks;
    private final ExecutionRunMapper runs;
    private final SchemaVersionMapper versions;
    private final AlertRecordMapper alerts;
    private final AuditLogMapper audits;
    private final SystemSettingMapper settings;

    public ManagementController(AuthService auth, AuditService audit, CryptoService crypto, Jsons jsons,
                                SyncEngine engine, TaskSchedulerService scheduler, SysUserMapper users,
                                TaskGroupMapper groups, DataSourceConfigMapper sources, InterfaceTaskMapper tasks,
                                ExecutionRunMapper runs, SchemaVersionMapper versions, AlertRecordMapper alerts,
                                AuditLogMapper audits, SystemSettingMapper settings) {
        this.auth = auth; this.audit = audit; this.crypto = crypto; this.jsons = jsons; this.engine = engine;
        this.scheduler = scheduler; this.users = users; this.groups = groups; this.sources = sources;
        this.tasks = tasks; this.runs = runs; this.versions = versions; this.alerts = alerts;
        this.audits = audits; this.settings = settings;
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        auth.require("dashboard");
        List<InterfaceTask> taskList = tasks.selectList(null);
        LocalDateTime start = LocalDate.now().atStartOfDay();
        List<ExecutionRun> todayRuns = runs.selectList(Wrappers.<ExecutionRun>lambdaQuery().ge(ExecutionRun::getStartedAt, start));
        List<AlertRecord> openAlerts = alerts.selectList(Wrappers.<AlertRecord>lambdaQuery().eq(AlertRecord::getStatus, "未处理").orderByDesc(AlertRecord::getId).last("LIMIT 8"));
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", taskList.size());
        stats.put("enabled", taskList.stream().filter(item -> Boolean.TRUE.equals(item.getEnabled())).count());
        stats.put("running", engine.runningTaskIds().size());
        stats.put("failed", taskList.stream().filter(item -> List.of("失败", "Cron无效").contains(item.getStatus())).count());
        stats.put("pendingKey", taskList.stream().filter(item -> "待配置主键".equals(item.getStatus())).count());
        Map<String, Object> today = new LinkedHashMap<>();
        today.put("runs", todayRuns.size());
        today.put("success", todayRuns.stream().filter(item -> "成功".equals(item.getStatus())).count());
        today.put("inserted", sum(todayRuns, ExecutionRun::getInsertedCount));
        today.put("updated", sum(todayRuns, ExecutionRun::getUpdatedCount));
        today.put("failed", sum(todayRuns, ExecutionRun::getFailedCount));
        List<Map<String, Object>> recent = runs.selectList(Wrappers.<ExecutionRun>lambdaQuery().orderByDesc(ExecutionRun::getId).last("LIMIT 10")).stream().map(this::publicRun).toList();
        return Map.of("stats", stats, "today", today, "recent", recent,
                "alerts", openAlerts.stream().map(this::publicAlert).toList(), "runningTaskIds", engine.runningTaskIds(), "generatedAt", LocalDateTime.now());
    }

    @GetMapping("/groups")
    public Map<String, Object> groups() {
        auth.current();
        return Map.of("items", groups.selectList(Wrappers.<TaskGroup>lambdaQuery().orderByAsc(TaskGroup::getName)));
    }

    @PostMapping("/groups")
    public ResponseEntity<Map<String, Object>> createGroup(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        SessionPrincipal user = auth.require("tasks");
        TaskGroup group = new TaskGroup();
        group.setName(required(body, "name", "分组名称"));
        group.setDescription(string(body.get("description")));
        group.setCreatedAt(LocalDateTime.now());
        groups.insert(group);
        audit.record(user, "新增任务分组", "任务配置", group.getName(), ip(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("item", group));
    }

    @GetMapping("/datasources")
    public Map<String, Object> dataSources() {
        SessionPrincipal user = auth.current();
        requireAny(user, "datasources", "tasks");
        return Map.of("items", sources.selectList(Wrappers.<DataSourceConfig>lambdaQuery().orderByAsc(DataSourceConfig::getId)).stream().map(this::publicSource).toList());
    }

    @PostMapping("/datasources")
    public ResponseEntity<Map<String, Object>> createDataSource(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        SessionPrincipal user = auth.require("datasources");
        DataSourceConfig source = normalizeSource(body, null);
        sources.insert(source);
        audit.record(user, "新增数据源", "数据源", source.getCode(), ip(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("item", publicSource(source)));
    }

    @PutMapping("/datasources/{id}")
    public Map<String, Object> updateDataSource(@PathVariable Long id, @RequestBody Map<String, Object> body, HttpServletRequest request) {
        SessionPrincipal user = auth.require("datasources");
        DataSourceConfig current = requireSource(id);
        DataSourceConfig source = normalizeSource(body, current);
        source.setId(id);
        source.setCreatedAt(current.getCreatedAt());
        sources.updateById(source);
        audit.record(user, "修改数据源", "数据源", source.getCode(), ip(request));
        return Map.of("ok", true);
    }

    @PostMapping("/datasources/{id}/test")
    public Map<String, Object> testDataSource(@PathVariable Long id) throws Exception {
        auth.require("datasources");
        DataSourceConfig source = requireSource(id);
        try {
            long duration = engine.testDataSource(id);
            source.setLastTestStatus("成功"); source.setLastTestMessage(duration + "ms"); source.setUpdatedAt(LocalDateTime.now()); sources.updateById(source);
            return Map.of("ok", true, "durationMs", duration);
        } catch (Exception error) {
            source.setLastTestStatus("失败"); source.setLastTestMessage(error.getMessage()); source.setUpdatedAt(LocalDateTime.now()); sources.updateById(source);
            throw error;
        }
    }

    @PostMapping("/cron/validate")
    public ResponseEntity<Map<String, Object>> validateCron(@RequestBody Map<String, Object> body) {
        auth.current();
        try {
            String normalized = CronSupport.normalize(string(body.get("expression")));
            List<LocalDateTime> next = CronSupport.nextRuns(normalized, 3);
            return ResponseEntity.ok(Map.of("valid", true, "expression", normalized, "nextRuns", next));
        } catch (Exception error) {
            return ResponseEntity.unprocessableEntity().body(Map.of("valid", false, "error", error.getMessage(), "nextRuns", List.of()));
        }
    }

    @GetMapping("/tasks")
    public Map<String, Object> taskList(@RequestParam(defaultValue = "") String search, @RequestParam(defaultValue = "") String status) {
        SessionPrincipal user = auth.current();
        requireAny(user, "tasks", "execute", "logs", "data");
        LambdaQueryWrapper<InterfaceTask> query = Wrappers.lambdaQuery();
        if (!search.isBlank()) query.and(q -> q.like(InterfaceTask::getName, search).or().like(InterfaceTask::getCode, search).or().like(InterfaceTask::getUrl, search));
        if (!status.isBlank()) query.eq(InterfaceTask::getStatus, status);
        query.orderByDesc(InterfaceTask::getId);
        return Map.of("items", tasks.selectList(query).stream().map(this::publicTask).toList(), "runningTaskIds", engine.runningTaskIds());
    }

    @PostMapping("/tasks")
    public ResponseEntity<Map<String, Object>> createTask(@RequestBody Map<String, Object> body, HttpServletRequest request) throws Exception {
        SessionPrincipal user = auth.require("tasks");
        InterfaceTask task = normalizeTask(body, null, user.username());
        tasks.insert(task);
        scheduler.schedule(task);
        audit.record(user, "新增接口任务", "任务配置", task.getCode(), ip(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("item", publicTask(task)));
    }

    @GetMapping("/tasks/{id}")
    public Map<String, Object> taskDetail(@PathVariable Long id) {
        auth.current();
        InterfaceTask task = requireTask(id);
        Map<String, Object> item = publicTask(task);
        Map<String, Object> authConfig = jsons.map(crypto.decrypt(task.getAuthJsonEnc()));
        mask(authConfig, "password");
        mask(authConfig, "fixedToken");
        item.put("auth", authConfig);
        return Map.of("item", item);
    }

    @PutMapping("/tasks/{id}")
    public Map<String, Object> updateTask(@PathVariable Long id, @RequestBody Map<String, Object> body, HttpServletRequest request) throws Exception {
        SessionPrincipal user = auth.require("tasks");
        InterfaceTask current = requireTask(id);
        InterfaceTask task = normalizeTask(body, current, current.getCreatedBy());
        task.setId(id); task.setCreatedAt(current.getCreatedAt());
        tasks.updateById(task);
        scheduler.schedule(task);
        audit.record(user, "修改接口任务", "任务配置", task.getCode(), ip(request));
        return Map.of("ok", true);
    }

    @PatchMapping("/tasks/{id}/toggle")
    public Map<String, Object> toggleTask(@PathVariable Long id, @RequestBody Map<String, Object> body, HttpServletRequest request) throws Exception {
        SessionPrincipal user = auth.require("tasks");
        InterfaceTask task = requireTask(id);
        task.setEnabled(bool(body.get("enabled"), false));
        task.setUpdatedAt(LocalDateTime.now());
        tasks.updateById(task);
        scheduler.schedule(task);
        audit.record(user, Boolean.TRUE.equals(task.getEnabled()) ? "启用任务" : "停用任务", "任务配置", task.getCode(), ip(request));
        return Map.of("ok", true);
    }

    @PostMapping("/tasks/{id}/test")
    public Map<String, Object> testTask(@PathVariable Long id, HttpServletRequest request) {
        SessionPrincipal user = auth.current();
        requireAny(user, "tasks", "execute");
        Map<String, Object> result = engine.testInterface(id);
        audit.record(user, "测试接口", "任务执行", requireTask(id).getCode(), ip(request));
        return result;
    }

    @PostMapping("/tasks/{id}/run")
    public Map<String, Object> runTask(@PathVariable Long id, HttpServletRequest request) {
        SessionPrincipal user = auth.require("execute");
        ExecutionRun run = engine.execute(id, new SyncEngine.ExecuteOptions("manual", false, user.username()));
        audit.record(user, "手动执行任务", "任务执行", run.getRunNo(), ip(request));
        return Map.of("item", run);
    }

    @PostMapping("/tasks/{id}/full-refresh")
    public Map<String, Object> fullRefresh(@PathVariable Long id, @RequestBody Map<String, Object> body, HttpServletRequest request) {
        SessionPrincipal user = auth.require("full");
        if (!"FULL REFRESH".equals(body.get("confirmText"))) throw new ApiException(422, "请输入 FULL REFRESH 确认全量重刷");
        ExecutionRun run = engine.execute(id, new SyncEngine.ExecuteOptions("manual-full", true, user.username()));
        audit.record(user, "强制全量重刷", "任务执行", run.getRunNo(), ip(request));
        return Map.of("item", run);
    }

    @GetMapping("/tasks/{id}/data")
    public Map<String, Object> taskData(@PathVariable Long id, @RequestParam(defaultValue = "50") int limit) throws Exception {
        auth.require("data");
        return engine.preview(id, limit);
    }

    @GetMapping("/tasks/{id}/schema")
    public Map<String, Object> schema(@PathVariable Long id) {
        auth.require("data");
        List<Map<String, Object>> items = versions.selectList(Wrappers.<SchemaVersion>lambdaQuery().eq(SchemaVersion::getTaskId, id).orderByDesc(SchemaVersion::getVersionNo)).stream().map(version -> {
            Map<String, Object> map = bean(version);
            map.put("structure", jsons.map(version.getStructureJson()));
            map.put("mapping", jsons.map(version.getMappingJson()));
            return map;
        }).toList();
        return Map.of("items", items);
    }

    @GetMapping("/runs")
    public Map<String, Object> runList(@RequestParam(defaultValue = "0") Long taskId, @RequestParam(defaultValue = "") String status) {
        auth.require("logs");
        LambdaQueryWrapper<ExecutionRun> query = Wrappers.lambdaQuery();
        if (taskId != null && taskId > 0) query.eq(ExecutionRun::getTaskId, taskId);
        if (!status.isBlank()) query.eq(ExecutionRun::getStatus, status);
        query.orderByDesc(ExecutionRun::getId).last("LIMIT 300");
        return Map.of("items", runs.selectList(query).stream().map(this::publicRun).toList());
    }

    @GetMapping("/runs/{id}")
    public Map<String, Object> runDetail(@PathVariable Long id) {
        auth.require("logs");
        ExecutionRun run = runs.selectById(id);
        if (run == null) throw new ApiException(404, "执行记录不存在");
        Map<String, Object> item = publicRun(run);
        item.put("schemaChanges", jsons.mapper().convertValue(jsons.tree(run.getSchemaChanges()), Object.class));
        item.put("detail", jsons.map(run.getDetailJson()));
        return Map.of("item", item);
    }

    @GetMapping("/alerts")
    public Map<String, Object> alertList() {
        auth.require("logs");
        return Map.of("items", alerts.selectList(Wrappers.<AlertRecord>lambdaQuery().orderByDesc(AlertRecord::getId).last("LIMIT 200")).stream().map(this::publicAlert).toList());
    }

    @PatchMapping("/alerts/{id}/resolve")
    public Map<String, Object> resolveAlert(@PathVariable Long id) {
        auth.require("logs");
        AlertRecord alert = alerts.selectById(id);
        if (alert == null) throw new ApiException(404, "告警不存在");
        alert.setStatus("已处理"); alert.setResolvedAt(LocalDateTime.now()); alerts.updateById(alert);
        return Map.of("ok", true);
    }

    @GetMapping("/settings")
    public Map<String, Object> settings() {
        SessionPrincipal user = auth.require("settings");
        List<Map<String, Object>> userList = user.permissions().contains("*") ? users.selectList(Wrappers.<SysUser>lambdaQuery().orderByAsc(SysUser::getId)).stream().map(item -> {
            Map<String, Object> map = bean(item); map.remove("passwordHash"); return map;
        }).toList() : List.of();
        List<AuditLog> auditList = user.permissions().contains("*") ? audits.selectList(Wrappers.<AuditLog>lambdaQuery().orderByDesc(AuditLog::getId).last("LIMIT 100")) : List.of();
        return Map.of("items", settings.selectList(Wrappers.<SystemSetting>lambdaQuery().orderByAsc(SystemSetting::getSettingKey)), "users", userList, "audits", auditList);
    }

    @PutMapping("/settings")
    public Map<String, Object> updateSettings(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        SessionPrincipal user = auth.require("settings");
        Object values = body.get("settings");
        if (!(values instanceof Map<?, ?> map)) throw new ApiException(422, "settings 必须是对象");
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            SystemSetting setting = settings.selectById(String.valueOf(entry.getKey()));
            if (setting == null) continue;
            setting.setSettingValue(String.valueOf(entry.getValue())); setting.setUpdatedAt(LocalDateTime.now()); settings.updateById(setting);
        }
        audit.record(user, "修改系统参数", "系统管理", map.keySet().toString(), ip(request));
        return Map.of("ok", true);
    }

    @GetMapping("/audit/list")
    public Map<String, Object> auditList(@RequestParam(defaultValue = "100") int limit) {
        auth.require("settings");
        int safeLimit = Math.max(1, Math.min(limit <= 0 ? 100 : limit, 500));
        List<Map<String, Object>> items = audits.selectList(Wrappers.<AuditLog>lambdaQuery().orderByDesc(AuditLog::getId).last("LIMIT " + safeLimit)).stream().map(this::bean).toList();
        return Map.of("items", items, "total", audits.selectCount(null));
    }

    @PostMapping("/audit/verify")
    public Map<String, Object> auditVerify() {
        auth.require("settings");
        String broken = audit.verifyChain();
        return Map.of("valid", broken == null, "error", broken == null ? "" : broken);
    }
    @GetMapping(value = "/export/runs", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<byte[]> exportRuns() {
        auth.require("logs");
        List<ExecutionRun> records = runs.selectList(Wrappers.<ExecutionRun>lambdaQuery().orderByDesc(ExecutionRun::getId));
        String[] headers = {"run_no", "task_id", "trigger_type", "sync_mode", "status", "started_at", "ended_at", "duration_ms", "total_count", "inserted_count", "updated_count", "skipped_count", "failed_count", "error_message"};
        StringBuilder csv = new StringBuilder("\uFEFF").append(String.join(",", headers)).append("\r\n");
        for (ExecutionRun run : records) {
            Map<String, Object> row = bean(run);
            for (int i = 0; i < headers.length; i++) {
                if (i > 0) csv.append(',');
                String camel = snakeToCamel(headers[i]);
                csv.append('"').append(String.valueOf(row.getOrDefault(camel, "")).replace("\"", "\"\"")).append('"');
            }
            csv.append("\r\n");
        }
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=execution-runs.csv").body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    private DataSourceConfig normalizeSource(Map<String, Object> body, DataSourceConfig current) {
        String type = required(body, "dbType", "数据库类型").toLowerCase(Locale.ROOT);
        if (!List.of("mysql", "postgres", "sqlserver", "oracle").contains(type)) throw new ApiException(422, "不支持的数据源类型");
        String password = string(body.get("password"));
        LocalDateTime now = LocalDateTime.now();
        DataSourceConfig source = new DataSourceConfig();
        source.setName(required(body, "name", "数据源名称"));
        source.setCode(JsonAnalyzer.snake(required(body, "code", "数据源编码")).toUpperCase(Locale.ROOT));
        source.setDbType(type); source.setHost(string(body.get("host"))); source.setPort(integer(body.get("port"), null));
        source.setDatabaseName(string(body.get("databaseName"))); source.setSchemaName(string(body.get("schemaName")));
        source.setServiceName(string(body.get("serviceName"))); source.setJdbcUrl(string(body.get("jdbcUrl")));
        source.setUsername(string(body.get("username")));
        source.setPasswordEnc(password.isBlank() && current != null ? current.getPasswordEnc() : crypto.encrypt(password));
        source.setOptionsJson(jsons.write(body.get("options") instanceof Map<?, ?> options ? options : Map.of()));
        source.setActive(bool(body.get("active"), true));
        source.setLastTestStatus(current == null ? "未测试" : current.getLastTestStatus());
        source.setLastTestMessage(current == null ? "" : current.getLastTestMessage());
        source.setCreatedAt(current == null ? now : current.getCreatedAt()); source.setUpdatedAt(now);
        if (source.getJdbcUrl().isBlank() && (source.getHost().isBlank() || source.getUsername().isBlank())) throw new ApiException(422, "主机地址和用户名不能为空");
        return source;
    }

    private InterfaceTask normalizeTask(Map<String, Object> body, InterfaceTask current, String createdBy) {
        Long datasourceId = longValue(body.get("datasourceId"), null);
        DataSourceConfig source = datasourceId == null ? null : sources.selectById(datasourceId);
        if (source == null || !Boolean.TRUE.equals(source.getActive())) throw new ApiException(422, "目标数据源不存在或未启用");
        String cron;
        try { cron = CronSupport.normalize(required(body, "cronExpr", "Cron 表达式")); }
        catch (Exception error) { throw new ApiException(422, "Cron 表达式无效: " + error.getMessage()); }
        String url = required(body, "url", "接口 URL");
        try { URI.create(url); } catch (Exception error) { throw new ApiException(422, "接口 URL 不合法"); }
        String method = string(body.getOrDefault("method", "GET")).toUpperCase(Locale.ROOT);
        if (!List.of("GET", "POST").contains(method)) throw new ApiException(422, "请求方式仅支持 GET/POST");
        String authType = string(body.getOrDefault("authType", "none")).toLowerCase(Locale.ROOT);
        if (!List.of("none", "basic", "form", "token").contains(authType)) throw new ApiException(422, "认证方式不合法");
        Map<String, Object> authConfig = body.get("auth") instanceof Map<?, ?> map ? stringMap(map) : current == null ? new LinkedHashMap<>() : jsons.map(crypto.decrypt(current.getAuthJsonEnc()));
        if (current != null) {
            Map<String, Object> previous = jsons.map(crypto.decrypt(current.getAuthJsonEnc()));
            if ("******".equals(authConfig.get("password"))) authConfig.put("password", previous.get("password"));
            if ("******".equals(authConfig.get("fixedToken"))) authConfig.put("fixedToken", previous.get("fixedToken"));
        }
        LocalDateTime now = LocalDateTime.now();
        InterfaceTask task = new InterfaceTask();
        task.setName(required(body, "name", "任务名称"));
        task.setCode(JsonAnalyzer.snake(required(body, "code", "任务编码")).toUpperCase(Locale.ROOT));
        task.setGroupId(longValue(body.get("groupId"), null)); task.setUrl(url); task.setMethod(method);
        task.setDescription(string(body.get("description"))); task.setEnabled(bool(body.get("enabled"), false));
        task.setStatus(current == null ? "未执行" : current.getStatus());
        task.setHeadersJson(jsons.write(body.get("headers") instanceof Map<?, ?> map ? map : Map.of()));
        task.setQueryJson(jsons.write(body.get("query") instanceof Map<?, ?> map ? map : Map.of()));
        task.setBodyJson(jsons.write(body.get("body") instanceof Map<?, ?> map ? map : Map.of()));
        task.setAuthType(authType); task.setAuthJsonEnc(crypto.encrypt(jsons.write(authConfig)));
        task.setRootPath(string(body.get("rootPath"))); task.setDatasourceId(datasourceId);
        String tableName = string(body.get("tableName"));
        task.setTableName(JsonAnalyzer.snake(tableName.isBlank() ? "auto_data_" + task.getCode() : tableName));
        task.setUniqueKey(string(body.get("uniqueKey"))); task.setCronExpr(cron);
        task.setRetryCount(clamp(integer(body.get("retryCount"), 2), 0, 10));
        task.setRetryIntervalSec(clamp(integer(body.get("retryIntervalSec"), 5), 0, 3600));
        task.setTimeoutSec(clamp(integer(body.get("timeoutSec"), 30), 1, 600));
        task.setSourceLabel(string(body.get("sourceLabel")).isBlank() ? url : string(body.get("sourceLabel")));
        task.setNextRunAt(current == null ? null : current.getNextRunAt());
        task.setLastRunAt(current == null ? null : current.getLastRunAt()); task.setLastSuccessAt(current == null ? null : current.getLastSuccessAt());
        task.setCreatedBy(createdBy); task.setCreatedAt(current == null ? now : current.getCreatedAt()); task.setUpdatedAt(now);
        return task;
    }

    private Map<String, Object> publicSource(DataSourceConfig source) {
        Map<String, Object> map = bean(source);
        map.remove("passwordEnc"); map.put("hasPassword", source.getPasswordEnc() != null && !source.getPasswordEnc().isBlank());
        map.put("options", jsons.map(source.getOptionsJson()));
        return map;
    }

    private Map<String, Object> publicTask(InterfaceTask task) {
        Map<String, Object> map = bean(task);
        map.remove("authJsonEnc");
        map.put("headers", jsons.map(task.getHeadersJson())); map.put("query", jsons.map(task.getQueryJson())); map.put("body", jsons.map(task.getBodyJson()));
        TaskGroup group = task.getGroupId() == null ? null : groups.selectById(task.getGroupId());
        DataSourceConfig source = sources.selectById(task.getDatasourceId());
        map.put("groupName", group == null ? "未分组" : group.getName());
        map.put("datasourceName", source == null ? "已删除" : source.getName());
        map.put("datasourceType", source == null ? "" : source.getDbType());
        map.put("runCount", runs.selectCount(Wrappers.<ExecutionRun>lambdaQuery().eq(ExecutionRun::getTaskId, task.getId())));
        LocalDateTime liveNext = scheduler.nextRun(task.getId()); if (liveNext != null) map.put("nextRunAt", liveNext);
        return map;
    }

    private Map<String, Object> publicRun(ExecutionRun run) {
        Map<String, Object> map = bean(run);
        InterfaceTask task = tasks.selectById(run.getTaskId());
        map.put("taskName", task == null ? "已删除" : task.getName()); map.put("taskCode", task == null ? "" : task.getCode());
        return map;
    }

    private Map<String, Object> publicAlert(AlertRecord alert) {
        Map<String, Object> map = bean(alert);
        InterfaceTask task = alert.getTaskId() == null ? null : tasks.selectById(alert.getTaskId());
        map.put("taskName", task == null ? "系统" : task.getName()); map.put("taskCode", task == null ? "" : task.getCode());
        return map;
    }

    private DataSourceConfig requireSource(Long id) { DataSourceConfig source = sources.selectById(id); if (source == null) throw new ApiException(404, "数据源不存在"); return source; }
    private InterfaceTask requireTask(Long id) { InterfaceTask task = tasks.selectById(id); if (task == null) throw new ApiException(404, "任务不存在"); return task; }
    private Map<String, Object> bean(Object value) { return jsons.mapper().convertValue(value, new TypeReference<LinkedHashMap<String, Object>>() {}); }
    private void mask(Map<String, Object> map, String key) { if (map.get(key) != null && !String.valueOf(map.get(key)).isBlank()) map.put(key, "******"); }
    private String required(Map<String, Object> body, String key, String label) { String value = string(body.get(key)).trim(); if (value.isBlank()) throw new ApiException(422, label + "不能为空"); return value; }
    private String string(Object value) { return value == null ? "" : String.valueOf(value); }
    private boolean bool(Object value, boolean fallback) { if (value == null) return fallback; return value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value)); }
    private Integer integer(Object value, Integer fallback) { if (value == null || String.valueOf(value).isBlank()) return fallback; return Integer.parseInt(String.valueOf(value)); }
    private Long longValue(Object value, Long fallback) { if (value == null || String.valueOf(value).isBlank()) return fallback; return Long.parseLong(String.valueOf(value)); }
    private int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    private Map<String, Object> stringMap(Map<?, ?> source) { Map<String, Object> result = new LinkedHashMap<>(); source.forEach((key, value) -> result.put(String.valueOf(key), value)); return result; }
    private String ip(HttpServletRequest request) { String forwarded = request.getHeader("X-Forwarded-For"); return forwarded == null ? request.getRemoteAddr() : forwarded.split(",")[0].trim(); }
    private void requireAny(SessionPrincipal user, String... permissions) { if (user.permissions().contains("*")) return; for (String permission : permissions) if (user.permissions().contains(permission)) return; throw new ApiException(403, "当前角色没有该操作权限"); }
    private long sum(List<ExecutionRun> values, java.util.function.Function<ExecutionRun, Long> field) { return values.stream().map(field).filter(Objects::nonNull).mapToLong(Long::longValue).sum(); }
    private String snakeToCamel(String value) { StringBuilder out = new StringBuilder(); boolean upper = false; for (char c : value.toCharArray()) { if (c == '_') upper = true; else { out.append(upper ? Character.toUpperCase(c) : c); upper = false; } } return out.toString(); }
}
