package com.example.ingestion.ingest;

import com.example.ingestion.common.ApiException;
import com.example.ingestion.common.Hashing;
import com.example.ingestion.common.Jsons;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;

@Component
public class JsonAnalyzer {
    private static final List<String> COMMON_ROOTS = List.of("data.list", "data.records", "data.items", "data.rows", "list", "records", "items", "rows", "data");
    private static final Pattern DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final Pattern DATE_TIME = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?(?:Z|[+-]\\d{2}:?\\d{2})?$");
    private final Jsons jsons;

    public JsonAnalyzer(Jsons jsons) {
        this.jsons = jsons;
    }

    public record ColumnDef(String type, int length) {}
    public record Extraction(List<JsonNode> records, String detectedRoot, int totalRaw) {}
    public static class ChildAnalysis {
        public final Map<String, ColumnDef> columns = new LinkedHashMap<>();
        public final List<Map<String, Object>> rows = new ArrayList<>();
        public final Map<String, String> fieldMapping = new LinkedHashMap<>();
    }
    public record Analysis(Map<String, ColumnDef> columns, List<Map<String, Object>> rows,
                           Map<String, ChildAnalysis> childTables, Map<String, String> fieldMapping,
                           int emptyCount) {}

    public Extraction extract(JsonNode payload, String configuredRoot) {
        String root = configuredRoot == null ? "" : configuredRoot.trim();
        JsonNode target = root.isBlank() ? null : at(payload, root);
        String detected = root;
        if ((target == null || target.isMissingNode()) && root.isBlank()) {
            if (payload.isArray()) {
                target = payload;
                detected = "$";
            } else {
                for (String candidate : COMMON_ROOTS) {
                    JsonNode value = at(payload, candidate);
                    if (value != null && value.isArray()) {
                        target = value;
                        detected = candidate;
                        break;
                    }
                }
                if (target == null) {
                    target = payload;
                    detected = "$";
                }
            }
        }
        if (target == null || target.isMissingNode()) throw new ApiException(422, "数据根节点不存在: " + root);
        List<JsonNode> records = new ArrayList<>();
        int total;
        if (target.isArray()) {
            total = target.size();
            target.forEach(item -> { if (item.isObject()) records.add(item); });
        } else if (target.isObject()) {
            total = 1;
            records.add(target);
        } else {
            throw new ApiException(422, "数据根节点必须是 JSON 对象或对象数组");
        }
        return new Extraction(records, detected, total);
    }

    public Analysis analyze(List<JsonNode> records) {
        Map<String, ColumnDef> columns = new LinkedHashMap<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, ChildAnalysis> children = new LinkedHashMap<>();
        Map<String, String> fieldMapping = new LinkedHashMap<>();
        Map<String, String> pathColumns = new LinkedHashMap<>();
        Set<String> usedColumns = new LinkedHashSet<>();

        for (int sourceIndex = 0; sourceIndex < records.size(); sourceIndex++) {
            JsonNode record = records.get(sourceIndex);
            if (!record.isObject()) continue;
            Map<String, Object> output = new LinkedHashMap<>();
            Map<String, ArrayNode> childArrays = new LinkedHashMap<>();
            flatten(record, new ArrayList<>(), output, childArrays, fieldMapping, pathColumns, usedColumns);
            output.forEach((name, value) -> columns.merge(name, logicalType(value), this::mergeType));
            String recordHash = Hashing.sha256(output);
            output.put("_record_hash", recordHash);
            output.put("_source_index", sourceIndex);
            rows.add(output);

            for (Map.Entry<String, ArrayNode> childEntry : childArrays.entrySet()) {
                ChildAnalysis child = children.computeIfAbsent(childEntry.getKey(), ignored -> new ChildAnalysis());
                child.columns.put("_parent_hash", new ColumnDef("string", 64));
                child.columns.put("_parent_key", new ColumnDef("string", 255));
                child.columns.put("_item_index", new ColumnDef("integer", 0));
                child.columns.put("_child_key", new ColumnDef("string", 64));
                int itemIndex = 0;
                for (JsonNode item : childEntry.getValue()) {
                    JsonNode value = item.isObject() ? item : jsons.mapper().createObjectNode().set("value", item);
                    Map<String, Object> childRow = new LinkedHashMap<>();
                    Map<String, ArrayNode> ignoredNestedArrays = new LinkedHashMap<>();
                    Map<String, String> localPathColumns = new LinkedHashMap<>();
                    Set<String> localUsed = new LinkedHashSet<>();
                    flatten(value, new ArrayList<>(), childRow, ignoredNestedArrays, child.fieldMapping, localPathColumns, localUsed);
                    childRow.forEach((name, scalar) -> child.columns.merge(name, logicalType(scalar), this::mergeType));
                    childRow.put("_parent_hash", recordHash);
                    childRow.put("_item_index", itemIndex++);
                    childRow.put("_parent_source_index", sourceIndex);
                    childRow.put("_record_hash", Hashing.sha256(childRow));
                    child.rows.add(childRow);
                }
            }
        }
        return new Analysis(columns, rows, children, fieldMapping, records.size() - rows.size());
    }

    public String mapBusinessKey(String configuredKey, Analysis analysis) {
        if (configuredKey == null || configuredKey.isBlank()) return "";
        String direct = snake(configuredKey);
        if (analysis.columns().containsKey(direct)) return direct;
        return analysis.fieldMapping().entrySet().stream()
                .filter(entry -> entry.getValue().equals(configuredKey) || entry.getValue().endsWith("." + configuredKey))
                .map(Map.Entry::getKey).findFirst().orElse(direct);
    }

