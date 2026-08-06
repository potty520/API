package com.example.ingestion.ingest;

import com.example.ingestion.entity.SystemSetting;
import com.example.ingestion.mapper.SystemSettingMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** 为建表字段生成中文注释: 内置词典优先，未命中时可选调用本地 Ollama 批量翻译。 */
@Slf4j
@Component
public class ColumnCommenter {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final Pattern ASCII_ONLY = Pattern.compile("[A-Za-z0-9_]+");

    private static final Map<String, String> DICTIONARY = new HashMap<>();
    static {
        DICTIONARY.put("id", "主键ID"); DICTIONARY.put("_id", "自增主键"); DICTIONARY.put("_record_hash", "记录哈希");
        DICTIONARY.put("_sync_time", "同步时间"); DICTIONARY.put("_task_id", "任务ID"); DICTIONARY.put("_batch_id", "批次号");
        DICTIONARY.put("_source", "数据来源"); DICTIONARY.put("_updated_at", "更新时间"); DICTIONARY.put("_parent_hash", "父记录哈希");
        DICTIONARY.put("_parent_key", "父记录业务键"); DICTIONARY.put("_parent_source_index", "父记录源序号");
        DICTIONARY.put("_item_index", "明细序号"); DICTIONARY.put("_child_key", "子记录业务键"); DICTIONARY.put("_source_index", "源数据序号");
        DICTIONARY.put("value", "值"); DICTIONARY.put("name", "姓名"); DICTIONARY.put("user_name", "用户名");
        DICTIONARY.put("username", "用户名"); DICTIONARY.put("password", "密码"); DICTIONARY.put("nick_name", "昵称");
        DICTIONARY.put("display_name", "显示名称"); DICTIONARY.put("real_name", "真实姓名"); DICTIONARY.put("full_name", "全名");
        DICTIONARY.put("first_name", "名"); DICTIONARY.put("last_name", "姓"); DICTIONARY.put("maiden_name", "曾用姓");
        DICTIONARY.put("title", "标题"); DICTIONARY.put("subtitle", "副标题"); DICTIONARY.put("content", "内容");
        DICTIONARY.put("description", "描述"); DICTIONARY.put("remark", "备注"); DICTIONARY.put("comment", "备注");
        DICTIONARY.put("status", "状态"); DICTIONARY.put("state", "状态"); DICTIONARY.put("type", "类型");
        DICTIONARY.put("category", "分类"); DICTIONARY.put("code", "编码"); DICTIONARY.put("no", "编号");
        DICTIONARY.put("number", "编号"); DICTIONARY.put("serial_no", "流水号"); DICTIONARY.put("order_no", "订单号");
        DICTIONARY.put("email", "邮箱"); DICTIONARY.put("phone", "电话"); DICTIONARY.put("mobile", "手机号");
        DICTIONARY.put("telephone", "电话"); DICTIONARY.put("phone_number", "电话号码"); DICTIONARY.put("mobile_phone", "手机号");
        DICTIONARY.put("id_card", "身份证号"); DICTIONARY.put("id_card_no", "身份证号码"); DICTIONARY.put("id_number", "身份证号码");
        DICTIONARY.put("identity_no", "证件号码"); DICTIONARY.put("bank", "开户行"); DICTIONARY.put("bank_name", "开户银行");
        DICTIONARY.put("bank_card_no", "银行卡号"); DICTIONARY.put("account_no", "账号"); DICTIONARY.put("account_name", "账户名");
        DICTIONARY.put("address", "地址"); DICTIONARY.put("city", "城市"); DICTIONARY.put("province", "省份");
        DICTIONARY.put("district", "区县"); DICTIONARY.put("street", "街道"); DICTIONARY.put("postal_code", "邮政编码");
        DICTIONARY.put("zip_code", "邮编"); DICTIONARY.put("country", "国家"); DICTIONARY.put("nation", "民族");
        DICTIONARY.put("age", "年龄"); DICTIONARY.put("gender", "性别"); DICTIONARY.put("sex", "性别");
        DICTIONARY.put("birth_date", "出生日期"); DICTIONARY.put("birthday", "生日"); DICTIONARY.put("birth", "出生日期");
        DICTIONARY.put("occupation", "职业"); DICTIONARY.put("job", "职务"); DICTIONARY.put("position", "职位");
        DICTIONARY.put("department", "部门"); DICTIONARY.put("company", "公司"); DICTIONARY.put("unit", "单位");
        DICTIONARY.put("amount", "金额"); DICTIONARY.put("price", "单价"); DICTIONARY.put("total_amount", "总金额");
        DICTIONARY.put("total", "总计"); DICTIONARY.put("count", "数量"); DICTIONARY.put("quantity", "数量");
        DICTIONARY.put("num", "数量"); DICTIONARY.put("weight", "重量"); DICTIONARY.put("height", "身高");
        DICTIONARY.put("length", "长度"); DICTIONARY.put("width", "宽度"); DICTIONARY.put("size", "尺寸");
        DICTIONARY.put("currency", "币种"); DICTIONARY.put("rate", "费率"); DICTIONARY.put("tax", "税额");
        DICTIONARY.put("discount", "折扣"); DICTIONARY.put("fee", "费用"); DICTIONARY.put("cost", "成本");
        DICTIONARY.put("date", "日期"); DICTIONARY.put("time", "时间"); DICTIONARY.put("datetime", "日期时间");
        DICTIONARY.put("start_date", "开始日期"); DICTIONARY.put("end_date", "结束日期"); DICTIONARY.put("created_at", "创建时间");
        DICTIONARY.put("created_time", "创建时间"); DICTIONARY.put("create_time", "创建时间"); DICTIONARY.put("created_by", "创建人");
        DICTIONARY.put("updated_at", "更新时间"); DICTIONARY.put("update_time", "更新时间"); DICTIONARY.put("updated_by", "更新人");
        DICTIONARY.put("deleted", "删除标记"); DICTIONARY.put("enabled", "启用标记"); DICTIONARY.put("active", "是否启用");
        DICTIONARY.put("url", "URL地址"); DICTIONARY.put("image", "图片"); DICTIONARY.put("image_url", "图片地址");
        DICTIONARY.put("avatar", "头像"); DICTIONARY.put("photo", "照片"); DICTIONARY.put("icon", "图标");
        DICTIONARY.put("file_name", "文件名"); DICTIONARY.put("file_path", "文件路径"); DICTIONARY.put("file_url", "文件地址");
        DICTIONARY.put("version", "版本号"); DICTIONARY.put("revision", "修订号"); DICTIONARY.put("sort", "排序");
        DICTIONARY.put("sort_order", "排序号"); DICTIONARY.put("order_id", "订单ID"); DICTIONARY.put("user_id", "用户ID");
        DICTIONARY.put("product_id", "商品ID"); DICTIONARY.put("goods_id", "商品ID"); DICTIONARY.put("sku_id", "SKU ID");
        DICTIONARY.put("customer_id", "客户ID"); DICTIONARY.put("supplier_id", "供应商ID"); DICTIONARY.put("org_id", "组织ID");
        DICTIONARY.put("dept_id", "部门ID"); DICTIONARY.put("group_id", "分组ID"); DICTIONARY.put("category_id", "分类ID");
        DICTIONARY.put("area_id", "区域ID"); DICTIONARY.put("region", "区域"); DICTIONARY.put("area", "地区");
        DICTIONARY.put("ip", "IP地址"); DICTIONARY.put("ip_address", "IP地址"); DICTIONARY.put("mac_address", "MAC地址");
        DICTIONARY.put("user_agent", "浏览器标识"); DICTIONARY.put("source", "来源"); DICTIONARY.put("channel", "渠道");
        DICTIONARY.put("is_deleted", "是否删除"); DICTIONARY.put("is_active", "是否启用"); DICTIONARY.put("is_valid", "是否有效");
        DICTIONARY.put("has_password", "是否设置密码"); DICTIONARY.put("completed", "是否完成"); DICTIONARY.put("done", "是否完成");
        DICTIONARY.put("detail", "详情"); DICTIONARY.put("details", "详情"); DICTIONARY.put("items", "明细");
        DICTIONARY.put("list", "列表"); DICTIONARY.put("records", "记录列表"); DICTIONARY.put("data", "数据");
        DICTIONARY.put("token", "令牌"); DICTIONARY.put("access_token", "访问令牌"); DICTIONARY.put("expires_in", "有效期(秒)");
        DICTIONARY.put("login", "登录"); DICTIONARY.put("password_hash", "密码哈希"); DICTIONARY.put("role", "角色");
        DICTIONARY.put("role_name", "角色名称"); DICTIONARY.put("permission", "权限"); DICTIONARY.put("permissions", "权限列表");
    }

