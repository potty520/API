package com.example.ingestion.service;

import com.example.ingestion.common.Jsons;
import com.example.ingestion.entity.AlertRecord;
import com.example.ingestion.entity.SystemSetting;
import com.example.ingestion.mapper.SystemSettingMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class AlertNotifier {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SystemSettingMapper settings;
    private final Jsons jsons;
    private final OkHttpClient client;

    public AlertNotifier(SystemSettingMapper settings, Jsons jsons) {
        this.settings = settings;
        this.jsons = jsons;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                .writeTimeout(8, TimeUnit.SECONDS)
                .build();
    }

    /** 推送告警到配置的 Webhook(钉钉/企业微信/飞书群机器人兼容格式)。失败静默，不影响主流程。 */
    public void notify(AlertRecord alert) {
        String webhook = webhookUrl();
        if (webhook == null || webhook.isBlank()) return;
        try {
            String content = "[JSON接入系统] " + alert.getLevelName()
                    + " | " + alert.getTitle()
                    + (alert.getTaskId() == null ? "" : " (任务ID:" + alert.getTaskId() + ")")
                    + "\n" + (alert.getMessage() == null ? "" : alert.getMessage())
                    + "\n时间: " + (alert.getCreatedAt() == null ? LocalDateTime.now() : alert.getCreatedAt()).format(FMT);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("msgtype", "text");
            payload.put("text", Map.of("content", content));
            Request request = new Request.Builder()
                    .url(webhook)
                    .post(RequestBody.create(jsons.write(payload), JSON))
                    .build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.warn("告警推送失败 HTTP {}: {}", response.code(), response.body() == null ? "" : response.body().string());
                }
            }
        } catch (Exception error) {
            log.warn("告警推送异常: {}", error.getMessage());
        }
    }

    public String webhookUrl() {
        SystemSetting setting = settings.selectById("alert_webhook_url");
        return setting == null ? "" : setting.getSettingValue();
    }
}