    private JsonNode at(JsonNode root, String path) {
        JsonNode current = root;
        for (String part : path.split("\\.")) {
            if (part.isBlank()) continue;
            current = current.path(part);
            if (current.isMissingNode()) return current;
        }
        return current;
    }

    private void flatten(JsonNode node, List<String> path, Map<String, Object> output,
                         Map<String, ArrayNode> childArrays, Map<String, String> mapping,
                         Map<String, String> pathColumns, Set<String> usedColumns) {
        if (node == null || node.isNull() || node.isValueNode()) {
            if (path.isEmpty()) return;
            String sourcePath = String.join(".", path);
            String column = columnName(sourcePath, pathColumns, usedColumns);
            output.put(column, cleanScalar(node));
            mapping.put(column, sourcePath);
            return;
        }
        if (node.isArray()) {
            boolean containsObjects = false;
            for (JsonNode item : node) if (item.isObject()) { containsObjects = true; break; }
            if (containsObjects) {
                childArrays.put(snake(String.join("_", path)), (ArrayNode) node);
            } else if (!path.isEmpty()) {
                String sourcePath = String.join(".", path);
                String column = columnName(sourcePath, pathColumns, usedColumns);
                output.put(column, node.isEmpty() ? null : jsons.write(node));
                mapping.put(column, sourcePath);
            }
            return;
        }
        if (node.isEmpty() && !path.isEmpty()) {
            String sourcePath = String.join(".", path);
            String column = columnName(sourcePath, pathColumns, usedColumns);
            output.put(column, null);
            mapping.put(column, sourcePath);
            return;
        }
        node.fields().forEachRemaining(entry -> {
            List<String> next = new ArrayList<>(path);
            next.add(entry.getKey());
            flatten(entry.getValue(), next, output, childArrays, mapping, pathColumns, usedColumns);
        });
    }

    private String columnName(String sourcePath, Map<String, String> pathColumns, Set<String> used) {
        if (pathColumns.containsKey(sourcePath)) return pathColumns.get(sourcePath);
        String base = snake(sourcePath.replace('.', '_'));
        // 冲突后缀取来源路径哈希: 同一字段在不同批次/不同字段顺序下都能得到稳定列名(自增后缀会随顺序漂移)
        String digest = Hashing.sha256(sourcePath);
        String candidate = base;
        for (int length = 6; length <= 16 && used.contains(candidate); length += 2) candidate = base + "_" + digest.substring(0, length);
        int suffix = 2;
        while (used.contains(candidate)) candidate = base + "_" + digest.substring(0, 8) + "_" + suffix++;
        used.add(candidate);
        pathColumns.put(sourcePath, candidate);
        return candidate;
    }

    private Object cleanScalar(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isBoolean()) return node.booleanValue();
        if (node.isIntegralNumber()) {
            long value = node.longValue();
            return value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE ? (int) value : value;
        }
        if (node.isFloatingPointNumber()) return node.decimalValue();
        String text = node.asText().trim();
        if (text.isEmpty()) return null;
        if (DATE_TIME.matcher(text).matches()) return normalizeDateTime(text);
        return text;
    }

    private String normalizeDateTime(String value) {
        String base = value.replace('T', ' ');
        try {
            if (value.endsWith("Z")) {
                return LocalDateTime.ofInstant(Instant.parse(value), ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            }
            if (value.matches(".*[+-]\\d{2}:?\\d{2}$")) {
                return OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.of("Asia/Shanghai")).toLocalDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            }
        } catch (DateTimeParseException ignored) {}
        return base.length() >= 19 ? base.substring(0, 19) : base;
    }

    private ColumnDef logicalType(Object value) {
        if (value == null) return new ColumnDef("null", 0);
        if (value instanceof Boolean) return new ColumnDef("boolean", 1);
        if (value instanceof Integer) return new ColumnDef("integer", 0);
        if (value instanceof Long) return new ColumnDef("bigint", 0);
        if (value instanceof BigDecimal || value instanceof Double || value instanceof Float) return new ColumnDef("decimal", 0);
        String text = String.valueOf(value);
        if (DATE.matcher(text).matches() || DATE_TIME.matcher(text).matches()) return new ColumnDef("datetime", text.length());
        if (text.length() > 1000) return new ColumnDef("text", text.length());
        return new ColumnDef("string", text.length());
    }

    private ColumnDef mergeType(ColumnDef current, ColumnDef incoming) {
        if (current.type().equals("null")) return incoming;
        if (incoming.type().equals("null")) return current;
        if (current.type().equals(incoming.type())) return new ColumnDef(current.type(), Math.max(current.length(), incoming.length()));
        Set<String> numeric = Set.of("integer", "bigint", "decimal");
        if (numeric.contains(current.type()) && numeric.contains(incoming.type())) {
            if (current.type().equals("decimal") || incoming.type().equals("decimal")) return new ColumnDef("decimal", Math.max(current.length(), incoming.length()));
            return new ColumnDef("bigint", Math.max(current.length(), incoming.length()));
        }
        return new ColumnDef("text", Math.max(current.length(), incoming.length()));
    }

    public static String snake(String value) {
        if (value == null) return "field";
        String normalized = value.replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .replaceAll("[^\\p{L}\\p{N}_]+", "_").replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");
        normalized = Pattern.compile("[A-Za-z]+").matcher(normalized).replaceAll(m -> m.group().toLowerCase(Locale.ROOT));
        return normalized.isBlank() ? "field" : normalized.substring(0, Math.min(60, normalized.length()));
    }
}
