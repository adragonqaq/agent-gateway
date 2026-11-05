package com.agent.gateway.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * 应用启动时执行数据库 schema 初始化。
 * <p>在单例创建阶段（{@link PostConstruct}）执行，早于 TaskScheduler 启动，
 * 避免 {@code ConfirmExpiryScheduler} 等定时任务在建表前扫库。
 * 使用 classpath 下的 {@code db/schema.sql}，允许已存在对象时继续，便于幂等部署。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SchemaInitializer {

    private static final Logger log = LoggerFactory.getLogger(SchemaInitializer.class);

    private final DataSource dataSource;

    /**
     * @param dataSource 业务数据源
     */
    public SchemaInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * 在 Bean 初始化完成后立即加载并执行 schema 脚本。
     */
    @PostConstruct
    public void initialize() {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new ClassPathResource("db/schema.sql"));
        // H2/Postgres 幂等建表；单句失败不阻断后续（IF NOT EXISTS）
        populator.setContinueOnError(true);
        populator.execute(dataSource);
        log.info("数据库 schema 已应用（classpath:db/schema.sql）");
    }
}
