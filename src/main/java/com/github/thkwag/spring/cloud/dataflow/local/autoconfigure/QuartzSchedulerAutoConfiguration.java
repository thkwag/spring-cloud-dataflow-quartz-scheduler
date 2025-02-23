package com.github.thkwag.spring.cloud.dataflow.local.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.dataflow.server.config.features.SchedulerConfiguration;
import org.springframework.cloud.deployer.spi.scheduler.Scheduler;
import org.springframework.cloud.deployer.spi.task.TaskLauncher;
import org.springframework.cloud.deployer.spi.local.LocalDeployerProperties;
import org.springframework.cloud.deployer.spi.local.LocalTaskLauncher;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.cloud.task.repository.TaskExplorer;
import org.springframework.cloud.task.repository.support.SimpleTaskExplorer;
import org.springframework.cloud.task.repository.support.TaskExecutionDaoFactoryBean;

import com.github.thkwag.spring.cloud.dataflow.local.scheduler.AutowiringSpringBeanJobFactory;
import com.github.thkwag.spring.cloud.dataflow.local.scheduler.QuartzScheduler;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Auto-configuration class for Quartz Scheduler integration with Spring Cloud Data Flow.
 * This class provides the necessary beans and configuration for running scheduled tasks
 * using Quartz Scheduler in a Spring Cloud Data Flow environment.
 *
 * <p>The configuration is activated when the following conditions are met:
 * <ul>
 *   <li>The scheduler feature is enabled in Spring Cloud Data Flow</li>
 *   <li>The platform type is set to 'quartz' in the configuration</li>
 *   <li>Required classes (SchedulerFactoryBean and SchedulerConfiguration) are present on the classpath</li>
 * </ul>
 *
 * <p>This configuration provides:
 * <ul>
 *   <li>Task execution and exploration capabilities</li>
 *   <li>Local task launcher configuration</li>
 *   <li>Quartz scheduler configuration with database persistence</li>
 *   <li>Automatic database type detection for PostgreSQL and MySQL/MariaDB</li>
 * </ul>
 *
 * @see org.springframework.cloud.dataflow.server.config.features.SchedulerConfiguration
 * @see org.springframework.scheduling.quartz.SchedulerFactoryBean
 * @see com.github.thkwag.spring.cloud.dataflow.local.scheduler.QuartzScheduler
 */
@AutoConfiguration
@Import(QuartzSchedulerSchemaAutoConfiguration.class)
@Conditional(SchedulerConfiguration.SchedulerConfigurationPropertyChecker.class)
@ConditionalOnProperty(name = "spring.cloud.dataflow.task.scheduler.local.platform.type", havingValue = "quartz")
public class QuartzSchedulerAutoConfiguration {

    /**
     * Creates a TaskExecutionDaoFactoryBean for managing task execution data persistence.
     *
     * @param dataSource The datasource to be used for task execution data
     * @return A configured TaskExecutionDaoFactoryBean
     */
    @Bean
    @ConditionalOnMissingBean
    public TaskExecutionDaoFactoryBean taskExecutionDaoFactoryBean(DataSource dataSource) {
        return new TaskExecutionDaoFactoryBean(dataSource);
    }

    /**
     * Creates a TaskExplorer for querying task execution information.
     *
     * @param daoFactoryBean The factory bean for creating task execution DAOs
     * @return A configured TaskExplorer
     */
    @Bean
    @ConditionalOnMissingBean
    public TaskExplorer taskExplorer(TaskExecutionDaoFactoryBean daoFactoryBean) {
        return new SimpleTaskExplorer(daoFactoryBean);
    }

