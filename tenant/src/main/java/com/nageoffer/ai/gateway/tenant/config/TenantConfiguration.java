package com.nageoffer.ai.gateway.tenant.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
@ComponentScan(basePackages = "com.nageoffer.ai.gateway.tenant")
@ConditionalOnBean(DataSource.class)
public class TenantConfiguration {

    // JdbcTemplate is provided by Boot when DataSource exists; this class anchors scanning.
}
