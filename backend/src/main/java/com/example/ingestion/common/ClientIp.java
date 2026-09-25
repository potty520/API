package com.example.ingestion.common;

import jakarta.servlet.http.HttpServletRequest;

import java.util.regex.Pattern;

/**
 * 客户端 IP 解析。
 *
 * <p>只有当 TCP 对端是本机反代(回环地址)且未显式关闭信任时, 才采信 X-Forwarded-For 的第一个值;
 * 否则一律使用对端地址。这样 Nginx 反代后的审计日志能记录真实来源,
 * 而客户端自带的伪造 X-Forwarded-For 既不能绕过登录锁定, 也不能污染审计。
 *
 * <p>Nginx 侧必须使用 {@code proxy_set_header X-Forwarded-For $remote_addr}(覆盖而非追加),
 * 见 deploy/nginx-json-ingestion.conf。应用直接对外时必须设 INGESTION_TRUST_PROXY=false。
 */
public final class ClientIp {
    /** 只接受 IP 字面量, 避免把任意字符串写进审计表或前端。 */
    private static final Pattern IP_LITERAL = Pattern.compile("^([0-9]{1,3}\\.){3}[0-9]{1,3}$|^[0-9A-Fa-f:.]{2,45}$");
    private static final int MAX_LENGTH = 45;
    private static final boolean TRUST_PROXY =
            !"false".equalsIgnoreCase(System.getenv().getOrDefault("INGESTION_TRUST_PROXY", "true"));

    private ClientIp() {}

    public static String of(HttpServletRequest request) {
        String remote = normalize(request.getRemoteAddr());
        if (!TRUST_PROXY || !isLoopback(remote)) return remote;
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) return remote;
        String candidate = forwarded.split(",")[0].trim();
        return IP_LITERAL.matcher(candidate).matches() ? candidate : remote;
    }

    private static boolean isLoopback(String address) {
        return address != null && (address.startsWith("127.") || address.equals("::1")
                || address.equals("0:0:0:0:0:0:0:1"));
    }

    private static String normalize(String address) {
        if (address == null) return "";
        String value = address.trim();
        return value.length() > MAX_LENGTH ? value.substring(0, MAX_LENGTH) : value;
    }
}
