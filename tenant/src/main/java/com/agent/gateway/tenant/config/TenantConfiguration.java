package com.agent.gateway.tenant.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * tenant 模块 Spring 装配入口。
 * 在存在 {@link DataSource} 时扫描 {@code com.agent.gateway.tenant}，启用仓库/策略/鉴权等组件；
 * Boot 会在有数据源时提供 {@link JdbcTemplate}，本类主要锚定组件扫描边界。
 */
@Configuration
@ComponentScan(basePackages = "com.agent.gateway.tenant")
@ConditionalOnBean(DataSource.class)
public class TenantConfiguration {

    // JdbcTemplate 由 Boot 在 DataSource 存在时提供；本类锚定扫描边界
}
