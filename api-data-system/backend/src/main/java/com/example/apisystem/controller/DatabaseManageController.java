package com.example.apisystem.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.apisystem.dto.ApiResponse;
import com.example.apisystem.entity.ColumnInfo;
import com.example.apisystem.entity.DatabaseConnection;
import com.example.apisystem.entity.TableInfo;
import com.example.apisystem.service.DatabaseManageService;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/database")
public class DatabaseManageController {

    private final DatabaseManageService databaseManageService;

    public DatabaseManageController(DatabaseManageService databaseManageService) {
        this.databaseManageService = databaseManageService;
    }

    // ==================== 数据库连接管理 ====================

    @GetMapping("/connections")
    public ApiResponse<Page<DatabaseConnection>> getConnections(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword) {
        Page<DatabaseConnection> page = databaseManageService.findConnections(pageNum, pageSize, keyword);
        return ApiResponse.success(page);
    }

    @GetMapping("/connections/{id}")
    public ApiResponse<DatabaseConnection> getConnection(@PathVariable Long id) {
        DatabaseConnection connection = databaseManageService.findConnectionById(id);
        return ApiResponse.success(connection);
    }

    @PostMapping("/connections/save")
    public ApiResponse<Boolean> saveConnection(@RequestBody DatabaseConnection connection) {
        boolean result = databaseManageService.saveConnection(connection);
        return ApiResponse.success("保存成功", result);
    }

    @DeleteMapping("/connections/{id}")
    public ApiResponse<Boolean> deleteConnection(@PathVariable Long id) {
        boolean result = databaseManageService.deleteConnection(id);
        return ApiResponse.success("删除成功", result);
    }

    @PostMapping("/connections/test")
    public ApiResponse<Boolean> testConnection(@RequestBody DatabaseConnection connection) {
        boolean success = databaseManageService.testConnection(connection);
        return ApiResponse.success("测试" + (success ? "成功" : "失败"), success);
    }

    // ==================== 表管理 ====================

    @GetMapping("/tables")
    public ApiResponse<List<TableInfo>> getTables(@RequestParam(required = false) String keyword) {
        List<TableInfo> tables = keyword != null ?
            databaseManageService.searchTables(keyword) :
            databaseManageService.getTables();
        return ApiResponse.success(tables);
    }

    @GetMapping("/tables/{tableName}/columns")
    public ApiResponse<List<ColumnInfo>> getColumns(@PathVariable String tableName) {
        List<ColumnInfo> columns = databaseManageService.getColumns(tableName);
        return ApiResponse.success(columns);
    }

    @GetMapping("/tables/{tableName}/preview")
    public ApiResponse<Map<String, Object>> previewTableData(
            @PathVariable String tableName,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        List<Map<String, Object>> rows = databaseManageService.previewTableData(tableName, page, pageSize);
        long total = databaseManageService.getTableRowCount(tableName);
        return ApiResponse.success(Map.of("rows", rows, "total", total));
    }

    @GetMapping("/tables/{tableName}/create-sql")
    public ApiResponse<String> getCreateTableSql(@PathVariable String tableName) {
        String sql = databaseManageService.generateCreateTableSql(tableName);
        return ApiResponse.success(sql);
    }

    @DeleteMapping("/tables/{tableName}")
    public ApiResponse<Boolean> dropTable(@PathVariable String tableName) {
        boolean result = databaseManageService.dropTable(tableName);
        return ApiResponse.success("删除表成功", result);
    }

    @DeleteMapping("/tables/{tableName}/truncate")
    public ApiResponse<Boolean> truncateTable(@PathVariable String tableName) {
        boolean result = databaseManageService.truncateTable(tableName);
        return ApiResponse.success("清空表成功", result);
    }

    // ==================== SQL执行 ====================

    @PostMapping("/execute")
    public ApiResponse<Map<String, Object>> executeSql(@RequestBody Map<String, String> request) {
        String sql = request.get("sql");
        if (sql == null || sql.trim().isEmpty()) {
            return ApiResponse.fail("SQL语句不能为空");
        }
        Map<String, Object> result = databaseManageService.executeSql(sql);
        if (Boolean.TRUE.equals(result.get("error"))) {
            return ApiResponse.fail(result.get("message").toString());
        }
        return ApiResponse.success(result);
    }

    @PostMapping("/tables/create")
    public ApiResponse<Boolean> createTable(@RequestBody Map<String, String> request) {
        String sql = request.get("sql");
        if (sql == null || sql.trim().isEmpty()) {
            return ApiResponse.fail("建表SQL不能为空");
        }
        boolean result = databaseManageService.createTable(sql);
        return ApiResponse.success("创建表成功", result);
    }

    // ==================== 数据库信息 ====================

    @GetMapping("/info")
    public ApiResponse<Map<String, Object>> getDatabaseInfo() {
        Map<String, Object> info = databaseManageService.getDatabaseInfo();
        return ApiResponse.success(info);
    }
}
