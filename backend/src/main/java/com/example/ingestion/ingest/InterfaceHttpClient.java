package com.example.ingestion.ingest;

import com.example.ingestion.common.ApiException;
import com.example.ingestion.common.Jsons;
import com.example.ingestion.entity.InterfaceTask;
import com.example.ingestion.security.CryptoService;
import com.fasterxml.jackson.databind.JsonNode;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
public class InterfaceHttpClient {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final boolean ALLOW_PRIVATE_URLS = Boolean.parseBoolean(System.getenv().getOrDefault("INGESTION_ALLOW_PRIVATE_URLS", "false"));
    private record CachedToken(String token, Instant expiresAt) {}
    public record HttpResult(int status, long durationMs, JsonNode payload, String raw) {}

    private final Jsons jsons;
    private final CryptoService crypto;
    private final Map<Long, CachedToken> tokenCache = new ConcurrentHashMap<>();

    public InterfaceHttpClient(Jsons jsons, CryptoService crypto) {
        this.jsons = jsons;
        this.crypto = crypto;
    }

    public HttpResult call(InterfaceTask task) {
        Map<String, Object> headers = expandMap(jsons.map(task.getHeadersJson()), task);
        Map<String, Object> query = expandMap(jsons.map(task.getQueryJson()), task);
        Map<String, Object> body = expandMap(jsons.map(task.getBodyJson()), task);
        Map<String, Object> auth = jsons.map(crypto.decrypt(task.getAuthJsonEnc()));

        String rawUrl = expand(String.valueOf(task.getUrl()), task);
        validateTargetUrl(rawUrl);
        HttpUrl parsed = HttpUrl.parse(rawUrl);
        if (parsed == null) throw new ApiException(422, "接口 URL 不合法");
        HttpUrl.Builder url = parsed.newBuilder();
        query.forEach((key, value) -> { if (value != null) url.setQueryParameter(key, String.valueOf(value)); });
        Request.Builder request = new Request.Builder().url(url.build());
        headers.forEach((key, value) -> { if (value != null) request.header(key, String.valueOf(value)); });

        if ("basic".equalsIgnoreCase(task.getAuthType())) {
            request.header("Authorization", Credentials.basic(String.valueOf(auth.getOrDefault("username", "")), String.valueOf(auth.getOrDefault("password", "")), java.nio.charset.StandardCharsets.UTF_8));
        } else if ("token".equalsIgnoreCase(task.getAuthType())) {
            String token = resolveToken(task, auth);
            String header = String.valueOf(auth.getOrDefault("headerKey", "Authorization"));
            String prefix = String.valueOf(auth.getOrDefault("prefix", "Bearer "));
            request.header(header, prefix + token);
        }

        RequestBody requestBody = null;
        if ("form".equalsIgnoreCase(task.getAuthType())) {
            FormBody.Builder form = new FormBody.Builder();
            body.forEach((key, value) -> { if (value != null) form.add(key, String.valueOf(value)); });
            form.add(String.valueOf(auth.getOrDefault("usernameField", "username")), String.valueOf(auth.getOrDefault("username", "")));
            form.add(String.valueOf(auth.getOrDefault("passwordField", "password")), String.valueOf(auth.getOrDefault("password", "")));
            requestBody = form.build();
        } else if ("POST".equalsIgnoreCase(task.getMethod())) {
            requestBody = RequestBody.create(jsons.write(body), JSON);
        }
        if ("POST".equalsIgnoreCase(task.getMethod())) request.post(requestBody == null ? RequestBody.create(new byte[0], JSON) : requestBody);
        else request.get();

        int timeout = Math.max(1, Math.min(600, Optional.ofNullable(task.getTimeoutSec()).orElse(30)));
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(timeout, TimeUnit.SECONDS)
                .readTimeout(timeout, TimeUnit.SECONDS)
                .writeTimeout(timeout, TimeUnit.SECONDS)
                .callTimeout(timeout, TimeUnit.SECONDS)
                .build();
        long started = System.nanoTime();
        try (Response response = client.newCall(request.build()).execute()) {
            long duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            String raw = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new RemoteCallException(response.code(), duration, "接口返回 HTTP " + response.code() + ": " + redact(raw.substring(0, Math.min(raw.length(), 500))));
            }
            return new HttpResult(response.code(), duration, jsons.tree(raw), raw);
        } catch (RemoteCallException error) {
            throw error;
        } catch (Exception error) {
            throw new RemoteCallException(null, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started), "接口调用失败: " + error.getMessage(), error);
        }
    }

    private String resolveToken(InterfaceTask task, Map<String, Object> auth) {
        String fixed = String.valueOf(auth.getOrDefault("fixedToken", ""));
        if (!fixed.isBlank()) return fixed;
        CachedToken cached = tokenCache.get(task.getId());
        if (cached != null && cached.expiresAt().isAfter(Instant.now().plusSeconds(30))) return cached.token();
        String tokenUrl = String.valueOf(auth.getOrDefault("tokenUrl", ""));
        if (tokenUrl.isBlank()) throw new ApiException(422, "Token 认证未配置 fixedToken 或 tokenUrl");
        validateTargetUrl(tokenUrl);
        Map<String, Object> tokenBody = auth.get("tokenBody") instanceof Map<?, ?> map ? castMap(map) : Map.of();
        Request request = new Request.Builder().url(tokenUrl).post(RequestBody.create(jsons.write(tokenBody), JSON)).build();
        try (Response response = new OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build().newCall(request).execute()) {
            String raw = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) throw new ApiException(422, "Token 刷新失败，HTTP " + response.code());
            JsonNode node = jsons.tree(raw);
            String tokenPath = String.valueOf(auth.getOrDefault("tokenPath", "access_token"));
            for (String part : tokenPath.split("\\.")) node = node.path(part);
            if (node.isMissingNode() || node.isNull() || node.asText().isBlank()) throw new ApiException(422, "Token 响应中找不到字段: " + tokenPath);
            long expires = Long.parseLong(String.valueOf(auth.getOrDefault("expiresInSec", "3600")));
            CachedToken value = new CachedToken(node.asText(), Instant.now().plusSeconds(Math.max(60, expires)));
            tokenCache.put(task.getId(), value);
            return value.token();
        } catch (ApiException error) {
            throw error;
        } catch (Exception error) {
            throw new ApiException(422, "Token 刷新失败: " + error.getMessage());
        }
    }

    private void validateTargetUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) throw new ApiException(422, "接口 URL 不能为空");
        URI uri;
        try {
            uri = new URI(rawUrl.trim());
        } catch (URISyntaxException error) {
            throw new ApiException(422, "接口 URL 不合法");
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new ApiException(422, "出于安全考虑，接口地址仅支持 http/https 协议");
        }
        if (ALLOW_PRIVATE_URLS) return;
        String host = uri.getHost();
        if (host == null || host.isBlank()) throw new ApiException(422, "接口 URL 缺少主机名");
        try {
            InetAddress address = InetAddress.getByName(host);
            if (address.isLoopbackAddress() || address.isAnyLocalAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress()) {
                throw new ApiException(422, "出于安全考虑，禁止访问本机或内网地址: " + host);
            }
        } catch (ApiException error) {
            throw error;
        } catch (Exception error) {
            throw new ApiException(422, "接口地址无法解析: " + host);
        }
    }

    private String redact(String text) {
        if (text == null || text.isBlank()) return text;
        return text.replaceAll("(?i)(\"?(authorization|access_token|access-token|password|secret|api[_-]?key)\"?\\s*[:=]\\s*\"?)[^\\s,}\"\\]]{6,}", "$1***");
    }

    private Map<String, Object> expandMap(Map<String, Object> input, InterfaceTask task) {
        Map<String, Object> output = new LinkedHashMap<>();
        input.forEach((key, value) -> output.put(key, expandValue(value, task)));
        return output;
    }

    private Object expandValue(Object value, InterfaceTask task) {
        if (value instanceof String text) return expand(text, task);
        if (value instanceof Map<?, ?> map) return expandMap(castMap(map), task);
        if (value instanceof List<?> list) return list.stream().map(item -> expandValue(item, task)).toList();
        return value;
    }

    private String expand(String value, InterfaceTask task) {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        LocalDateTime now = LocalDateTime.now(zone);
        String result = value.replace("{{now}}", now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .replace("{{date}}", now.toLocalDate().toString())
                .replace("{{last_success_time}}", task.getLastSuccessAt() == null ? "" : task.getLastSuccessAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\{\\{env\\.([A-Za-z_][A-Za-z0-9_]*)}}").matcher(result);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) matcher.appendReplacement(buffer, java.util.regex.Matcher.quoteReplacement(System.getenv().getOrDefault(matcher.group(1), "")));
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private Map<String, Object> castMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    public static class RemoteCallException extends RuntimeException {
        private final Integer httpStatus;
        private final long durationMs;
        public RemoteCallException(Integer status, long durationMs, String message) { this(status, durationMs, message, null); }
        public RemoteCallException(Integer status, long durationMs, String message, Throwable cause) {
            super(message, cause);
            this.httpStatus = status;
            this.durationMs = durationMs;
        }
        public Integer getHttpStatus() { return httpStatus; }
        public long getDurationMs() { return durationMs; }
    }
}