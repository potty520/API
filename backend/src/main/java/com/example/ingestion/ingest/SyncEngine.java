package com.example.ingestion.ingest;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.ingestion.common.ApiException;
import com.example.ingestion.common.Hashing;
import com.example.ingestion.common.Jsons;
import com.example.ingestion.entity.*;
import com.example.ingestion.mapper.*;
import com.example.ingestion.service.AlertNotifier;
import com.example.ingestion.ingest.ColumnCommenter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class SyncEngine {
    public record ExecuteOptions(String triggerType, boolean fullRefresh, String requestedBy) {}
    private record SyncOutcome(TargetDatabase.IngestStats mainStats, List<Map<String, Object>> schemaChanges,
                               List<String> childNames, boolean existed, boolean hasUniqueKey, long pruned) {}

    private final InterfaceTaskMapper tasks;
    private final DataSourceConfigMapper sources;
    private final ExecutionRunMapper runs;
    private final SchemaVersionMapper versions;
    private final AlertRecordMapper alerts;
    private final SystemSettingMapper settings;
    private final AlertNotifier notifier;
    private final ColumnCommenter commenter;
    private final InterfaceHttpClient http;
    private final JsonAnalyzer analyzer;
    private final TargetDatabaseFactory databases;
    private final Jsons jsons;
    private final Set<Long> running = ConcurrentHashMap.newKeySet();

    public SyncEngine(InterfaceTaskMapper tasks, DataSourceConfigMapper sources, ExecutionRunMapper runs,
                      SchemaVersionMapper versions, AlertRecordMapper alerts, SystemSettingMapper settings,
                      InterfaceHttpClient http, JsonAnalyzer analyzer, TargetDatabaseFactory databases, Jsons jsons, AlertNotifier notifier, ColumnCommenter commenter) {
        this.tasks = tasks;
        this.sources = sources;
        this.runs = runs;
        this.versions = versions;
        this.alerts = alerts;
        this.settings = settings;
        this.http = http;
        this.analyzer = analyzer;
        this.databases = databases;
        this.jsons = jsons;
        this.notifier = notifier;
        this.commenter = commenter;
    }

    public Set<Long> runningTaskIds() {
        return Set.copyOf(running);
    }

    public Map<String, Object> testInterface(Long taskId) {
        InterfaceTask task = requireTask(taskId);
        InterfaceHttpClient.HttpResult response = http.call(task);
        JsonAnalyzer.Extraction extraction = analyzer.extract(response.payload(), task.getRootPath());
        JsonAnalyzer.Analysis analysis = analyzer.analyze(extraction.records().stream().limit(200).toList());
        Map<String, Object> children = new LinkedHashMap<>();
        analysis.childTables().forEach((name, child) -> children.put(name, Map.of("columns", child.columns, "count", child.rows.size())));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("httpStatus", response.status());
        result.put("responseMs", response.durationMs());
        result.put("detectedRoot", extraction.detectedRoot());
        result.put("totalCount", extraction.records().size());
        result.put("columns", analysis.columns());
        result.put("fieldMapping", analysis.fieldMapping());
        result.put("childTables", children);
        result.put("sample", extraction.records().stream().limit(3).toList());
        result.put("rawPreview", response.raw().substring(0, Math.min(50000, response.raw().length())));
        return result;
    }

    public long testDataSource(Long sourceId) throws Exception {
        DataSourceConfig source = sources.selectById(sourceId);
        if (source == null) throw new ApiException(404, "数据源不存在");
        try (TargetDatabase database = databases.create(source)) {
            return database.test();
        }
    }

    public ExecutionRun execute(Long taskId, ExecuteOptions options) {
        if (!running.add(taskId)) throw new ApiException(409, "同一任务正在执行，已阻止重复调度");
        InterfaceTask task = requireTask(taskId);
        String runNo = id("RUN");
        String batchId = id("BATCH");
        LocalDateTime started = LocalDateTime.now();
        ExecutionRun run = initialRun(task, options, runNo, batchId, started);
        runs.insert(run);
        task.setStatus("执行中");
        task.setLastRunAt(started);
        task.setUpdatedAt(started);
        tasks.updateById(task);

        InterfaceHttpClient.HttpResult response = null;
        int attempts = 0;
        try {
            int maxAttempts = Math.max(1, Optional.ofNullable(task.getRetryCount()).orElse(0) + 1);
            RuntimeException last = null;
            while (attempts < maxAttempts) {
                attempts++;
                try {
                    response = http.call(task);
                    break;
                } catch (RuntimeException error) {
                    last = error;
                    if (attempts >= maxAttempts) throw error;
                    TimeUnit.SECONDS.sleep(Math.max(0, Optional.ofNullable(task.getRetryIntervalSec()).orElse(0)));
                }
            }
            if (response == null && last != null) throw last;
            JsonAnalyzer.Extraction extraction = analyzer.extract(response.payload(), task.getRootPath());
            JsonAnalyzer.Analysis analysis = analyzer.analyze(extraction.records());
            if (analysis.rows().isEmpty()) {
                finishEmpty(run, task, response, attempts, extraction.totalRaw(), started);
                return runs.selectById(run.getId());
            }

            DataSourceConfig source = sources.selectById(task.getDatasourceId());
            if (source == null || !Boolean.TRUE.equals(source.getActive())) throw new ApiException(422, "目标数据源不存在或未启用");
            String table = resolveTable(task);
            String configuredKey = analyzer.mapBusinessKey(task.getUniqueKey(), analysis);
            List<String> previousChildren = latestChildNames(taskId);
            int batchSize = settingInt("batch_size", 1000);

            SyncOutcome outcome;
            try (TargetDatabase database = databases.create(source)) {
                InterfaceHttpClient.HttpResult finalResponse = response;
                outcome = database.transaction(connection -> sync(connection, database, task, table, analysis, configuredKey,
                        options.fullRefresh(), batchId, previousChildren, batchSize));
            }

            boolean pendingKey = outcome.existed() && !outcome.hasUniqueKey() && !options.fullRefresh();
            String taskStatus = pendingKey ? "待配置主键" : "成功";
            String warning = pendingKey ? "缺失增量唯一Key，仅按记录哈希追加新数据，不更新、不覆盖历史数据" : "";
            String syncMode = options.fullRefresh() ? "人工全量" : !outcome.existed() ? "首次全量" : outcome.hasUniqueKey() ? "增量" : "安全追加";
            LocalDateTime ended = LocalDateTime.now();
            updateSuccessfulRun(run, response, attempts, analysis, extraction, outcome, syncMode, taskStatus, warning, ended, started);
            task.setStatus(taskStatus);
            task.setLastSuccessAt(ended);
            task.setUpdatedAt(ended);
            tasks.updateById(task);
            saveVersionIfChanged(task, table, batchId, analysis, outcome.schemaChanges());
            if (!warning.isBlank()) createAlert(taskId, run.getId(), "警告", "任务待配置业务主键", warning);
            return runs.selectById(run.getId());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            fail(run, task, response, attempts, started, new RuntimeException("任务重试等待被中断", error));
            throw new ApiException(500, "任务执行被中断");
        } catch (Exception error) {
            fail(run, task, response, attempts, started, error);
            if (error instanceof ApiException api) throw api;
            // 详细原因与堆栈已写入执行记录, 不通过接口回显, 避免带出 SQL / 连接串等内部信息
            log.error("任务 {} 执行失败, runNo={}", taskId, runNo, error);
            throw new ApiException(500, "任务执行失败，详情请查看执行日志 " + runNo);
        } finally {
            running.remove(taskId);
        }
    }

    private SyncOutcome sync(Connection connection, TargetDatabase database, InterfaceTask task, String table,
                             JsonAnalyzer.Analysis analysis, String configuredKey, boolean fullRefresh,
                             String batchId, List<String> previousChildren, int batchSize) throws Exception {
        boolean existed = database.tableExists(connection, table);
        boolean hasKey = configuredKey != null && !configuredKey.isBlank() && analysis.columns().containsKey(configuredKey);
        String effectiveKey = hasKey ? configuredKey : "_record_hash";
        List<Map<String, Object>> changes = new ArrayList<>();
        if (!existed) {
            database.createTable(connection, table, analysis.columns(), effectiveKey, task.getName(), commenter.comments(analysis.columns().keySet()));
            changes.add(change("CREATE_TABLE", table, analysis.columns().keySet()));
        } else {
            // 已存在的表也要保证系统列齐全, 否则缺少 _batch_id/_task_id 会让写入与清理失败
            changes.addAll(evolutionChanges(table, database.evolveSchema(connection, table,
                    withSystemColumns(analysis.columns()), commenter.comments(analysis.columns().keySet()))));
            database.ensureUniqueIndex(connection, table, effectiveKey);
        }

        enrichChildren(analysis, effectiveKey);
        List<String> currentChildren = new ArrayList<>(analysis.childTables().keySet());
        Set<String> allChildNames = new LinkedHashSet<>(previousChildren);
        allChildNames.addAll(currentChildren);
        for (String childName : allChildNames) {
            String childTable = childTable(table, childName);
            JsonAnalyzer.ChildAnalysis child = analysis.childTables().get(childName);
            if (child != null) {
                if (!database.tableExists(connection, childTable)) {
                    database.createTable(connection, childTable, child.columns, "_child_key", task.getName() + "-" + childName, commenter.comments(child.columns.keySet()));
                    changes.add(change("CREATE_CHILD_TABLE", childTable, child.columns.keySet()));
                } else {
                    changes.addAll(evolutionChanges(childTable, database.evolveSchema(connection, childTable,
                            withSystemColumns(child.columns), commenter.comments(child.columns.keySet()))));
                    database.ensureUniqueIndex(connection, childTable, "_child_key");
                }
            }
        }

        TargetDatabase.SyncContext context = new TargetDatabase.SyncContext(LocalDateTime.now(), task.getId(), batchId,
                task.getSourceLabel() == null || task.getSourceLabel().isBlank() ? task.getUrl() : task.getSourceLabel(), batchSize, fullRefresh);
        // 全量刷新不再"先清空表再写入": 写入完成后由 ingest 按批次号清理旧行, 中途失败原数据仍在
        TargetDatabase.IngestStats main = database.ingest(connection, table, analysis.rows(), effectiveKey, context);
        long pruned = main.pruned();
        for (Map.Entry<String, JsonAnalyzer.ChildAnalysis> entry : analysis.childTables().entrySet()) {
            pruned += database.ingest(connection, childTable(table, entry.getKey()), entry.getValue().rows, "_child_key", context).pruned();
        }
        if (fullRefresh) {
            // 本次响应里已经消失的子表, 清掉本任务留下的历史行
            for (String childName : allChildNames) {
                if (currentChildren.contains(childName)) continue;
                String childTable = childTable(table, childName);
                if (database.tableExists(connection, childTable)) pruned += database.pruneStaleRows(connection, childTable, batchId, task.getId());
            }
        }
        return new SyncOutcome(main, changes, currentChildren, existed, hasKey, pruned);
    }

    private void enrichChildren(JsonAnalyzer.Analysis analysis, String effectiveKey) {
        Map<Integer, Map<String, Object>> parents = new HashMap<>();
        for (Map<String, Object> row : analysis.rows()) parents.put(((Number) row.get("_source_index")).intValue(), row);
        for (JsonAnalyzer.ChildAnalysis child : analysis.childTables().values()) {
            Map<String, Integer> occurrences = new HashMap<>();
            for (Map<String, Object> row : child.rows) {
                int parentIndex = ((Number) row.get("_parent_source_index")).intValue();
                Map<String, Object> parent = parents.get(parentIndex);
                String parentKey = String.valueOf(parent.getOrDefault(effectiveKey, parent.get("_record_hash")));
                row.put("_parent_key", parentKey);
                // _child_key 改为内容寻址: 数组换顺序不再被判定成"整批数据都变了", 相同内容按出现次序区分
                Map<String, Object> content = new LinkedHashMap<>(row);
                content.remove("_item_index");
                content.remove("_parent_source_index");
                content.remove("_parent_hash");
                content.remove("_child_key");
                content.remove("_record_hash");
                String contentHash = Hashing.sha256(content);
                int occurrence = occurrences.merge(parentKey + "|" + contentHash, 1, Integer::sum);
                row.put("_child_key", Hashing.sha256(parentKey + ":" + contentHash + ":" + occurrence));
                Map<String, Object> hashFields = new LinkedHashMap<>(row);
                hashFields.remove("_record_hash");
                hashFields.remove("_parent_source_index");
                // _item_index 只是数组下标, 参与哈希会让"顺序变化"被误判成"内容变化"
                hashFields.remove("_item_index");
                row.put("_record_hash", Hashing.sha256(hashFields));
            }
        }
    }

    public Map<String, Object> preview(Long taskId, int limit) throws Exception {
        InterfaceTask task = requireTask(taskId);
        DataSourceConfig source = sources.selectById(task.getDatasourceId());
        if (source == null || !Boolean.TRUE.equals(source.getActive())) throw new ApiException(422, "目标数据源不存在或未启用");
        String table = resolveTable(task);
        try (TargetDatabase database = databases.create(source); Connection connection = database.open()) {
            if (!database.tableExists(connection, table)) return Map.of("table", table, "items", List.of(), "childTables", List.of());
            List<Map<String, Object>> childTables = new ArrayList<>();
            for (String child : latestChildNames(taskId)) {
                String childTable = childTable(table, child);
                if (database.tableExists(connection, childTable)) childTables.add(Map.of("name", child, "table", childTable, "items", database.preview(childTable, limit)));
            }
            return Map.of("table", table, "items", database.preview(table, limit), "childTables", childTables);
        }
    }

    private InterfaceTask requireTask(Long taskId) {
        InterfaceTask task = tasks.selectById(taskId);
        if (task == null) throw new ApiException(404, "任务不存在");
        return task;
    }

    private ExecutionRun initialRun(InterfaceTask task, ExecuteOptions options, String runNo, String batchId, LocalDateTime started) {
        ExecutionRun run = new ExecutionRun();
        run.setRunNo(runNo); run.setTaskId(task.getId()); run.setTriggerType(options.triggerType());
        run.setSyncMode(options.fullRefresh() ? "人工全量" : "智能判定"); run.setStatus("执行中");
        run.setStartedAt(started); run.setDurationMs(0L); run.setResponseMs(0L); run.setAttemptCount(0);
        run.setTotalCount(0L); run.setInsertedCount(0L); run.setUpdatedCount(0L); run.setSkippedCount(0L);
        run.setFailedCount(0L); run.setEmptyCount(0L); run.setBatchId(batchId);
        run.setTargetTable(resolveTable(task)); run.setSchemaChanges("[]");
        run.setRequestedBy(options.requestedBy()); run.setDetailJson("{}");
        return run;
    }

    private void finishEmpty(ExecutionRun run, InterfaceTask task, InterfaceHttpClient.HttpResult response, int attempts, int totalRaw, LocalDateTime started) {
        LocalDateTime ended = LocalDateTime.now();
        run.setStatus("成功"); run.setSyncMode("无数据"); run.setEndedAt(ended); run.setDurationMs(duration(started));
        run.setHttpStatus(response.status()); run.setResponseMs(response.durationMs()); run.setAttemptCount(attempts);
        run.setEmptyCount((long) totalRaw); runs.updateById(run);
        task.setStatus("成功"); task.setLastSuccessAt(ended); task.setUpdatedAt(ended); tasks.updateById(task);
    }

    private void updateSuccessfulRun(ExecutionRun run, InterfaceHttpClient.HttpResult response, int attempts,
                                     JsonAnalyzer.Analysis analysis, JsonAnalyzer.Extraction extraction, SyncOutcome outcome,
                                     String syncMode, String taskStatus, String warning, LocalDateTime ended, LocalDateTime started) {
        run.setStatus(taskStatus.equals("待配置主键") ? "部分成功" : "成功"); run.setSyncMode(syncMode);
        run.setEndedAt(ended); run.setDurationMs(duration(started)); run.setHttpStatus(response.status());
        run.setResponseMs(response.durationMs()); run.setAttemptCount(attempts); run.setTotalCount((long) analysis.rows().size());
        run.setInsertedCount(outcome.mainStats().inserted()); run.setUpdatedCount(outcome.mainStats().updated());
        run.setSkippedCount(outcome.mainStats().skipped()); run.setFailedCount(0L);
        run.setEmptyCount((long) (extraction.totalRaw() - analysis.rows().size())); run.setSchemaChanges(jsons.write(outcome.schemaChanges()));
        run.setErrorMessage(warning);
        run.setDetailJson(jsons.write(Map.of("root", extraction.detectedRoot(), "childTables", outcome.childNames(), "pruned", outcome.pruned())));
        runs.updateById(run);
    }

    private void saveVersionIfChanged(InterfaceTask task, String table, String batchId, JsonAnalyzer.Analysis analysis, List<Map<String, Object>> changes) {
        if (changes.isEmpty()) return;
        SchemaVersion latest = versions.selectOne(Wrappers.<SchemaVersion>lambdaQuery().eq(SchemaVersion::getTaskId, task.getId())
                .eq(SchemaVersion::getTableName, table).orderByDesc(SchemaVersion::getVersionNo).last("LIMIT 1"));
        Map<String, JsonAnalyzer.ColumnDef> structure = new LinkedHashMap<>(analysis.columns());
        TargetDatabase.SYSTEM_COLUMNS.forEach(structure::putIfAbsent);
        SchemaVersion version = new SchemaVersion();
        version.setTaskId(task.getId()); version.setTableName(table); version.setVersionNo(latest == null ? 1 : latest.getVersionNo() + 1);
        version.setStructureJson(jsons.write(structure)); version.setMappingJson(jsons.write(analysis.fieldMapping()));
        version.setBatchId(batchId); version.setCreatedAt(LocalDateTime.now()); versions.insert(version);
    }

    private void fail(ExecutionRun run, InterfaceTask task, InterfaceHttpClient.HttpResult response, int attempts, LocalDateTime started, Exception error) {
        LocalDateTime ended = LocalDateTime.now();
        run.setStatus("失败"); run.setEndedAt(ended); run.setDurationMs(duration(started)); run.setAttemptCount(attempts);
        if (response != null) { run.setHttpStatus(response.status()); run.setResponseMs(response.durationMs()); }
        if (error instanceof InterfaceHttpClient.RemoteCallException remote) { run.setHttpStatus(remote.getHttpStatus()); run.setResponseMs(remote.getDurationMs()); }
        run.setErrorMessage(error.getMessage()); run.setErrorStack(stack(error));
        run.setDetailJson(jsons.write(Map.of("exception", error.getClass().getName()))); runs.updateById(run);
        task.setStatus("失败"); task.setUpdatedAt(ended); tasks.updateById(task);
        String reason = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        if (reason.length() > 1000) reason = reason.substring(0, 1000) + "...";
        createAlert(task.getId(), run.getId(), "错误", "同步任务执行失败", reason);
    }

    private void createAlert(Long taskId, Long runId, String level, String title, String message) {
        AlertRecord alert = new AlertRecord();
        alert.setTaskId(taskId); alert.setRunId(runId); alert.setLevelName(level); alert.setTitle(title);
        alert.setMessage(message); alert.setStatus("未处理"); alert.setCreatedAt(LocalDateTime.now()); alerts.insert(alert);
        notifier.notify(alert);
    }

    private List<String> latestChildNames(Long taskId) {
        ExecutionRun latest = runs.selectOne(Wrappers.<ExecutionRun>lambdaQuery().eq(ExecutionRun::getTaskId, taskId)
                .in(ExecutionRun::getStatus, "成功", "部分成功").orderByDesc(ExecutionRun::getId).last("LIMIT 1"));
        if (latest == null || latest.getDetailJson() == null) return List.of();
        Object children = jsons.map(latest.getDetailJson()).get("childTables");
        if (!(children instanceof List<?> list)) return List.of();
        return list.stream().map(String::valueOf).toList();
    }

    private int settingInt(String key, int fallback) {
        SystemSetting value = settings.selectById(key);
        try { return value == null ? fallback : Integer.parseInt(value.getSettingValue()); } catch (NumberFormatException ignored) { return fallback; }
    }

    private Map<String, Object> change(String action, String table, Collection<String> columns) {
        return Map.of("action", action, "table", table, "columns", List.copyOf(columns));
    }

    private List<Map<String, Object>> evolutionChanges(String table, TargetDatabase.SchemaEvolution evolution) {
        List<Map<String, Object>> changes = new ArrayList<>();
        if (!evolution.added().isEmpty()) changes.add(change("ADD_COLUMNS", table, evolution.added()));
        if (!evolution.widened().isEmpty()) changes.add(change("WIDEN_COLUMNS", table, evolution.widened()));
        return changes;
    }

    private Map<String, JsonAnalyzer.ColumnDef> withSystemColumns(Map<String, JsonAnalyzer.ColumnDef> columns) {
        Map<String, JsonAnalyzer.ColumnDef> all = new LinkedHashMap<>(columns);
        TargetDatabase.SYSTEM_COLUMNS.forEach(all::putIfAbsent);
        return all;
    }

    /** 表名推导要和 execute 保持一致, 否则执行记录/数据预览会指向另一张表。 */
    private String resolveTable(InterfaceTask task) {
        String name = task.getTableName() == null || task.getTableName().isBlank() ? "auto_data_" + task.getCode() : task.getTableName();
        return JsonAnalyzer.snake(name);
    }

    private String childTable(String table, String childName) {
        String value = table + "_" + JsonAnalyzer.snake(childName);
        return value.substring(0, Math.min(60, value.length()));
    }

    private String id(String prefix) {
        return prefix + "-" + Long.toString(System.currentTimeMillis(), 36).toUpperCase(Locale.ROOT) + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
    }

    private long duration(LocalDateTime started) {
        return java.time.Duration.between(started, LocalDateTime.now()).toMillis();
    }

    private String stack(Throwable error) {
        StringWriter buffer = new StringWriter();
        error.printStackTrace(new PrintWriter(buffer));
        return buffer.toString();
    }
}
