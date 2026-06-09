package com.example.sensitivedetection.ambiguity.cache;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * 仅在 ambiguity.cache.enabled=true 时，自建数据源与 JdbcTemplate。
 * （全局已排除 Boot 的 DataSource 自动配置，避免缓存关闭时因无数据源而启动失败。）
 */
@Configuration
@ConditionalOnProperty(prefix = "ambiguity.cache", name = "enabled", havingValue = "true")
public class AmbiguityCacheDbConfig {

    @Bean
    public DataSource ambiguityDataSource(AmbiguityCacheProperties props) {
        AmbiguityCacheProperties.Datasource ds = props.getDatasource();
        HikariDataSource hikari = new HikariDataSource();
        hikari.setJdbcUrl(ds.getUrl());
        hikari.setUsername(ds.getUsername());
        hikari.setPassword(ds.getPassword());
        hikari.setDriverClassName(ds.getDriverClassName());
        // 百万级批量读写：适当放大连接池
        hikari.setMaximumPoolSize(16);
        hikari.setPoolName("ambiguity-cache-pool");
        return hikari;
    }

    @Bean
    public JdbcTemplate ambiguityJdbcTemplate(DataSource ambiguityDataSource) {
        return new JdbcTemplate(ambiguityDataSource);
    }
}
