package com.example.apisystem.service;

import com.example.apisystem.entity.*;
import com.example.apisystem.repository.FieldMappingRepository;
import com.example.apisystem.repository.ScheduledTaskRepository;
import com.jayway.jsonpath.JsonPath;
import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.apisystem.ApplicationContextProvider;
import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class TaskExecutorService {

    @Resource
    private ScheduledTaskService scheduledTaskService;

    @Resource
    private TaskExecutionLogService taskExecutionLogService;

    @Resource
    private TokenConfigService tokenConfigService;

    @Resource
    private ApiConfigService apiConfigService;

    @Resource
    private FieldMappingRepository fieldMappingRepository;

    @Resource
    private ScheduledTaskRepository scheduledTaskRepository;

    private static final int TIMEOUT = 30000;

    public void executeTask(Long taskId) {
        ScheduledTask task = scheduledTaskService.findById(taskId);
        if (task == null) {
            throw new RuntimeException("Task not found: " + taskId);
        }

        TaskExecutionLog log = taskExecutionLogService.createLog(taskId, task.getTaskName());
        long startTime = System.currentTimeMillis();
        String requestBody = null;
        String responseBody = null;

        try {
            // 获取API配置
            ApiConfig apiConfig = apiConfigService.findById(task.getApiConfigId());
            if (apiConfig == null) {
                throw new RuntimeException("API config not found: " + task.getApiConfigId());
            }

            // 获取Token
            String token = null;
            if (apiConfig.getTokenConfigId() != null) {
                TokenConfig tokenConfig = tokenConfigService.findById(apiConfig.getTokenConfigId());
                if (tokenConfig != null) {
                    token = getValidToken(tokenConfig);
                }
            }

            // 获取字段映射
            List<FieldMapping> fieldMappings = fieldMappingRepository.findByApiConfigId(apiConfig.getId());
            if (fieldMappings == null || fieldMappings.isEmpty()) {
                throw new RuntimeException("No field mappings found for API config: " + apiConfig.getId());
            }

            // 调用API
            responseBody = callApi(apiConfig, token, requestBody);

            // 解析数据
            List<Map<String, Object>> dataList = extractData(apiConfig.getDataExtractPath(), responseBody);
            if (dataList == null || dataList.isEmpty()) {
                throw new RuntimeException("No data extracted from API response");
            }

            // 确保目标表存在
            ensureTableExists(task.getTargetTable(), fieldMappings);

            // 入库数据
            int totalRecords = dataList.size();
            int newInsertCount = 0;
            int existingCount = 0;

            for (Map<String, Object> dataItem : dataList) {
                boolean inserted = insertOrUpdateData(task.getTargetTable(), fieldMappings, dataItem);
                if (inserted) {
                    newInsertCount++;
                } else {
                    existingCount++;
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            taskExecutionLogService.updateLogSuccess(log, duration, totalRecords, newInsertCount, existingCount);
            scheduledTaskService.updateTaskExecutionStats(taskId, "SUCCESS", duration, totalRecords, newInsertCount, existingCount);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            String errorMessage = e.getMessage();
            String stackTrace = getStackTraceAsString(e);

            taskExecutionLogService.updateLogFailed(log, duration, errorMessage, stackTrace);
            scheduledTaskService.updateTaskExecutionStats(taskId, "FAILED", duration, 0, 0, 0);

            throw new RuntimeException(e);
        }
    }

    private String getValidToken(TokenConfig tokenConfig) {
        if ("FIXED".equals(tokenConfig.getTokenType())) {
            return tokenConfig.getFixedToken();
        } else if ("DYNAMIC".equals(tokenConfig.getTokenType())) {
            // 动态获取Token
            try {
                String response = callApiInternal(tokenConfig.getTokenUrl(), "POST",
                    tokenConfig.getTokenParams(), tokenConfig.getTokenHeaders());
                return JsonPath.read(response, tokenConfig.getTokenExtractPath());
            } catch (Exception e) {
                throw new RuntimeException("Failed to get dynamic token: " + e.getMessage());
            }
        }
        return null;
    }

    private String callApi(ApiConfig apiConfig, String token, String requestBody) throws Exception {
        Map<String, String> headers = new HashMap<>();
        if (token != null) {
            headers.put("Authorization", "Bearer " + token);
        }
        if (apiConfig.getApiHeaders() != null) {
            Map<String, String> configHeaders = parseJson(apiConfig.getApiHeaders());
            headers.putAll(configHeaders);
        }
        return callApiInternal(apiConfig.getApiUrl(), apiConfig.getApiMethod(),
            apiConfig.getApiParams(), headers, requestBody);
    }

    private String callApiInternal(String url, String method, String params,
                                    Map<String, String> headers, String requestBody) throws Exception {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(TIMEOUT)
                .setSocketTimeout(TIMEOUT)
                .build();

            if ("GET".equalsIgnoreCase(method)) {
                String fullUrl = url;
                if (params != null && !params.isEmpty()) {
                    fullUrl = url + "?" + buildQueryString(params);
                }
                HttpGet httpGet = new HttpGet(fullUrl);
                httpGet.setConfig(requestConfig);
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    httpGet.setHeader(entry.getKey(), entry.getValue());
                }

                try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
                    HttpEntity entity = response.getEntity();
                    return EntityUtils.toString(entity, StandardCharsets.UTF_8);
                }
            } else {
                HttpPost httpPost = new HttpPost(url);
                httpPost.setConfig(requestConfig);
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    httpPost.setHeader(entry.getKey(), entry.getValue());
                }
                if (requestBody != null) {
                    httpPost.setEntity(new StringEntity(requestBody, StandardCharsets.UTF_8));
                } else if (params != null) {
                    httpPost.setEntity(new StringEntity(params, StandardCharsets.UTF_8));
                    httpPost.setHeader("Content-Type", "application/json");
                }

                try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                    HttpEntity entity = response.getEntity();
                    return EntityUtils.toString(entity, StandardCharsets.UTF_8);
                }
            }
        }
    }

    private String callApiInternal(String url, String method, String params, String headers) throws Exception {
        return callApiInternal(url, method, params, new HashMap<>(), null);
    }

    private String buildQueryString(String params) throws Exception {
        Map<String, String> paramMap = parseJson(params);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : paramMap.entrySet()) {
            if (sb.length() > 0) sb.append("&");
            sb.append(entry.getKey()).append("=").append(java.net.URLEncoder.encode(entry.getValue(), "UTF-8"));
        }
        return sb.toString();
    }

    private Map<String, String> parseJson(String json) throws Exception {
        Map<String, String> result = new HashMap<>();
        if (json == null || json.isEmpty()) return result;

        // 简单解析JSON
        json = json.trim();
        if (json.startsWith("{") && json.endsWith("}")) {
            json = json.substring(1, json.length() - 1);
        }

        // 分割键值对
        String[] pairs = splitJsonObject(json);
        for (String pair : pairs) {
            int colonIndex = pair.indexOf(':');
            if (colonIndex > 0) {
                String key = pair.substring(0, colonIndex).trim();
                String value = pair.substring(colonIndex + 1).trim();
                key = removeQuotes(key);
                value = removeQuotes(value);
                result.put(key, value);
            }
        }
        return result;
    }

    private String[] splitJsonObject(String json) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int braceCount = 0;
        int bracketCount = 0;
        boolean inQuote = false;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"' && (i == 0 || json.charAt(i - 1) != '\\')) {
                inQuote = !inQuote;
            }
            if (!inQuote) {
                if (c == '{') braceCount++;
                else if (c == '}') braceCount--;
                else if (c == '[') bracketCount++;
                else if (c == ']') bracketCount--;
            }
            if (c == ',' && braceCount == 0 && bracketCount == 0 && !inQuote) {
                result.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            result.add(current.toString().trim());
        }
        return result.toArray(new String[0]);
    }

    private String removeQuotes(String str) {
        if (str == null) return null;
        str = str.trim();
        if (str.startsWith("\"") && str.endsWith("\"") && str.length() >= 2) {
            str = str.substring(1, str.length() - 1);
        }
        return str.replace("\\\"", "\"");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractData(String jsonPath, String responseBody) throws Exception {
        if (jsonPath == null || jsonPath.equals("$") || jsonPath.isEmpty()) {
            // 直接返回整个响应作为列表
            if (responseBody.trim().startsWith("[")) {
                return com.jayway.jsonpath.JsonPath.parse(responseBody).read("$");
            } else {
                List<Map<String, Object>> list = new ArrayList<>();
                Object parsed = com.jayway.jsonpath.JsonPath.parse(responseBody).read("$");
                if (parsed instanceof Map) {
                    list.add((Map<String, Object>) parsed);
                }
                return list;
            }
        }
        Object result = com.jayway.jsonpath.JsonPath.parse(responseBody).read(jsonPath);
        if (result instanceof List) {
            return (List<Map<String, Object>>) result;
        } else {
            List<Map<String, Object>> list = new ArrayList<>();
            if (result instanceof Map) {
                list.add((Map<String, Object>) result);
            }
            return list;
        }
    }

    private void ensureTableExists(String tableName, List<FieldMapping> fieldMappings) throws SQLException {
        if (!tableExists(tableName)) {
            createTable(tableName, fieldMappings);
        }
    }

    private boolean tableExists(String tableName) throws SQLException {
        // 先查找配置的数据库连接
        Connection conn = getConnection();
        try {
            DatabaseMetaData metaData = conn.getMetaData();
            String catalog = conn.getCatalog();
            String schema = null;
            try { schema = conn.getSchema(); } catch (Exception e) {}

            try (ResultSet rs = metaData.getTables(catalog, schema, tableName, new String[]{"TABLE"})) {
                return rs.next();
            }
        } finally {
            closeConnection(conn);
        }
    }

    private void createTable(String tableName, List<FieldMapping> fieldMappings) throws SQLException {
        Connection conn = getConnection();
        try {
            StringBuilder sql = new StringBuilder("CREATE TABLE IF NOT EXISTS ");
            sql.append(tableName).append(" (");

            // 添加主键和唯一键字段
            List<String> uniqueKeyFields = new ArrayList<>();
            String uniqueKeyTarget = null;

            for (FieldMapping fm : fieldMappings) {
                if (fm.getIsUniqueKey() != null && fm.getIsUniqueKey() == 1) {
                    uniqueKeyFields.add(fm.getTargetField());
                    uniqueKeyTarget = fm.getTargetField();
                }
            }

            // 构建列定义
            List<String> columns = new ArrayList<>();
            for (FieldMapping fm : fieldMappings) {
                String colDef = "`" + fm.getTargetField() + "` " + convertToSqlType(fm.getTargetType());
                columns.add(colDef);
            }

            // 添加唯一键约束
            if (!uniqueKeyFields.isEmpty()) {
                columns.add("UNIQUE KEY `uk_" + uniqueKeyTarget + "` (`" + uniqueKeyTarget + "`)");
            }

            sql.append(String.join(", ", columns));
            sql.append(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ODS临时表'");

            conn.createStatement().executeUpdate(sql.toString());
            System.out.println("Created table: " + tableName);
        } finally {
            closeConnection(conn);
        }
    }

    private String convertToSqlType(String javaType) {
        if (javaType == null) return "VARCHAR(255)";

        String type = javaType.toUpperCase().trim();

        if (type.startsWith("INT") || type.equals("INTEGER")) return "INT";
        if (type.startsWith("BIGINT")) return "BIGINT";
        if (type.startsWith("SMALLINT")) return "SMALLINT";
        if (type.startsWith("TINYINT")) return "TINYINT";
        if (type.startsWith("DOUBLE")) return "DOUBLE";
        if (type.startsWith("DECIMAL")) return "DECIMAL(20,6)";
        if (type.equals("FLOAT")) return "FLOAT";
        if (type.equals("BOOLEAN") || type.equals("BOOL")) return "TINYINT(1)";
        if (type.startsWith("DATETIME") || type.startsWith("TIMESTAMP")) return "DATETIME";
        if (type.startsWith("DATE")) return "DATE";
        if (type.startsWith("TEXT")) return "TEXT";
        if (type.startsWith("VARCHAR")) return type;
        if (type.startsWith("CHAR")) return type;
        if (type.startsWith("JSON")) return "JSON";

        return "VARCHAR(255)";
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean insertOrUpdateData(String tableName, List<FieldMapping> fieldMappings,
                                       Map<String, Object> dataItem) throws SQLException {
        Connection conn = getConnection();
        try {
            // 构建INSERT ON DUPLICATE KEY UPDATE语句
            List<String> columns = new ArrayList<>();
            List<String> values = new ArrayList<>();
            List<String> updateClauses = new ArrayList<>();

            // 找出唯一键
            String uniqueKeyField = null;
            Object uniqueKeyValue = null;

            for (FieldMapping fm : fieldMappings) {
                columns.add("`" + fm.getTargetField() + "`");
                Object value = extractValue(fm.getSourceJsonPath(), dataItem);
                values.add("?");
                updateClauses.add("`" + fm.getTargetField() + "` = VALUES(`" + fm.getTargetField() + "`)");

                if (fm.getIsUniqueKey() != null && fm.getIsUniqueKey() == 1) {
                    uniqueKeyField = fm.getTargetField();
                    uniqueKeyValue = value;
                }
            }

            String sql;
            if (uniqueKeyField != null) {
                sql = "INSERT INTO " + tableName + " (" + String.join(", ", columns) + ") " +
                      "VALUES (" + String.join(", ", values) + ") " +
                      "ON DUPLICATE KEY UPDATE " + String.join(", ", updateClauses);
            } else {
                sql = "INSERT INTO " + tableName + " (" + String.join(", ", columns) + ") " +
                      "VALUES (" + String.join(", ", values) + ")";
            }

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                int paramIndex = 1;
                for (FieldMapping fm : fieldMappings) {
                    Object value = extractValue(fm.getSourceJsonPath(), dataItem);
                    ps.setObject(paramIndex++, convertValue(value, fm.getTargetType()));
                }

                int affected = ps.executeUpdate();
                return affected > 0;
            }
        } finally {
            closeConnection(conn);
        }
    }

    private Object extractValue(String jsonPath, Map<String, Object> dataItem) {
        if (jsonPath == null || jsonPath.isEmpty()) return null;

        // jsonPath格式: $.fieldName 或 $.data.list
        try {
            String path = jsonPath;
            if (!path.startsWith("$")) {
                path = "$." + path;
            }
            return JsonPath.read(dataItem, path);
        } catch (Exception e) {
            // 尝试直接作为key获取
            String key = jsonPath.replace("$.", "").replace("$", "");
            if (dataItem.containsKey(key)) {
                return dataItem.get(key);
            }
            // 尝试忽略$的直接字段名
            for (Map.Entry<String, Object> entry : dataItem.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(key)) {
                    return entry.getValue();
                }
            }
            return null;
        }
    }

    private Object convertValue(Object value, String targetType) {
        if (value == null) return null;

        String type = targetType != null ? targetType.toUpperCase() : "VARCHAR";

        if (type.startsWith("INT") || type.startsWith("BIGINT") || type.startsWith("SMALLINT") || type.startsWith("TINYINT")) {
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
            return Long.parseLong(value.toString());
        }

        if (type.startsWith("DOUBLE") || type.startsWith("DECIMAL") || type.startsWith("FLOAT")) {
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }
            return Double.parseDouble(value.toString());
        }

        if (type.equals("BOOLEAN") || type.equals("BOOL") || type.startsWith("TINYINT(1)")) {
            if (value instanceof Boolean) return value;
            return Boolean.parseBoolean(value.toString());
        }

        if (type.startsWith("DATETIME") || type.startsWith("DATE") || type.startsWith("TIMESTAMP")) {
            if (value instanceof java.util.Date) {
                return new java.sql.Timestamp(((java.util.Date) value).getTime());
            }
            if (value instanceof String) {
                try {
                    return Timestamp.valueOf((String) value);
                } catch (Exception e) {
                    return new Timestamp(System.currentTimeMillis());
                }
            }
        }

        return value.toString();
    }

    private Connection getConnection() throws SQLException {
        // 从数据源获取连接
        javax.sql.DataSource dataSource = ApplicationContextProvider.getApplicationContext().getBean(javax.sql.DataSource.class);
        return dataSource.getConnection();
    }

    private void closeConnection(Connection conn) {
        if (conn != null) {
            try {
                conn.close();
            } catch (Exception e) {
                // ignore
            }
        }
    }

    public String getNextExecuteTime(String cronExpression) {
        try {
            CronExpressionParser parser = new CronExpressionParser(cronExpression);
            LocalDateTime nextTime = parser.getNextExecution();
            return nextTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception e) {
            return "Invalid Cron Expression";
        }
    }

    private String getStackTraceAsString(Exception e) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : e.getStackTrace()) {
            sb.append(element.toString()).append("\n");
        }
        return sb.toString();
    }
}
