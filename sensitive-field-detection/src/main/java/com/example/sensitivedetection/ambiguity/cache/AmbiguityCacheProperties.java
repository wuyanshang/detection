package com.example.sensitivedetection.ambiguity.cache;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 歧义缓存配置。enabled=false 时整个缓存关闭、不需要数据库。
 */
@Data
@Component
@ConfigurationProperties(prefix = "ambiguity.cache")
public class AmbiguityCacheProperties {

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
