package com.example.ingestion.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * jdbcUrl 由“配置员”填写并原样交给 DriverManager, 必须按不可信输入处理:
 * autoDeserialize / socketFactory / allowLoadLocalInfile 等参数可直接导致 RCE 或读取主密钥文件。
 */
class JdbcUrlGuardTest {

    private static final String MYSQL = "jdbc:mysql://127.0.0.1:3306/demo";
    private static final String POSTGRES = "jdbc:postgresql://127.0.0.1:5432/demo";
    private static final String SQLSERVER = "jdbc:sqlserver://127.0.0.1:1433;databaseName=demo";
    private static final String ORACLE = "jdbc:oracle:thin:@//127.0.0.1:1521/ORCLPDB1";

    private static ApiException rejectUrl(String dbType, String jdbcUrl) {
        return assertThrows(ApiException.class, () -> JdbcUrlGuard.validateUrl(dbType, jdbcUrl));
    }

    @Test
    void blankUrlIsAllowedBecauseHostFieldsAreUsed() {
        assertDoesNotThrow(() -> JdbcUrlGuard.validateUrl("mysql", null));
        assertDoesNotThrow(() -> JdbcUrlGuard.validateUrl("mysql", "   "));
    }

    @Test
    void whitelistedParametersPass() {
        assertDoesNotThrow(() -> JdbcUrlGuard.validateUrl("mysql", MYSQL + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false"));
        assertDoesNotThrow(() -> JdbcUrlGuard.validateUrl("postgres", POSTGRES + "?sslmode=require&connectTimeout=5"));
        assertDoesNotThrow(() -> JdbcUrlGuard.validateUrl("sqlserver", SQLSERVER + ";encrypt=false;trustServerCertificate=true"));
        assertDoesNotThrow(() -> JdbcUrlGuard.validateUrl("oracle", ORACLE));
    }

    @Test
    void parametersLeadingToRceOrLocalFileReadAreRejected() {
        assertEquals(422, rejectUrl("mysql", MYSQL + "?autoDeserialize=true").getStatus());
        assertTrue(rejectUrl("mysql", MYSQL + "?queryInterceptors=com.evil.Plugin").getMessage().contains("queryinterceptors"));
        rejectUrl("mysql", MYSQL + "?allowLoadLocalInfile=true");
        rejectUrl("mysql", MYSQL + "?allowUrlInLocalInfile=true");
        rejectUrl("mysql", MYSQL + "?allowMultiQueries=true");
        rejectUrl("mysql", MYSQL + "?sessionVariables=sql_mode=ANSI");
        rejectUrl("postgres", POSTGRES + "?socketFactory=org.evil.Factory");
        rejectUrl("postgres", POSTGRES + "?sslfactory=org.evil.Factory");
        rejectUrl("oracle", ORACLE + "?tns_admin=/tmp/evil");
        rejectUrl("sqlserver", SQLSERVER + ";integratedSecurity=true");
    }

    @Test
    void unknownParametersAreRejected() {
        assertTrue(rejectUrl("mysql", MYSQL + "?someBrandNewFlag=1").getMessage().contains("不在允许清单内"));
        rejectUrl("postgres", POSTGRES + "?loggerLevel=TRACE");
    }

    @Test
    void structuralTricksAreRejected() {
        rejectUrl("mysql", POSTGRES);
        rejectUrl("mysql", MYSQL + ";allowLoadLocalInfile=true");
        rejectUrl("mysql", MYSQL + "?connectTimeout=5 jdbc:mysql://evil.example/db");
        rejectUrl("postgres", POSTGRES + "?sslrootcert=http://evil.example/ca.pem");
        rejectUrl("db2", MYSQL);
    }

    @Test
    void hostFieldsAreValidated() {
        assertDoesNotThrow(() -> JdbcUrlGuard.validateHostParts("db.internal-1", 3306, "demo_db", "public", "ORCLPDB1"));
        assertDoesNotThrow(() -> JdbcUrlGuard.validateHostParts(null, null, null, null, null));
        assertDoesNotThrow(() -> JdbcUrlGuard.validateHostParts("  ", 3306, " ", null, null));
        assertEquals(422, assertThrows(ApiException.class,
                () -> JdbcUrlGuard.validateHostParts("127.0.0.1;drop table x", 3306, "demo", null, null)).getStatus());
        assertThrows(ApiException.class, () -> JdbcUrlGuard.validateHostParts("127.0.0.1", 0, "demo", null, null));
        assertThrows(ApiException.class, () -> JdbcUrlGuard.validateHostParts("127.0.0.1", 70000, "demo", null, null));
        assertThrows(ApiException.class, () -> JdbcUrlGuard.validateHostParts("127.0.0.1", 3306, "demo`--", null, null));
        assertThrows(ApiException.class, () -> JdbcUrlGuard.validateHostParts("127.0.0.1", 3306, "demo", "1;drop", null));
    }

    @Test
    void combinedEntryChecksUrlAndHostFields() {
        assertDoesNotThrow(() -> JdbcUrlGuard.validate("mysql", MYSQL, "127.0.0.1", 3306, "demo", null, null));
        assertThrows(ApiException.class, () -> JdbcUrlGuard.validate("mysql", MYSQL + "?autoDeserialize=true", "127.0.0.1", 3306, "demo", null, null));
        assertThrows(ApiException.class, () -> JdbcUrlGuard.validate("mysql", MYSQL, "evil host", 3306, "demo", null, null));
    }
}
