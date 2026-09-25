package com.example.ingestion.common;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 目标库连接串白名单校验。
 *
 * <p>数据源由“配置员”维护, 而 jdbcUrl 会被原样交给 DriverManager, 因此必须按不可信输入处理:
 * MySQL/PostgreSQL 驱动的 socketFactory、autoDeserialize、queryInterceptors、allowLoadLocalInfile
 * 等参数可直接导致远程类加载/反序列化 RCE, 或读取本地文件(包括 AES 主密钥文件)。
 *
 * <p>策略: 按方言的参数白名单 + 已知高危参数显式拒绝 + 主机字段字符集校验。
 * 未知参数一律拒绝; 确需新参数时必须显式加入白名单并评估风险。
 */
public final class JdbcUrlGuard {

    private static final Map<String, String> URL_PREFIX = Map.of(
            "mysql", "jdbc:mysql://",
            "postgres", "jdbc:postgresql://",
            "sqlserver", "jdbc:sqlserver://",
            "oracle", "jdbc:oracle:thin:@");

    /** 已知可被用于 RCE / 任意文件读取 / 会话劫持的参数, 命中即拒绝并给出明确原因。 */
    private static final Set<String> DENIED_PARAMS = Set.of(
            "autodeserialize", "queryinterceptors", "statementinterceptors", "exceptioninterceptors",
            "connectionlifecycleinterceptors", "socketfactory", "socketfactoryarg", "propertiestransform",
            "authenticationplugins", "disabledauthenticationplugins", "defaultauthenticationplugin",
            "authenticationpluginclassname", "allowloadlocalinfile", "allowurlinlocalinfile",
            "allowloadlocalinfileinpath", "allowmultiqueries", "serverrsapublickeyfile",
            "detectcustomcollations", "useconfigs", "usereadaheadinput", "profilereventhandler",
            "sessionvariables", "sslfactory", "sslhostnameverifier", "loggerlevel", "loggerfile",
            "tns_admin", "tnsadmin", "integratedsecurity", "options");

    private static final Map<String, Set<String>> ALLOWED_PARAMS = Map.of(
            "mysql", Set.of("useunicode", "characterencoding", "charactersetresults", "servertimezone",
                    "usessl", "requiressl", "allowpublickeyretrieval", "connecttimeout", "sockettimeout",
                    "tcpkeepalive", "useserverprepstmts", "cacheprepstmts", "prepstmtcachesize",
                    "prepstmtcachelimit", "rewritebatchedstatements", "usecursorfetch", "defaultfetchsize",
                    "zerodatetimebehavior", "nullcatalogmeanscurrent", "connectioncollation",
                    "uselegacydatetimecode", "enabledtlsprotocols", "enabledsslciphersuites", "tinyint1isbit",
                    "yearisdate", "uselocaltimezone", "preserveresultsetinsertcolumnorder"),
            "postgres", Set.of("ssl", "sslmode", "sslrootcert", "sslcert", "sslkey", "applicationname",
                    "currentschema", "connecttimeout", "sockettimeout", "tcpkeepalive", "readonly",
                    "targetservertype", "loadbalancehosts", "preparethreshold", "autosave",
                    "rewritebatchedinserts", "stringtype", "preferquerymode", "binarytransfer"),
            "sqlserver", Set.of("databasename", "encrypt", "trustservercertificate", "logintimeout",
                    "sockettimeout", "responsebuffering", "applicationname", "sendstringparametersasunicode",
                    "selectmethod", "multisubnetfailover", "failoverpartner", "authentication",
                    "hostnameincertificate", "packetsize", "cancelquerytimeout",
                    "serverpreparedstatementdiscardthreshold", "enableprepareonfirstpreparedstatementcall"),
            "oracle", Set.of("oracle.jdbc.readtimeout", "oracle.net.connect_timeout",
                    "oracle.jdbc.j2ee13compliant", "oracle.jdbc.mapdatetotimestamp",
                    "oracle.net.disableoob", "oracle.net.ssl_version", "oracle.net.ssl_server_dn_match",
                    "oracle.net.keepalive"));

