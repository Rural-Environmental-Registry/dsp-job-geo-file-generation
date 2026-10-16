package br.car.dsp_geo_file.configuration;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Target DataSource — {@code dsp-db}, where the territorial regeneration flags live.
 */
@Configuration
public class TargetDataSourceConfig {

    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.target")
    public DataSourceProperties targetDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.target.hikari")
    public HikariConfig targetHikariConfig() {
        HikariConfig config = new HikariConfig();
        DataSourceProperties properties = targetDataSourceProperties();
        config.setJdbcUrl(properties.getUrl());
        config.setUsername(properties.getUsername());
        config.setPassword(properties.getPassword());
        config.setDriverClassName(properties.getDriverClassName());
        // Allow pool startup even if the DB is down; unified connectivity check fails later.
        config.setInitializationFailTimeout(-1);
        // Defense in depth: session TZ must not reinterpret TIMESTAMP values.
        config.setConnectionInitSql("SET TIME ZONE 'UTC'");
        return config;
    }

    @Bean(name = "targetDataSource")
    public DataSource targetDataSource() {
        return new HikariDataSource(targetHikariConfig());
    }

    @Bean(name = "targetJdbcTemplate")
    public JdbcTemplate targetJdbcTemplate(@Qualifier("targetDataSource") DataSource targetDataSource) {
        return new JdbcTemplate(targetDataSource);
    }
}
