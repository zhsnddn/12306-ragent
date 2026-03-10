package com.ming.agent12306.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;

/** 知识库表结构初始化配置 */
@Component
public class KnowledgeSchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public KnowledgeSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureColumnExists("knowledge_document", "progress_message", "ALTER TABLE knowledge_document ADD COLUMN progress_message VARCHAR(255) NULL");
        ensureColumnExists("knowledge_document", "uploaded_at", "ALTER TABLE knowledge_document ADD COLUMN uploaded_at TIMESTAMP NULL");
    }

    private void ensureColumnExists(String tableName, String columnName, String alterSql) {
        try (Connection connection = jdbcTemplate.getDataSource().getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            if (hasColumn(metaData, tableName, columnName)) {
                return;
            }
            try {
                jdbcTemplate.execute(alterSql);
            } catch (Exception ex) {
                if (isDuplicateColumn(ex)) {
                    return;
                }
                throw ex;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("初始化知识库表结构失败: " + tableName + "." + columnName, ex);
        }
    }

    private boolean hasColumn(DatabaseMetaData metaData, String tableName, String columnName) throws SQLException {
        try (ResultSet direct = metaData.getColumns(null, null, tableName, columnName)) {
            if (direct.next()) {
                return true;
            }
        }
        try (ResultSet lower = metaData.getColumns(null, null, tableName.toLowerCase(Locale.ROOT), columnName.toLowerCase(Locale.ROOT))) {
            return lower.next();
        }
    }

    private boolean isDuplicateColumn(Exception ex) {
        Throwable current = ex;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase(Locale.ROOT);
                if (normalized.contains("duplicate column")
                        || normalized.contains("duplicate column name")
                        || normalized.contains("already exists")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }
}
