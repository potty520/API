package com.example.apisystem.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.apisystem.entity.*;
import com.example.apisystem.repository.DatabaseConnectionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class DatabaseManageService {

    @Autowired
    private DatabaseConnectionRepository databaseConnectionRepository;

    @Value("${spring.datasource.url}")
    private String currentDbUrl;

    @Value("${spring.datasource.username}")
    private String currentDbUsername;

    @Value("${spring.datasource.password}")
    private String currentDbPassword;

    @Autowired
    private DataSource dataSource;

    // ==================== 数据库连接管理 ====================

    public Page<DatabaseConnection> findConnections(int pageNum, int pageSize, String keyword) {
        Page<DatabaseConnection> page = new Page<>(pageNum, pageSize);
        QueryWrapper<DatabaseConnection> wrapper = new QueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like("name", keyword).or().like("host", keyword);
        }
        wrapper.orderByDesc("create_time");
        return databaseConnectionRepository.selectPage(page, wrapper);
    }

    public DatabaseConnection findConnectionById(Long id) {
        return databaseConnectionRepository.selectById(id);
    }

    public boolean saveConnection(DatabaseConnection connection) {
        if (connection.getId() == null) {
            connection.setCreateTime(LocalDateTime.now().toString());
            connection.setUpdateTime(LocalDateTime.now().toString());
            if (connection.getDbType() == null) {
                connection.setDbType("MYSQL");
            }
            if (connection.getCharset() == null) {
                connection.setCharset("utf8mb4");
            }
            if (connection.getStatus() == null) {
                connection.setStatus("ACTIVE");
            }
            return databaseConnectionRepository.insert(connection) > 0;
        } else {
            connection.setUpdateTime(LocalDateTime.now().toString());
            return databaseConnectionRepository.updateById(connection) > 0;
        }
    }

    public boolean deleteConnection(Long id) {
        return databaseConnectionRepository.deleteById(id) > 0;
    }

    public boolean testConnection(DatabaseConnection connection) {
        try {
            String url = buildJdbcUrl(connection);
            Properties props = new Properties();
            props.setProperty("user", connection.getUsername());
            props.setProperty("password", connection.getPassword());
            props.setProperty("connectTimeout", "5000");
            props.setProperty("socketTimeout", "5000");

            try (Connection conn = DriverManager.getConnection(url, props)) {
                return conn.isValid(5);
            }
        } catch (Exception e) {
            throw new RuntimeException("连接失败: " + e.getMessage());
        }
    }

    private String buildJdbcUrl(DatabaseConnection conn) {
        if ("MYSQL".equalsIgnoreCase(conn.getDbType())) {
            return String.format("jdbc:mysql://%s:%d/%s?useUnicode=true&characterEncoding=%s&serverTimezone=Asia/Shanghai&useSSL=false",
                conn.getHost(), conn.getPort(), conn.getDatabaseName(), conn.getCharset());
        }
        return conn.getHost() + ":" + conn.getPort() + "/" + conn.getDatabaseName();
    }

    // ==================== 表管理 ====================

    public List<TableInfo> getTables() {
        List<TableInfo> tables = new ArrayList<>();
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            String catalog = conn.getCatalog();
            String schema = null;
            try { schema = conn.getSchema(); } catch (Exception e) {}

            try (ResultSet rs = metaData.getTables(catalog, schema, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    TableInfo table = new TableInfo();
                    table.setTableName(rs.getString("TABLE_NAME"));
                    table.setTableComment(rs.getString("REMARKS"));
                    table.setEngine(getTableEngine(conn, catalog, table.getTableName()));
                    tables.add(table);
                }
            }

            // 获取表统计数据
            for (TableInfo table : tables) {
                try (ResultSet rs = conn.createStatement().executeQuery(
                    "SELECT TABLE_ROWS, DATA_LENGTH, INDEX_LENGTH, AUTO_INCREMENT FROM information_schema.TABLES " +
                    "WHERE TABLE_SCHEMA = '" + catalog + "' AND TABLE_NAME = '" + table.getTableName() + "'")) {
                    if (rs.next()) {
                        table.setRowCount(rs.getLong("TABLE_ROWS"));
                        table.setDataLength(rs.getLong("DATA_LENGTH"));
                        table.setIndexLength(rs.getLong("INDEX_LENGTH"));
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("获取表列表失败: " + e.getMessage());
        }
        return tables;
    }

    public List<TableInfo> searchTables(String keyword) {
        List<TableInfo> allTables = getTables();
        if (keyword == null || keyword.isEmpty()) {
            return allTables;
        }
        List<TableInfo> filtered = new ArrayList<>();
        for (TableInfo table : allTables) {
            if ((table.getTableName().toLowerCase().contains(keyword.toLowerCase())) ||
               (table.getTableComment() != null && table.getTableComment().toLowerCase().contains(keyword.toLowerCase()))) {
                filtered.add(table);
            }
        }
        return filtered;
    }

    private String getTableEngine(Connection conn, String catalog, String tableName) {
        try {
            try (ResultSet rs = conn.createStatement().executeQuery(
                "SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA = '" + catalog + "' AND TABLE_NAME = '" + tableName + "'")) {
                if (rs.next()) {
                    return rs.getString("ENGINE");
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    // ==================== 字段管理 ====================

    public List<ColumnInfo> getColumns(String tableName) {
        List<ColumnInfo> columns = new ArrayList<>();
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            String catalog = conn.getCatalog();
            String schema = null;
            try { schema = conn.getSchema(); } catch (Exception e) {}

            try (ResultSet rs = metaData.getColumns(catalog, schema, tableName, "%")) {
                while (rs.next()) {
                    ColumnInfo column = new ColumnInfo();
                    column.setColumnName(rs.getString("COLUMN_NAME"));
                    column.setColumnType(rs.getString("TYPE_NAME"));
                    column.setColumnComment(rs.getString("REMARKS"));
                    column.setIsNullable("YES".equals(rs.getString("IS_NULLABLE")) ? "YES" : "NO");
                    column.setColumnDefault(rs.getString("COLUMN_DEF"));
                    column.setOrdinalPosition(rs.getInt("ORDINAL_POSITION"));

                    // 获取列详细信息
                    try (ResultSet pkRs = metaData.getPrimaryKeys(catalog, schema, tableName)) {
                        while (pkRs.next()) {
                            if (column.getColumnName().equals(pkRs.getString("COLUMN_NAME"))) {
                                column.setColumnKey("PRI");
                                break;
                            }
                        }
                    }
                    columns.add(column);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("获取字段列表失败: " + e.getMessage());
        }
        return columns;
    }

    // ==================== SQL执行 ====================

    public Map<String, Object> executeSql(String sql) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        int affectedRows = 0;
        List<String> headers = new ArrayList<>();
        boolean hasResultSet = false;

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            boolean isSelect = sql.trim().toLowerCase().startsWith("select") ||
                              sql.trim().toLowerCase().startsWith("show") ||
                              sql.trim().toLowerCase().startsWith("desc") ||
                              sql.trim().toLowerCase().startsWith("explain");

            if (isSelect) {
                try (ResultSet rs = stmt.executeQuery(sql)) {
                    ResultSetMetaData rsMetaData = rs.getMetaData();
                    int columnCount = rsMetaData.getColumnCount();

                    for (int i = 1; i <= columnCount; i++) {
                        headers.add(rsMetaData.getColumnLabel(i));
                    }

                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int i = 1; i <= columnCount; i++) {
                            Object value = rs.getObject(i);
                            row.put(rsMetaData.getColumnLabel(i), value);
                        }
                        rows.add(row);
                    }
                    hasResultSet = true;
                }
            } else {
                affectedRows = stmt.executeUpdate(sql, Statement.RETURN_GENERATED_KEYS);
                try (ResultSet keys = stmt.getGeneratedKeys()) {
                    if (keys.next()) {
                        result.put("generatedKey", keys.getLong(1));
                    }
                }
            }

            result.put("headers", headers);
            result.put("rows", rows);
            result.put("affectedRows", affectedRows);
            result.put("hasResultSet", hasResultSet);
            result.put("rowCount", rows.size());

        } catch (Exception e) {
            result.put("error", true);
            result.put("message", e.getMessage());
        }
        return result;
    }

    // ==================== DDL操作 ====================

    public boolean createTable(String sql) {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
            return true;
        } catch (Exception e) {
            throw new RuntimeException("创建表失败: " + e.getMessage());
        }
    }

    public boolean dropTable(String tableName) {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DROP TABLE IF EXISTS `" + tableName + "`");
            return true;
        } catch (Exception e) {
            throw new RuntimeException("删除表失败: " + e.getMessage());
        }
    }

    public boolean truncateTable(String tableName) {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("TRUNCATE TABLE `" + tableName + "`");
            return true;
        } catch (Exception e) {
            throw new RuntimeException("清空表失败: " + e.getMessage());
        }
    }

    public String generateCreateTableSql(String tableName) {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            try (ResultSet rs = stmt.executeQuery("SHOW CREATE TABLE `" + tableName + "`")) {
                if (rs.next()) {
                    return rs.getString(2);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("获取建表语句失败: " + e.getMessage());
        }
        return null;
    }

    // ==================== 数据预览 ====================

    public List<Map<String, Object>> previewTableData(String tableName, int page, int pageSize) {
        List<Map<String, Object>> rows = new ArrayList<>();
        int offset = (page - 1) * pageSize;

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            String sql = String.format("SELECT * FROM `%s` LIMIT %d OFFSET %d", tableName, pageSize, offset);
            try (ResultSet rs = stmt.executeQuery(sql)) {
                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();

                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= columnCount; i++) {
                        row.put(metaData.getColumnLabel(i), rs.getObject(i));
                    }
                    rows.add(row);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("预览数据失败: " + e.getMessage());
        }
        return rows;
    }

    public long getTableRowCount(String tableName) {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            String sql = String.format("SELECT COUNT(*) FROM `%s`", tableName);
            try (ResultSet rs = stmt.executeQuery(sql)) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("获取行数失败: " + e.getMessage());
        }
        return 0;
    }

    // ==================== 数据库信息 ====================

    public Map<String, Object> getDatabaseInfo() {
        Map<String, Object> info = new HashMap<>();
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            info.put("productName", metaData.getDatabaseProductName());
            info.put("productVersion", metaData.getDatabaseProductVersion());
            info.put("driverName", metaData.getDriverName());
            info.put("driverVersion", metaData.getDriverVersion());
            info.put("catalog", conn.getCatalog());
            try { info.put("schema", conn.getSchema()); } catch (Exception e) {}
            info.put("url", metaData.getURL());
            info.put("username", currentDbUsername);
            info.put("tableCount", getTables().size());
        } catch (Exception e) {
            throw new RuntimeException("获取数据库信息失败: " + e.getMessage());
        }
        return info;
    }
}