    private static final Pattern FORBIDDEN_CHARS = Pattern.compile("[\\s\"'`\\\\#<>\\x00-\\x1F]");
    private static final Pattern HOST_PATTERN = Pattern.compile("^[A-Za-z0-9._\\-]{1,255}$");
    private static final Pattern DB_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_$\\-]{1,128}$");
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[\\p{L}_][\\p{L}\\p{N}_]{0,127}");

    private JdbcUrlGuard() {}

    /** 保存数据源与真正建连前的统一入口。 */
    public static void validate(String dbType, String jdbcUrl, String host, Integer port,
                                String databaseName, String schemaName, String serviceName) {
        validateUrl(dbType, jdbcUrl);
        validateHostParts(host, port, databaseName, schemaName, serviceName);
    }

    public static void validateUrl(String dbType, String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) return;
        String type = normalizeType(dbType);
        Set<String> allowed = ALLOWED_PARAMS.get(type);
        if (allowed == null) throw new ApiException(422, "不支持的数据源类型: " + dbType);
        String url = jdbcUrl.trim();
        String prefix = URL_PREFIX.get(type);
        if (!url.regionMatches(true, 0, prefix, 0, prefix.length())) {
            throw new ApiException(422, "JDBC URL 必须以 " + prefix + " 开头");
        }
        if (FORBIDDEN_CHARS.matcher(url).find()) {
            throw new ApiException(422, "JDBC URL 含非法字符（空白、引号、反斜杠、# 或控制字符）");
        }
        if (!type.equals("sqlserver") && url.indexOf(';') >= 0) {
            throw new ApiException(422, "JDBC URL 含非法字符: ;");
        }
        // oracle 的 @//host 形式本身含 ://, 只对其它方言做嵌套协议检查
        if (!type.equals("oracle") && url.indexOf("://", prefix.length()) >= 0) {
            throw new ApiException(422, "JDBC URL 不允许嵌套其它协议地址");
        }
        checkParams(type, url, allowed);
    }

    public static void validateHostParts(String host, Integer port, String databaseName,
                                         String schemaName, String serviceName) {
        if (host != null && !host.isBlank() && !HOST_PATTERN.matcher(host.trim()).matches()) {
            throw new ApiException(422, "主机地址只能包含字母、数字、点、下划线和连字符");
        }
        if (port != null && (port < 1 || port > 65535)) throw new ApiException(422, "端口必须在 1-65535 之间");
        if (databaseName != null && !databaseName.isBlank() && !DB_NAME_PATTERN.matcher(databaseName.trim()).matches()) {
            throw new ApiException(422, "数据库名只能包含字母、数字、下划线、$ 和连字符");
        }
        if (serviceName != null && !serviceName.isBlank() && !DB_NAME_PATTERN.matcher(serviceName.trim()).matches()) {
            throw new ApiException(422, "服务名只能包含字母、数字、下划线、$ 和连字符");
        }
        if (schemaName != null && !schemaName.isBlank() && !IDENTIFIER_PATTERN.matcher(schemaName.trim()).matches()) {
            throw new ApiException(422, "Schema 名不合法");
        }
    }

    private static void checkParams(String type, String url, Set<String> allowed) {
        boolean sqlserver = type.equals("sqlserver");
        int cut = sqlserver ? url.indexOf(';') : url.indexOf('?');
        if (cut < 0) return;
        String rest = url.substring(cut + 1);
        if (rest.isBlank()) return;
        for (String pair : rest.split(sqlserver ? ";" : "&")) {
            if (pair.isBlank()) continue;
            int eq = pair.indexOf('=');
            String key = (eq < 0 ? pair : pair.substring(0, eq)).trim().toLowerCase(Locale.ROOT);
            String value = eq < 0 ? "" : pair.substring(eq + 1).trim();
            if (key.isEmpty()) continue;
            if (value.contains("://")) throw new ApiException(422, "JDBC 参数 " + key + " 不允许包含协议地址");
            if (DENIED_PARAMS.contains(key)) {
                throw new ApiException(422, "出于安全考虑禁止使用 JDBC 参数 " + key + "（存在远程代码执行或本地文件读取风险）");
            }
            if (!allowed.contains(key)) {
                throw new ApiException(422, "JDBC 参数 " + key + " 不在允许清单内；请改用主机/端口/库名字段，或联系管理员评估后加入白名单");
            }
        }
    }

    private static String normalizeType(String dbType) {
        return dbType == null ? "" : dbType.trim().toLowerCase(Locale.ROOT);
    }
}
