package com.agent.gateway.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * 应用启动时执行数据库 schema 初始化。
 * 使用 classpath 下的 {@code db/schema.sql}，允许已存在对象时继续，便于幂等部署。
 */
@Component
public class SchemaInitializer implements ApplicationRunner {

    private final DataSource dataSource;

    /**
     * @param dataSource 业务数据源
     */
    public SchemaInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * 在应用就绪后加载并执行 schema 脚本。
     *
     * @param args 启动参数（未使用）
     */
    @Override
    public void run(ApplicationArguments args) {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new ClassPathResource("db/schema.sql"));
        populator.setContinueOnError(true);
        populator.execute(dataSource);
    }
}
