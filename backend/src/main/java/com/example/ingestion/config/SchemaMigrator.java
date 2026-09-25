package com.example.ingestion.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

/**
 * 为已存在的元数据库补齐后续版本新增的列。
 * schema.sql 使用 CREATE TABLE IF NOT EXISTS, 对已建表不会生效, 因此这里做幂等的加列迁移。
 * 仅新增列, 不做任何破坏性变更; 单条失败只告警, 不阻断启动。
 */
@Slf4j
@Order(0)
@Component
public class SchemaMigrator implements ApplicationRunner {
    private record Migration(String table, String column, String definition) {}

    private static final List<Migration> MIGRATIONS = List.of(
            new Migration("sys_user", "must_change_password", "TINYINT NOT NULL DEFAULT 0"),
            new Migration("audit_log", "chain_version", "INT NOT NULL DEFAULT 1")
    );

    private final DataSource dataSource;

    public SchemaMigrator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (Migration migration : MIGRATIONS) {
            try {
                apply(migration);
            } catch (Exception error) {
                log.warn("结构补齐失败 {}.{}: {}", migration.table(), migration.column(), error.getMessage());
            }
        }
    }

    private void apply(Migration migration) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            if (columnExists(connection, migration.table(), migration.column())) return;
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE " + migration.table() + " ADD COLUMN " + migration.column() + " " + migration.definition());
            }
            log.info("结构补齐: {} 新增列 {}", migration.table(), migration.column());
        }
    }

    private boolean columnExists(Connection connection, String table, String column) throws Exception {
        try (ResultSet result = connection.getMetaData().getColumns(connection.getCatalog(), null, table, column)) {
            return result.next();
        }
    }
}
