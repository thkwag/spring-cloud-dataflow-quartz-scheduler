package com.github.thkwag.spring.cloud.dataflow.quartz.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.cloud.dataflow.server.config.features.SchedulerConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.init.DataSourceInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;

import javax.sql.DataSource;
import javax.annotation.PostConstruct;

/**
 * Auto-configuration class for initializing Quartz Scheduler database schema.
 * This class is responsible for managing the database schema initialization for Quartz Scheduler.
 * It defers the actual schema creation to Spring Boot's Quartz auto-configuration.
 *
 * <p>The configuration is activated when the following conditions are met:
 * <ul>
 *   <li>SchedulerFactoryBean and SchedulerConfiguration classes are present on the classpath</li>
 *   <li>The auto-create-tables property is set to true (default)</li>
 * </ul>
 *
 * <p>Key features:
 * <ul>
 *   <li>Delegates schema initialization to Spring Boot's Quartz auto-configuration</li>
 *   <li>Supports both PostgreSQL and MySQL/MariaDB databases</li>
 *   <li>Configurable through spring.cloud.dataflow.scheduler.quartz.auto-create-tables property</li>
 * </ul>
 *
 * <p>Note: This configuration runs before the main SchedulerConfiguration to ensure
 * the database schema is ready before the scheduler starts.
 *
 * @see org.springframework.cloud.dataflow.server.config.features.SchedulerConfiguration
 * @see org.springframework.scheduling.quartz.SchedulerFactoryBean
 */
@AutoConfiguration
@AutoConfigureBefore(SchedulerConfiguration.class)
@ConditionalOnClass({SchedulerFactoryBean.class, SchedulerConfiguration.class})
public class QuartzSchedulerSchemaAutoConfiguration {
    
    private static final Logger logger = LoggerFactory.getLogger(QuartzSchedulerSchemaAutoConfiguration.class);
    private final DataSourceProperties dataSourceProperties;

    /**
     * Property to control whether Quartz tables should be automatically created.
     * Defaults to true if not specified.
     */
    @Value("${spring.cloud.dataflow.scheduler.quartz.auto-create-tables:true}")
    private boolean autoCreateTables;

    /**
     * Constructor for QuartzSchedulerSchemaAutoConfiguration.
     *
     * @param dataSourceProperties Properties containing database connection information
     */
    public QuartzSchedulerSchemaAutoConfiguration(DataSourceProperties dataSourceProperties) {
        this.dataSourceProperties = dataSourceProperties;
        logger.info("=== QuartzSchedulerSchemaAutoConfiguration constructor called ===");
    }

    /**
     * Initializes the configuration and logs important settings.
     * This method runs after the bean properties have been set.
     */
    @PostConstruct
    public void init() {
        logger.info("QuartzSchedulerSchemaAutoConfiguration initialized");
        logger.info("  - Database URL: {}", dataSourceProperties.getUrl());
        logger.info("  - Auto create tables: {}", autoCreateTables);
    }

    /**
     * Creates a DataSourceInitializer that defers schema initialization to Spring Boot's Quartz auto-configuration.
     * The actual schema creation is handled by setting spring.quartz.jdbc.initialize-schema=always in the configuration.
     *
     * <p>This bean is only created when auto-create-tables is set to true (default behavior).
     * The schema initialization is disabled here as it's handled by Spring Boot's Quartz auto-configuration.
     *
     * @param dataSource The datasource to be used for schema initialization
     * @return A configured but disabled DataSourceInitializer
     */
    @Bean
    @ConditionalOnProperty(name = "spring.cloud.dataflow.scheduler.quartz.auto-create-tables", havingValue = "true", matchIfMissing = true)
    public DataSourceInitializer schedulerDataSourceInitializer(DataSource dataSource) {
        logger.info("Initializing scheduler schema. Auto-create tables: {}", autoCreateTables);
        
        // Create DataSourceInitializer but disable it as schema initialization
        // is handled by Spring Boot's Quartz auto-configuration through
        // spring.quartz.jdbc.initialize-schema=always setting
        DataSourceInitializer initializer = new DataSourceInitializer();
        initializer.setDataSource(dataSource);
        initializer.setEnabled(false);
        
        logger.info("Quartz schema initialization is handled by Spring Boot's Quartz auto-configuration");
        return initializer;
    }
} 