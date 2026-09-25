package com.example.ingestion.ingest;

import com.example.ingestion.common.Hashing;
import com.example.ingestion.common.Jsons;
import com.example.ingestion.common.JdbcUrlGuard;
import com.example.ingestion.entity.DataSourceConfig;
import com.example.ingestion.security.CryptoService;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;

@Slf4j
public class TargetDatabase implements AutoCloseable {
    public record IngestStats(long inserted, long updated, long skipped, long pruned) {}
    public record SyncContext(LocalDateTime syncTime, Long taskId, String batchId, String source, int batchSize,
                              boolean fullRefresh) {}
    public record ColumnInfo(String typeName, int size) {}
    public record SchemaEvolution(List<String> added, List<String> widened) {
        public boolean isEmpty() { return added.isEmpty() && widened.isEmpty(); }
    }

    /** 单次 IN 查询的 key 数量上限, 避免一次性拼出超长 SQL。 */
    private static final int KEY_LOOKUP_CHUNK = 500;

    /** 字符大对象(TEXT/CLOB 族)的等效长度, 用于判断是否还需要加宽。 */
    private static final int TEXT_SIZE = 100_000;

    private static final Map<String, Integer> NUMERIC_RANK = Map.ofEntries(
            Map.entry("BOOL", 10), Map.entry("BOOLEAN", 10), Map.entry("BIT", 10), Map.entry("TINYINT", 10),
            Map.entry("SMALLINT", 15), Map.entry("INT2", 15),
            Map.entry("INT", 20), Map.entry("INTEGER", 20), Map.entry("INT4", 20), Map.entry("MEDIUMINT", 20),
            Map.entry("BIGINT", 30), Map.entry("INT8", 30),
            Map.entry("DECIMAL", 40), Map.entry("NUMERIC", 40), Map.entry("NUMBER", 40), Map.entry("MONEY", 40),
            Map.entry("FLOAT", 50), Map.entry("REAL", 50), Map.entry("FLOAT4", 50),
            Map.entry("DOUBLE", 50), Map.entry("FLOAT8", 50), Map.entry("BINARY_DOUBLE", 50));

    private static final Map<String, Integer> TEXT_RANK = Map.ofEntries(
            Map.entry("CHAR", 0), Map.entry("BPCHAR", 0), Map.entry("NCHAR", 0), Map.entry("CHARACTER", 0),
            Map.entry("VARCHAR", 0), Map.entry("NVARCHAR", 0), Map.entry("VARCHAR2", 0),
            Map.entry("CHARACTER VARYING", 0), Map.entry("NVARCHAR2", 0),
            Map.entry("TEXT", TEXT_SIZE), Map.entry("TINYTEXT", TEXT_SIZE), Map.entry("MEDIUMTEXT", TEXT_SIZE),
            Map.entry("LONGTEXT", TEXT_SIZE), Map.entry("CLOB", TEXT_SIZE), Map.entry("NCLOB", TEXT_SIZE),
            Map.entry("NTEXT", TEXT_SIZE));

    public static final Map<String, JsonAnalyzer.ColumnDef> SYSTEM_COLUMNS = Map.of(
            "_record_hash", new JsonAnalyzer.ColumnDef("string", 64),
            "_sync_time", new JsonAnalyzer.ColumnDef("datetime", 19),
            "_task_id", new JsonAnalyzer.ColumnDef("bigint", 0),
            "_batch_id", new JsonAnalyzer.ColumnDef("string", 80),
            "_source", new JsonAnalyzer.ColumnDef("string", 255),
            "_updated_at", new JsonAnalyzer.ColumnDef("datetime", 19)
    );

    private final DataSourceConfig profile;
    private final CryptoService crypto;
    private final Jsons jsons;
    private final String dialect;

    public TargetDatabase(DataSourceConfig profile, CryptoService crypto, Jsons jsons) {
        this.profile = profile;
        this.crypto = crypto;
        this.jsons = jsons;
        this.dialect = profile.getDbType().toLowerCase(Locale.ROOT);
        loadDriver();
    }

