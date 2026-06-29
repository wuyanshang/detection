package com.example.sensitivedetection.security.config;

import com.zaxxer.hikari.HikariDataSource;
import lombok.Data;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * 仅在 security-classification.db.enabled=true 时，自建数据源与 JdbcTemplate，
 * 供结果缓存表与同义词规则表使用。
 * （全局已排除 Boot 的 DataSource 自动配置，关闭时无需数据库即可启动。）
 */
@Configuration
@ConditionalOnProperty(prefix = "security-classification.db", name = "enabled", havingValue = "true")
public class SecurityDbConfig {

    @Bean
    public DataSource securityDataSource(SecurityDbProperties props) {
        SecurityDbProperties.Datasource ds = props.getDatasource();
        HikariDataSource hikari = new HikariDataSource();
        hikari.setJdbcUrl(ds.getUrl());
        hikari.setUsername(ds.getUsername());
        hikari.setPassword(ds.getPassword());
        hikari.setDriverClassName(ds.getDriverClassName());
        hikari.setMaximumPoolSize(16);
        hikari.setPoolName("security-classification-pool");
        return hikari;
    }

    @Bean
    public JdbcTemplate securityJdbcTemplate(DataSource securityDataSource) {
        return new JdbcTemplate(securityDataSource);
    }

    /**
     * DB 连接配置。与 EsProperties 等分开，避免与歧义缓存数据源冲突。
     */
    @Data
    @Component
    @ConfigurationProperties(prefix = "security-classification.db")
    public static class SecurityDbProperties {
        private boolean enabled = false;
        private Datasource datasource = new Datasource();

        @Data
        public static class Datasource {
            private String url;
            private String username;
            private String password;
            private String driverClassName = "com.mysql.cj.jdbc.Driver";
        }
    }
}
