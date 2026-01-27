package com.syh.chat.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.Objects;

@Configuration
public class DataSourceConfig {
    
    @Bean
    public CommandLineRunner schemaMigrationRunner(DataSource dataSource) {
        return args -> {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource));
            try {
                jdbcTemplate.execute("ALTER TABLE message MODIFY COLUMN image_url LONGTEXT");
            } catch (Exception ignored) {
            }
            try {
                jdbcTemplate.execute("ALTER TABLE knowledge_document ADD COLUMN summary LONGTEXT");
            } catch (Exception ignored) {
            }
            try {
                jdbcTemplate.execute("ALTER TABLE generated_question ADD COLUMN question_type VARCHAR(20) NOT NULL DEFAULT 'single'");
            } catch (Exception ignored) {
            }
            try {
                jdbcTemplate.execute("ALTER TABLE generated_question MODIFY COLUMN options_json TEXT NULL");
            } catch (Exception ignored) {
            }
            try {
                jdbcTemplate.execute("UPDATE generated_question SET options_json = '[]' WHERE options_json IS NULL");
            } catch (Exception ignored) {
            }
            try {
                jdbcTemplate.execute("ALTER TABLE generated_question MODIFY COLUMN options_json TEXT NOT NULL DEFAULT '[]'");
            } catch (Exception ignored) {
            }
            try {
                jdbcTemplate.execute("UPDATE generated_question SET optionsJson = '[]' WHERE optionsJson IS NULL");
            } catch (Exception ignored) {
            }
            try {
                jdbcTemplate.execute("ALTER TABLE generated_question MODIFY COLUMN optionsJson TEXT NOT NULL DEFAULT '[]'");
            } catch (Exception ignored) {
            }
            try {
                jdbcTemplate.execute("ALTER TABLE generated_question MODIFY COLUMN answer VARCHAR(255) NOT NULL");
            } catch (Exception ignored) {
            }
        };
    }
}