    private final SystemSettingMapper settings;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private final Map<String, Instant> failures = new ConcurrentHashMap<>();
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).writeTimeout(10, TimeUnit.SECONDS).build();

    public ColumnCommenter(SystemSettingMapper settings) {
        this.settings = settings;
    }

    /** 字段名本身含中文时，直接用中文作为注释。 */
    public String comment(String column) {
        if (column == null || column.isBlank()) return column;
        if (!ASCII_ONLY.matcher(column).matches()) return column; // 中文列名，注释即列名
        String cached = cache.get(column);
        if (cached != null) return cached;
        String dict = customMap().getOrDefault(column.toLowerCase(Locale.ROOT), DICTIONARY.get(column.toLowerCase(Locale.ROOT)));
        if (dict != null && !dict.isBlank()) {
            cache.put(column, dict);
            return dict;
        }
        // 未命中: 仅在显式开启 Ollama 翻译时尝试(生产环境默认关闭，纯离线)
        String translated = translateViaOllama(column);
        cache.put(column, translated == null ? column : translated);
        return translated == null ? column : translated;
    }

    /** 批量预取注释，减少逐字段调用。 */
    public Map<String, String> comments(Collection<String> columns) {
        Map<String, String> result = new LinkedHashMap<>();
        List<String> missing = new ArrayList<>();
        for (String column : columns) {
            if (column == null || column.isBlank()) continue;
            if (!ASCII_ONLY.matcher(column).matches()) { result.put(column, column); continue; }
            String cached = cache.get(column);
            if (cached != null) { result.put(column, cached); continue; }
            String dict = customMap().getOrDefault(column.toLowerCase(Locale.ROOT), DICTIONARY.get(column.toLowerCase(Locale.ROOT)));
            if (dict != null && !dict.isBlank()) { result.put(column, dict); continue; }
            missing.add(column);
        }
        if (!missing.isEmpty()) {
            Map<String, String> batch = translateBatchViaOllama(missing);
            for (String column : missing) {
                String translated = batch.get(column);
                String finalComment = translated == null || translated.isBlank() ? column : translated;
                cache.put(column, finalComment);
                result.put(column, finalComment);
            }
        }
        return result;
    }

    private String translateViaOllama(String column) {
        if (!enabled() || recentlyFailed()) return null;
        Map<String, String> result = translateBatchViaOllama(List.of(column));
        return result.get(column);
    }

    private Map<String, String> translateBatchViaOllama(List<String> columns) {
        Map<String, String> result = new LinkedHashMap<>();
        if (!enabled()) return result;
        String url = setting("ollama_url", "http://127.0.0.1:11434");
        String model = setting("ollama_model", "qwen2.5:14b");
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", model);
            payload.put("stream", false);
            payload.put("options", Map.of("temperature", 0));
            StringBuilder prompt = new StringBuilder("把以下数据库字段名翻译成简洁的中文注释(2-8个字)，按输入顺序每行一个，只输出翻译结果，不要编号、冒号、引号：\n");
            for (String column : columns) prompt.append(column).append('\n');
            payload.put("prompt", prompt.toString());
            Request request = new Request.Builder().url(url + "/api/generate")
                    .post(RequestBody.create(mapper.writeValueAsString(payload), JSON)).build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return result;
                JsonNode node = mapper.readTree(response.body().string());
                String text = node.path("response").asText("");
                String[] lines = text.split("\\r?\\n");
                for (int i = 0; i < lines.length && i < columns.size(); i++) {
                    String line = lines[i].trim().replaceAll("^[\\d\\s\\-.、)）:：]+", "").trim();
                    if (!line.isBlank()) result.put(columns.get(i), line);
                }
            }
        } catch (Exception error) {
            log.warn("Ollama 字段翻译失败: {}", error.getMessage());
            failures.put("last", Instant.now());
        }
        return result;
    }

    private Map<String, String> customMap() {
        try {
            String raw = setting("field_comment_map", "");
            if (raw.isBlank()) return Map.of();
            JsonNode node = mapper.readTree(raw);
            Map<String, String> result = new HashMap<>();
            node.fields().forEachRemaining(entry -> result.put(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue().asText("")));
            return result;
        } catch (Exception error) {
            return Map.of();
        }
    }

    private boolean enabled() {
        return Boolean.parseBoolean(setting("comment_translate_enabled", "false"));
    }

    private boolean recentlyFailed() {
        Instant last = failures.get("last");
        return last != null && last.isAfter(Instant.now().minus(Duration.ofMinutes(10)));
    }

    private String setting(String key, String fallback) {
        try {
            SystemSetting setting = settings.selectById(key);
            return setting == null || setting.getSettingValue() == null || setting.getSettingValue().isBlank() ? fallback : setting.getSettingValue();
        } catch (Exception error) {
            return fallback;
        }
    }
}