    public Connection open() throws SQLException {
        Properties properties = new Properties();
        if (profile.getUsername() != null && !profile.getUsername().isBlank()) properties.setProperty("user", profile.getUsername());
        String password = crypto.decrypt(profile.getPasswordEnc());
        if (!password.isBlank()) properties.setProperty("password", password);
        return DriverManager.getConnection(jdbcUrl(), properties);
    }

    public long test() throws SQLException {
        long started = System.nanoTime();
        try (Connection connection = open(); Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(10);
            statement.execute(dialect.equals("oracle") ? "SELECT 1 FROM DUAL" : "SELECT 1");
        }
        return (System.nanoTime() - started) / 1_000_000;
    }

    public <T> T transaction(SqlFunction<Connection, T> work) throws Exception {
        try (Connection connection = open()) {
            boolean oldAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                T result = work.apply(connection);
                connection.commit();
                return result;
            } catch (Exception error) {
                connection.rollback();
                throw error;
            } finally {
                connection.setAutoCommit(oldAutoCommit);
            }
        }
    }

    public boolean tableExists(Connection connection, String table) throws SQLException {
        validIdentifier(table);
        DatabaseMetaData metadata = connection.getMetaData();
        for (String candidate : List.of(table, table.toUpperCase(Locale.ROOT), table.toLowerCase(Locale.ROOT))) {
            try (ResultSet result = metadata.getTables(connection.getCatalog(), schemaPattern(), candidate, new String[]{"TABLE"})) {
                if (result.next()) return true;
            }
        }
        return false;
    }

    /** 读取目标表已有列的类型与长度, 供结构演进判断是否需要加宽。 */
    public Map<String, ColumnInfo> columnInfo(Connection connection, String table) throws SQLException {
        validIdentifier(table);
        Map<String, ColumnInfo> columns = new LinkedHashMap<>();
        DatabaseMetaData metadata = connection.getMetaData();
        for (String candidate : List.of(table, table.toUpperCase(Locale.ROOT), table.toLowerCase(Locale.ROOT))) {
            try (ResultSet result = metadata.getColumns(connection.getCatalog(), schemaPattern(), candidate, null)) {
                while (result.next()) columns.put(result.getString("COLUMN_NAME").toLowerCase(Locale.ROOT), new ColumnInfo(result.getString("TYPE_NAME"), columnSize(result)));
            }
            if (!columns.isEmpty()) break;
        }
        return columns;
    }

    /** 部分驱动对 TEXT/BLOB 会返回超过 int 范围的长度, 这里统一夹到 int 区间。 */
    private static int columnSize(ResultSet result) throws SQLException {
        long size = result.getLong("COLUMN_SIZE");
        if (result.wasNull() || size <= 0) return 0;
        return size > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) size;
    }

    public void createTable(Connection connection, String table, Map<String, JsonAnalyzer.ColumnDef> columns, String uniqueKey,
                            String tableComment, Map<String, String> columnComments) throws SQLException {
        validIdentifier(table);
        validIdentifier(uniqueKey);
        Map<String, JsonAnalyzer.ColumnDef> all = new LinkedHashMap<>(columns);
        SYSTEM_COLUMNS.forEach(all::putIfAbsent);
        List<String> definitions = new ArrayList<>();
        definitions.add(quote("_id") + " " + identitySql());
        all.forEach((name, type) -> {
            validIdentifier(name);
            definitions.add(quote(name) + " " + typeSql(type) + " NULL" + inlineComment(name, columnComments));
        });
        definitions.add("CONSTRAINT " + quote(indexName(table, uniqueKey)) + " UNIQUE (" + quote(uniqueKey) + ")");
        execute(connection, "CREATE TABLE " + quote(table) + " (" + String.join(",", definitions) + ")" + tableCommentSql(tableComment));
        applyTableComments(connection, table, tableComment, columnComments);
    }

    /**
     * 对齐表结构: 补齐缺失列, 并在同族类型内按需加宽(VARCHAR(255)->VARCHAR(500)、INT->BIGINT 等)。
     * 单列加宽失败只告警不中断, 避免权限/锁问题让整批同步失败。
     */
    public SchemaEvolution evolveSchema(Connection connection, String table, Map<String, JsonAnalyzer.ColumnDef> incoming,
                                        Map<String, String> columnComments) throws SQLException {
        Map<String, ColumnInfo> existing = columnInfo(connection, table);
        List<String> added = new ArrayList<>();
        List<String> widened = new ArrayList<>();
        for (Map.Entry<String, JsonAnalyzer.ColumnDef> entry : incoming.entrySet()) {
            String name = entry.getKey();
            validIdentifier(name);
            ColumnInfo current = existing.get(name.toLowerCase(Locale.ROOT));
            if (current == null) {
                execute(connection, "ALTER TABLE " + quote(table) + " ADD " + quote(name) + " " + typeSql(entry.getValue()) + " NULL" + inlineComment(name, columnComments));
                added.add(name);
                continue;
            }
            String alter = widenSql(table, name, current, entry.getValue(), columnComments);
            if (alter == null) continue;
            try {
                execute(connection, alter);
                widened.add(name + "(" + current.typeName() + "->" + typeSql(entry.getValue()) + ")");
            } catch (SQLException error) {
                log.warn("表 {} 列 {} 加宽失败, 已跳过: {}", table, name, error.getMessage());
            }
        }
        applyColumnComments(connection, table, columnComments == null ? Map.of() : columnComments, new java.util.HashSet<>(added));
        return new SchemaEvolution(added, widened);
    }

    /** 只做"同族升秩/加长"的安全加宽, 其余情况返回 null 表示不动。 */
    private String widenSql(String table, String column, ColumnInfo current, JsonAnalyzer.ColumnDef incoming, Map<String, String> columnComments) {
        String existingType = current.typeName() == null ? "" : current.typeName().trim().toUpperCase(Locale.ROOT);
        Integer existingText = TEXT_RANK.get(existingType);
        String incomingType = incoming.type();
        if (incomingType.equals("string") || incomingType.equals("text")) {
            String target = alterTypeSql(table, column, typeSql(incoming), columnComments);
            // 已有列不是字符类型(数字/时间), 说明来源类型漂移, 放宽为字符类型以保住数据
            if (existingText == null) return target;
            int available = existingText > 0 ? existingText : current.size();
            return bucketLength(incoming) > available ? target : null;
        }
        Integer incomingRank = numericRank(incomingType);
        Integer existingRank = NUMERIC_RANK.get(existingType);
        if (incomingRank == null || existingRank == null || incomingRank <= existingRank) return null;
        return alterTypeSql(table, column, typeSql(incoming), columnComments);
    }

    private String alterTypeSql(String table, String column, String targetType, Map<String, String> columnComments) {
        String quoted = quote(column);
        return switch (dialect) {
            // MySQL 的 MODIFY 不写 COMMENT 会丢掉原有列注释, 这里补回
            case "mysql" -> "ALTER TABLE " + quote(table) + " MODIFY " + quoted + " " + targetType + " NULL" + inlineComment(column, columnComments);
            case "postgres" -> "ALTER TABLE " + quote(table) + " ALTER COLUMN " + quoted + " TYPE " + targetType + " USING CAST(" + quoted + " AS " + targetType + ")";
            case "sqlserver" -> "ALTER TABLE " + quote(table) + " ALTER COLUMN " + quoted + " " + targetType + " NULL";
            case "oracle" -> "ALTER TABLE " + quote(table) + " MODIFY (" + quoted + " " + targetType + ")";
            default -> throw new IllegalArgumentException("未知数据库类型");
        };
    }

    private static Integer numericRank(String logicalType) {
        return switch (logicalType) { case "boolean" -> 10; case "integer" -> 20; case "bigint" -> 30; case "decimal" -> 40; default -> null; };
    }

    private static int bucketLength(JsonAnalyzer.ColumnDef definition) {
        if (definition.type().equals("text")) return TEXT_SIZE;
        int length = definition.length();
        if (length <= 255) return 255;
        if (length <= 500) return 500;
        if (length <= 1000) return 1000;
        return TEXT_SIZE;
    }

    public void ensureUniqueIndex(Connection connection, String table, String key) throws SQLException {
        validIdentifier(table);
        validIdentifier(key);
        try (ResultSet indexes = connection.getMetaData().getIndexInfo(connection.getCatalog(), schemaPattern(), table, true, false)) {
            while (indexes.next()) if (key.equalsIgnoreCase(indexes.getString("COLUMN_NAME"))) return;
        }
        execute(connection, "CREATE UNIQUE INDEX " + quote(indexName(table, key)) + " ON " + quote(table) + " (" + quote(key) + ")");
    }

    public IngestStats ingest(Connection connection, String table, List<Map<String, Object>> rows,
                              String uniqueKey, SyncContext context) throws SQLException {
        validIdentifier(table);
        validIdentifier(uniqueKey);
        List<Map<String, Object>> prepared = new ArrayList<>(rows.size());
        Set<Object> keys = new LinkedHashSet<>();
        for (Map<String, Object> raw : rows) {
            Map<String, Object> row = prepare(raw, context);
            Object keyValue = row.get(uniqueKey);
            if (keyValue == null || String.valueOf(keyValue).isBlank()) throw new SQLException("业务唯一Key为空: " + uniqueKey);
            prepared.add(row);
            keys.add(keyValue);
        }
        // 只按本批 Key 回查已有哈希, 避免每次同步都全表扫描
        Map<String, String> existing = loadExisting(connection, table, uniqueKey, new ArrayList<>(keys));

        Map<List<String>, List<Map<String, Object>>> inserts = new LinkedHashMap<>();
        Map<List<String>, List<Map<String, Object>>> updates = new LinkedHashMap<>();
        List<Object> touchedKeys = new ArrayList<>();
        long skipped = 0;
        for (Map<String, Object> row : prepared) {
            Object keyValue = row.get(uniqueKey);
            String normalizedKey = keyText(keyValue);
            String currentHash = existing.get(normalizedKey);
            if (Objects.equals(currentHash, row.get("_record_hash"))) {
                skipped++;
                // 全量刷新时, 未变化的行也要盖上本批批次号, 否则会被清理逻辑误删
                if (context.fullRefresh()) touchedKeys.add(keyValue);
                continue;
            }
            List<String> names = new ArrayList<>(row.keySet());
            if (currentHash == null) inserts.computeIfAbsent(List.copyOf(names), ignored -> new ArrayList<>()).add(row);
            else updates.computeIfAbsent(List.copyOf(names), ignored -> new ArrayList<>()).add(row);
            existing.put(normalizedKey, String.valueOf(row.get("_record_hash")));
        }
        long inserted = executeInsertGroups(connection, table, inserts, context.batchSize());
        long updated = executeUpdateGroups(connection, table, uniqueKey, updates, context.batchSize());
        long pruned = context.fullRefresh() ? touchAndPrune(connection, table, uniqueKey, touchedKeys, context.batchId(), context.taskId()) : 0;
        return new IngestStats(inserted, updated, skipped, pruned);
    }

    /** 分块回查本批 Key 对应的已有记录哈希。 */
    private Map<String, String> loadExisting(Connection connection, String table, String uniqueKey, List<Object> keys) throws SQLException {
        Map<String, String> existing = new HashMap<>();
        if (keys.isEmpty()) return existing;
        String prefix = "SELECT " + quote(uniqueKey) + "," + quote("_record_hash") + " FROM " + quote(table) + " WHERE " + quote(uniqueKey) + " IN (";
        for (int start = 0; start < keys.size(); start += KEY_LOOKUP_CHUNK) {
            List<Object> chunk = keys.subList(start, Math.min(keys.size(), start + KEY_LOOKUP_CHUNK));
            String sql = prefix + String.join(",", Collections.nCopies(chunk.size(), "?")) + ")";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int index = 0; index < chunk.size(); index++) set(statement, index + 1, chunk.get(index));
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) existing.put(keyText(result.getObject(1)), result.getString(2));
                }
            }
        }
        return existing;
    }

    /** 归一化 Key 文本, 让"库里回读的值"与"本批 JSON 的值"能对齐(1.0 与 1、时间格式、二进制等)。 */
    private static String keyText(Object value) {
        if (value == null) return "";
        if (value instanceof BigDecimal decimal) return decimal.stripTrailingZeros().toPlainString();
        if (value instanceof Double || value instanceof Float) {
            double number = ((Number) value).doubleValue();
            if (Double.isNaN(number) || Double.isInfinite(number)) return String.valueOf(number);
            return number == Math.rint(number) ? Long.toString((long) number) : BigDecimal.valueOf(number).stripTrailingZeros().toPlainString();
        }
        if (value instanceof Number number) return number.toString();
        if (value instanceof Timestamp timestamp) return timestamp.toLocalDateTime().toString();
        if (value instanceof java.sql.Date date) return date.toLocalDate().toString();
        if (value instanceof java.sql.Time time) return time.toLocalTime().toString();
        if (value instanceof LocalDateTime dateTime) return dateTime.toString();
        if (value instanceof byte[] bytes) return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        return String.valueOf(value);
    }

    /**
     * 全量刷新收尾: 先给未变化的行补上本批批次号, 再删除本任务遗留的旧行。
     * 相比"先 DELETE 全表再写入", 中途失败不会把已有数据清空。
     */
    private long touchAndPrune(Connection connection, String table, String uniqueKey, List<Object> touchedKeys, String batchId, Long taskId) throws SQLException {
        if (!touchedKeys.isEmpty()) {
            String prefix = "UPDATE " + quote(table) + " SET " + quote("_batch_id") + "=? WHERE " + quote(uniqueKey) + " IN (";
            for (int start = 0; start < touchedKeys.size(); start += KEY_LOOKUP_CHUNK) {
                List<Object> chunk = touchedKeys.subList(start, Math.min(touchedKeys.size(), start + KEY_LOOKUP_CHUNK));
                String sql = prefix + String.join(",", Collections.nCopies(chunk.size(), "?")) + ")";
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, batchId);
                    for (int index = 0; index < chunk.size(); index++) set(statement, index + 2, chunk.get(index));
                    statement.executeUpdate();
                }
            }
        }
        return pruneStaleRows(connection, table, batchId, taskId);
    }

    /** 删除本任务写入、但批次号已不是当前批次的历史行(仅影响本任务, 不动其它任务或人工写入的数据)。 */
    public long pruneStaleRows(Connection connection, String table, String batchId, Long taskId) throws SQLException {
        validIdentifier(table);
        String sql = "DELETE FROM " + quote(table) + " WHERE " + quote("_task_id") + "=? AND ("
                + quote("_batch_id") + " IS NULL OR " + quote("_batch_id") + "<>?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (taskId == null) statement.setNull(1, Types.BIGINT);
            else statement.setLong(1, taskId);
            statement.setString(2, batchId);
            statement.setString(3, batchId);
            return statement.executeUpdate();
        }
    }

    public List<Map<String, Object>> preview(String table, int limit) throws SQLException {
        validIdentifier(table);
        int safeLimit = Math.max(1, Math.min(limit, 200));
        String sql;
        if (dialect.equals("sqlserver")) sql = "SELECT TOP " + safeLimit + " * FROM " + quote(table) + " ORDER BY " + quote("_id") + " DESC";
        else if (dialect.equals("oracle")) sql = "SELECT * FROM " + quote(table) + " ORDER BY " + quote("_id") + " DESC FETCH FIRST " + safeLimit + " ROWS ONLY";
        else sql = "SELECT * FROM " + quote(table) + " ORDER BY " + quote("_id") + " DESC LIMIT " + safeLimit;
        try (Connection connection = open(); Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            List<Map<String, Object>> rows = new ArrayList<>();
            ResultSetMetaData metadata = result.getMetaData();
            while (result.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= metadata.getColumnCount(); i++) row.put(metadata.getColumnLabel(i).toLowerCase(Locale.ROOT), result.getObject(i));
                rows.add(row);
            }
            return rows;
        }
    }

    private long executeInsertGroups(Connection connection, String table, Map<List<String>, List<Map<String, Object>>> groups, int configuredBatch) throws SQLException {
        long count = 0;
        int batchSize = Math.max(1, configuredBatch);
        for (Map.Entry<List<String>, List<Map<String, Object>>> group : groups.entrySet()) {
            List<String> names = group.getKey();
            String sql = "INSERT INTO " + quote(table) + " (" + names.stream().map(this::quote).reduce((a, b) -> a + "," + b).orElse("") + ") VALUES (" + String.join(",", Collections.nCopies(names.size(), "?")) + ")";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                int pending = 0;
                for (Map<String, Object> row : group.getValue()) {
                    bind(statement, names, row);
                    statement.addBatch();
                    pending++;
                    count++;
                    if (pending >= batchSize) { statement.executeBatch(); pending = 0; }
                }
                if (pending > 0) statement.executeBatch();
            }
        }
        return count;
    }

    private long executeUpdateGroups(Connection connection, String table, String uniqueKey,
                                     Map<List<String>, List<Map<String, Object>>> groups, int configuredBatch) throws SQLException {
        long count = 0;
        int batchSize = Math.max(1, configuredBatch);
        for (Map.Entry<List<String>, List<Map<String, Object>>> group : groups.entrySet()) {
            List<String> updateNames = group.getKey().stream().filter(name -> !name.equals(uniqueKey)).toList();
            String assignments = updateNames.stream().map(name -> quote(name) + "=?").reduce((a, b) -> a + "," + b).orElseThrow();
            String sql = "UPDATE " + quote(table) + " SET " + assignments + " WHERE " + quote(uniqueKey) + "=?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                int pending = 0;
                for (Map<String, Object> row : group.getValue()) {
                    bind(statement, updateNames, row);
                    set(statement, updateNames.size() + 1, row.get(uniqueKey));
                    statement.addBatch();
                    pending++;
                    count++;
                    if (pending >= batchSize) { statement.executeBatch(); pending = 0; }
                }
                if (pending > 0) statement.executeBatch();
            }
        }
        return count;
    }

    private Map<String, Object> prepare(Map<String, Object> raw, SyncContext context) {
        Map<String, Object> row = new LinkedHashMap<>(raw);
        row.remove("_source_index");
        row.remove("_parent_source_index");
        row.put("_sync_time", context.syncTime());
        row.put("_task_id", context.taskId());
        row.put("_batch_id", context.batchId());
        row.put("_source", context.source());
        row.put("_updated_at", context.syncTime());
        return row;
    }

    private void bind(PreparedStatement statement, List<String> names, Map<String, Object> row) throws SQLException {
        for (int index = 0; index < names.size(); index++) set(statement, index + 1, row.get(names.get(index)));
    }

    private void set(PreparedStatement statement, int index, Object value) throws SQLException {
        if (value == null) statement.setNull(index, Types.NULL);
        else if (value instanceof LocalDateTime dateTime) statement.setTimestamp(index, Timestamp.valueOf(dateTime));
        else if (value instanceof Boolean bool && !dialect.equals("postgres")) statement.setInt(index, bool ? 1 : 0);
        else if (value instanceof Map<?, ?> || value instanceof List<?>) statement.setString(index, jsons.write(value));
        else statement.setObject(index, value);
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) { statement.execute(sql); }
    }

    private String jdbcUrl() {
        String configured = trim(profile.getJdbcUrl());
        String url = configured.isBlank() ? buildJdbcUrl() : configured;
        // 存量数据源可能写于校验上线之前; optionsJson 也会被拼进 URL, 因此对最终串统一校验
        JdbcUrlGuard.validateUrl(dialect, url);
        JdbcUrlGuard.validateHostParts(profile.getHost(), profile.getPort(), profile.getDatabaseName(),
                profile.getSchemaName(), profile.getServiceName());
        return url;
    }

    private String buildJdbcUrl() {
        String host = trim(profile.getHost());
        String database = trim(profile.getDatabaseName());
        String serviceName = trim(profile.getServiceName());
        int port = profile.getPort() == null ? defaultPort() : profile.getPort();
        return switch (dialect) {
            case "mysql" -> "jdbc:mysql://" + host + ":" + port + "/" + database + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false";
            case "postgres" -> "jdbc:postgresql://" + host + ":" + port + "/" + database;
            case "sqlserver" -> "jdbc:sqlserver://" + host + ":" + port + ";databaseName=" + database + ";encrypt=" + option("encrypt", "false") + ";trustServerCertificate=" + option("trustServerCertificate", "true");
            case "oracle" -> "jdbc:oracle:thin:@//" + host + ":" + port + "/" + (serviceName.isBlank() ? database : serviceName);
            default -> throw new IllegalArgumentException("不支持的数据源类型: " + dialect);
        };
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String option(String key, String fallback) {
        return String.valueOf(jsons.map(profile.getOptionsJson()).getOrDefault(key, fallback));
    }

    private int defaultPort() {
        return switch (dialect) { case "mysql" -> 3306; case "postgres" -> 5432; case "sqlserver" -> 1433; case "oracle" -> 1521; default -> 0; };
    }

    private void loadDriver() {
        String driver = switch (dialect) {
            case "mysql" -> "com.mysql.cj.jdbc.Driver";
            case "postgres" -> "org.postgresql.Driver";
            case "sqlserver" -> "com.microsoft.sqlserver.jdbc.SQLServerDriver";
            case "oracle" -> "oracle.jdbc.OracleDriver";
            default -> throw new IllegalArgumentException("不支持的数据源类型: " + dialect);
        };
        try { Class.forName(driver); } catch (ClassNotFoundException error) { throw new IllegalStateException("数据库驱动未安装: " + driver, error); }
    }

    private String schemaPattern() {
        if (profile.getSchemaName() != null && !profile.getSchemaName().isBlank()) return profile.getSchemaName();
        if (dialect.equals("postgres")) return "public";
        return null;
    }

    private String identitySql() {
        return switch (dialect) {
            case "mysql" -> "BIGINT AUTO_INCREMENT PRIMARY KEY";
            case "postgres" -> "BIGSERIAL PRIMARY KEY";
            case "sqlserver" -> "BIGINT IDENTITY(1,1) PRIMARY KEY";
            case "oracle" -> "NUMBER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY";
            default -> throw new IllegalArgumentException("未知数据库类型");
        };
    }

    private String inlineComment(String column, Map<String, String> comments) {
        if (!dialect.equals("mysql")) return "";
        String comment = comments == null ? null : comments.get(column);
        if (comment == null || comment.isBlank()) return "";
        return " COMMENT '" + escapeComment(comment) + "'";
    }

    private String tableCommentSql(String tableComment) {
        if (!dialect.equals("mysql")) return "";
        if (tableComment == null || tableComment.isBlank()) return "";
        return " COMMENT='" + escapeComment(tableComment) + "'";
    }

    private void applyTableComments(Connection connection, String table, String tableComment, Map<String, String> columnComments) throws SQLException {
        if (tableComment == null || tableComment.isBlank()) return;
        if (dialect.equals("mysql")) return;
        if (dialect.equals("postgres") || dialect.equals("oracle")) {
            execute(connection, "COMMENT ON TABLE " + quote(table) + " IS '" + escapeComment(tableComment) + "'");
        } else if (dialect.equals("sqlserver")) {
            execute(connection, "EXEC sp_addextendedproperty 'MS_Description', N'" + escapeComment(tableComment) + "', 'SCHEMA', dbo, 'TABLE', " + quote(table));
        }
        applyColumnComments(connection, table, columnComments == null ? Map.of() : columnComments, null);
    }

    private void applyColumnComments(Connection connection, String table, Map<String, String> columnComments, java.util.Set<String> only) throws SQLException {
        if (columnComments.isEmpty()) return;
        for (Map.Entry<String, String> entry : columnComments.entrySet()) {
            if (only != null && !only.contains(entry.getKey())) continue;
            if (entry.getValue() == null || entry.getValue().isBlank()) continue;
            validIdentifier(entry.getKey());
            if (dialect.equals("postgres") || dialect.equals("oracle")) {
                execute(connection, "COMMENT ON COLUMN " + quote(table) + "." + quote(entry.getKey()) + " IS '" + escapeComment(entry.getValue()) + "'");
            } else if (dialect.equals("sqlserver")) {
                execute(connection, "EXEC sp_addextendedproperty 'MS_Description', N'" + escapeComment(entry.getValue()) + "', 'SCHEMA', dbo, 'TABLE', " + quote(table) + ", 'COLUMN', " + quote(entry.getKey()));
            }
        }
    }

    private String escapeComment(String text) {
        return text.replace("'", "''");
    }

    public String typeSql(JsonAnalyzer.ColumnDef definition) {
        int length = definition.length() <= 255 ? 255 : definition.length() <= 500 ? 500 : definition.length() <= 1000 ? 1000 : 0;
        return switch (dialect) {
            case "mysql" -> switch (definition.type()) { case "integer" -> "INT"; case "bigint" -> "BIGINT"; case "decimal" -> "DECIMAL(18,6)"; case "datetime" -> "DATETIME"; case "boolean" -> "TINYINT(1)"; case "text" -> "LONGTEXT"; default -> length > 0 ? "VARCHAR(" + length + ")" : "TEXT"; };
            case "postgres" -> switch (definition.type()) { case "integer" -> "INTEGER"; case "bigint" -> "BIGINT"; case "decimal" -> "NUMERIC(18,6)"; case "datetime" -> "TIMESTAMP"; case "boolean" -> "BOOLEAN"; case "text" -> "TEXT"; default -> length > 0 ? "VARCHAR(" + length + ")" : "TEXT"; };
            case "sqlserver" -> switch (definition.type()) { case "integer" -> "INT"; case "bigint" -> "BIGINT"; case "decimal" -> "DECIMAL(18,6)"; case "datetime" -> "DATETIME2"; case "boolean" -> "BIT"; case "text" -> "NVARCHAR(MAX)"; default -> length > 0 ? "NVARCHAR(" + length + ")" : "NVARCHAR(MAX)"; };
            case "oracle" -> switch (definition.type()) { case "integer" -> "NUMBER(10)"; case "bigint" -> "NUMBER(20)"; case "decimal" -> "NUMBER(18,6)"; case "datetime" -> "TIMESTAMP"; case "boolean" -> "NUMBER(1)"; case "text" -> "CLOB"; default -> length > 0 ? "VARCHAR2(" + length + ")" : "CLOB"; };
            default -> throw new IllegalArgumentException("未知数据库类型");
        };
    }

    private String quote(String identifier) {
        validIdentifier(identifier);
        return switch (dialect) { case "mysql" -> "`" + identifier + "`"; case "sqlserver" -> "[" + identifier + "]"; default -> "\"" + identifier + "\""; };
    }

    private String indexName(String table, String key) {
        String value = "ux_" + table + "_" + key;
        return value.substring(0, Math.min(value.length(), dialect.equals("oracle") ? 30 : 60));
    }

    private void validIdentifier(String value) {
        if (value == null || !value.matches("[\\p{L}_][\\p{L}\\p{N}_]{0,127}")) throw new IllegalArgumentException("数据库标识符不合法: " + value);
    }

    @Override
    public void close() {}

    @FunctionalInterface
    public interface SqlFunction<T, R> { R apply(T value) throws Exception; }
}