    /**
     * Configures local deployer properties for task execution.
     * Sets up default values for task deployment in the local environment.
     *
     * @return Configured LocalDeployerProperties
     */
    @Bean
    @ConditionalOnMissingBean
    public LocalDeployerProperties localDeployerProperties() {
        LocalDeployerProperties properties = new LocalDeployerProperties();
        // Preserve task artifacts for debugging and monitoring
        properties.setDeleteFilesOnExit(false);
        // Configure classpath for loading task dependencies
        properties.setJavaOpts("-Dloader.path=BOOT-INF/lib");
        return properties;
    }

    /**
     * Creates a TaskLauncher for executing tasks in the local environment.
     *
     * @param properties The local deployer properties
     * @return A configured LocalTaskLauncher
     */
    @Bean
    @ConditionalOnMissingBean
    public TaskLauncher taskLauncher(LocalDeployerProperties properties) {
        return new LocalTaskLauncher(properties);
    }

    /**
     * Configures the Quartz SchedulerFactoryBean with database persistence and transaction support.
     * This bean is responsible for creating and managing the Quartz Scheduler instance.
     *
     * @param dataSource The datasource for Quartz job store
     * @param transactionManager The transaction manager for Quartz operations
     * @param beanFactory The bean factory for autowiring Quartz jobs
     * @return A configured SchedulerFactoryBean
     * @throws IllegalStateException if database type detection fails or unsupported database is used
     */
    @Bean
    @Lazy(false)
    public SchedulerFactoryBean schedulerFactoryBean(
            DataSource dataSource,
            PlatformTransactionManager transactionManager,
            AutowireCapableBeanFactory beanFactory) {
        
        SchedulerFactoryBean factoryBean = new SchedulerFactoryBean();
        factoryBean.setSchedulerName("spring-cloud-dataflow-scheduler");
        factoryBean.setDataSource(dataSource);
        factoryBean.setTransactionManager(transactionManager);
        factoryBean.setWaitForJobsToCompleteOnShutdown(true);
        factoryBean.setAutoStartup(true);
        
        // Configure Quartz properties
        Properties quartzProperties = new Properties();
        quartzProperties.setProperty("org.quartz.jobStore.useProperties", "true");
        
        // Detect database type and set appropriate delegate
        try {
            String url = dataSource.getConnection().getMetaData().getURL().toLowerCase();
            if (url.contains("postgresql")) {
                // PostgreSQL-specific delegate
                quartzProperties.setProperty("org.quartz.jobStore.driverDelegateClass", 
                    "org.quartz.impl.jdbcjobstore.PostgreSQLDelegate");
            } else if (url.contains("mysql") || url.contains("mariadb")) {
                // MySQL/MariaDB delegate
                quartzProperties.setProperty("org.quartz.jobStore.driverDelegateClass", 
                    "org.quartz.impl.jdbcjobstore.StdJDBCDelegate");
            } else {
                throw new IllegalStateException("Unsupported database type. Only PostgreSQL, MySQL, and MariaDB are supported.");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to detect database type", e);
        }
        
        // Set common Quartz properties
        quartzProperties.setProperty("org.quartz.jobStore.tablePrefix", "QRTZ_");
        quartzProperties.setProperty("org.quartz.scheduler.instanceName", "spring-cloud-dataflow-scheduler");
        quartzProperties.setProperty("org.quartz.scheduler.instanceId", "AUTO");
        factoryBean.setQuartzProperties(quartzProperties);
        
        // Configure job factory for Spring dependency injection
        AutowiringSpringBeanJobFactory jobFactory = new AutowiringSpringBeanJobFactory(beanFactory);
        factoryBean.setJobFactory(jobFactory);
        
        return factoryBean;
    }

    /**
     * Creates the Quartz Scheduler implementation for Spring Cloud Data Flow.
     *
     * @param schedulerFactoryBean The factory bean that creates the Quartz Scheduler
     * @return A configured QuartzScheduler instance
     */
    @Bean(name = "quartzScheduler")
    public Scheduler quartzScheduler(SchedulerFactoryBean schedulerFactoryBean) {
        return new QuartzScheduler(schedulerFactoryBean);
    }
} 