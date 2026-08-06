package com.example.ingestion.ingest;

import com.example.ingestion.common.Hashing;
import com.example.ingestion.common.Jsons;
import com.example.ingestion.entity.DataSourceConfig;
import com.example.ingestion.security.CryptoService;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;

public class TargetDatabase implements AutoCloseable {
    public record IngestStats(long inserted, long updated, long skipped) {}
    public record SyncContext(LocalDateTime syncTime, Long taskId, String batchId, String source, int batchSize) {}

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

    public Map<String, String> columns(Connection connection, String table) throws SQLException {
        validIdentifier(table);
        Map<String, String> columns = new LinkedHashMap<>();
        DatabaseMetaData metadata = connection.getMetaData();
        for (String candidate : List.of(table, table.toUpperCase(Locale.ROOT), table.toLowerCase(Locale.ROOT))) {
            try (ResultSet result = metadata.getColumns(connection.getCatalog(), schemaPattern(), candidate, null)) {
                while (result.next()) columns.put(result.getString("COLUMN_NAME").toLowerCase(Locale.ROOT), result.getString("TYPE_NAME"));
            }
            if (!columns.isEmpty()) break;
        }
        return columns;
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

    public List<String> addMissingColumns(Connection connection, String table, Map<String, JsonAnalyzer.ColumnDef> incoming,
                                            Map<String, String> columnComments) throws SQLException {
        Map<String, String> existing = columns(connection, table);
        List<String> added = new ArrayList<>();
        for (Map.Entry<String, JsonAnalyzer.ColumnDef> entry : incoming.entrySet()) {
            if (existing.containsKey(entry.getKey().toLowerCase(Locale.ROOT))) continue;
            validIdentifier(entry.getKey());
            execute(connection, "ALTER TABLE " + quote(table) + " ADD " + quote(entry.getKey()) + " " + typeSql(entry.getValue()) + " NULL" + inlineComment(entry.getKey(), columnComments));
            added.add(entry.getKey());
        }
        applyColumnComments(connection, table, columnComments == null ? Map.of() : columnComments, new java.util.HashSet<>(added));
        return added;
    }

    public void ensureUniqueIndex(Connection connection, String table, String key) throws SQLException {
        validIdentifier(table);
        validIdentifier(key);
        try (ResultSet indexes = connection.getMetaData().getIndexInfo(connection.getCatalog(), schemaPattern(), table, true, false)) {
            while (indexes.next()) if (key.equalsIgnoreCase(indexes.getString("COLUMN_NAME"))) return;
        }
        execute(connection, "CREATE UNIQUE INDEX " + quote(indexName(table, key)) + " ON " + quote(table) + " (" + quote(key) + ")");
    }

    public void deleteRows(Connection connection, String table) throws SQLException {
        execute(connection, "DELETE FROM " + quote(table));
    }

    public IngestStats ingest(Connection connection, String table, List<Map<String, Object>> rows,
                              String uniqueKey, SyncContext context) throws SQLException {
        validIdentifier(table);
        validIdentifier(uniqueKey);
        Map<String, String> existing = new HashMap<>();
        String select = "SELECT " + quote(uniqueKey) + "," + quote("_record_hash") + " FROM " + quote(table);
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(select)) {
            while (result.next()) existing.put(String.valueOf(result.getObject(1)), result.getString(2));
        }

        Map<List<String>, List<Map<String, Object>>> inserts = new LinkedHashMap<>();
        Map<List<String>, List<Map<String, Object>>> updates = new LinkedHashMap<>();
        long skipped = 0;
        for (Map<String, Object> raw : rows) {
            Map<String, Object> row = prepare(raw, context);
            Object keyValue = row.get(uniqueKey);
            if (keyValue == null || String.valueOf(keyValue).isBlank()) throw new SQLException("业务唯一Key为空: " + uniqueKey);
            String keyText = String.valueOf(keyValue);
            String currentHash = existing.get(keyText);
            if (Objects.equals(currentHash, row.get("_record_hash"))) {
                skipped++;
                continue;
            }
            List<String> names = new ArrayList<>(row.keySet());
            if (currentHash == null) inserts.computeIfAbsent(List.copyOf(names), ignored -> new ArrayList<>()).add(row);
            else updates.computeIfAbsent(List.copyOf(names), ignored -> new ArrayList<>()).add(row);
            existing.put(keyText, String.valueOf(row.get("_record_hash")));
        }
        long inserted = executeInsertGroups(connection, table, inserts, context.batchSize());
        long updated = executeUpdateGroups(connection, table, uniqueKey, updates, context.batchSize());
        return new IngestStats(inserted, updated, skipped);
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
        if (profile.getJdbcUrl() != null && !profile.getJdbcUrl().isBlank()) return profile.getJdbcUrl();
        String host = profile.getHost();
        int port = profile.getPort() == null ? defaultPort() : profile.getPort();
        return switch (dialect) {
            case "mysql" -> "jdbc:mysql://" + host + ":" + port + "/" + profile.getDatabaseName() + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false";
            case "postgres" -> "jdbc:postgresql://" + host + ":" + port + "/" + profile.getDatabaseName();
            case "sqlserver" -> "jdbc:sqlserver://" + host + ":" + port + ";databaseName=" + profile.getDatabaseName() + ";encrypt=" + option("encrypt", "false") + ";trustServerCertificate=" + option("trustServerCertificate", "true");
            case "oracle" -> "jdbc:oracle:thin:@//" + host + ":" + port + "/" + (profile.getServiceName() == null || profile.getServiceName().isBlank() ? profile.getDatabaseName() : profile.getServiceName());
            default -> throw new IllegalArgumentException("不支持的数据源类型: " + dialect);
        };
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
