package com.github.thkwag.spring.cloud.dataflow.quartz.scheduler;

import org.jetbrains.annotations.NotNull;
import org.quartz.spi.TriggerFiredBundle;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;

/**
 * Custom implementation of SpringBeanJobFactory that adds autowiring support to Quartz Job instances.
 * This factory ensures that Spring dependency injection works properly for Quartz Jobs.
 *
 * <p>When Quartz creates a new Job instance, this factory intercepts the creation process and
 * applies Spring's autowiring to the newly created Job instance. This allows the Job to use
 * Spring-managed beans through {@code @Autowired} annotations.
 *
 * <p>Key features:
 * <ul>
 *   <li>Extends Spring's default SpringBeanJobFactory</li>
 *   <li>Adds support for dependency injection in Quartz Jobs</li>
 *   <li>Integrates Quartz job instantiation with Spring's bean factory</li>
 *   <li>Enables use of {@code @Autowired} in Quartz Job classes</li>
 * </ul>
 *
 * <p>Usage example:
 * <pre>{@code
 * @Bean
 * public SchedulerFactoryBean schedulerFactoryBean(AutowireCapableBeanFactory beanFactory) {
 *     SchedulerFactoryBean factory = new SchedulerFactoryBean();
 *     AutowiringSpringBeanJobFactory jobFactory = new AutowiringSpringBeanJobFactory(beanFactory);
 *     factory.setJobFactory(jobFactory);
 *     return factory;
 * }
 * }</pre>
 *
 * @see org.springframework.scheduling.quartz.SpringBeanJobFactory
 * @see org.springframework.beans.factory.config.AutowireCapableBeanFactory
 * @see org.quartz.spi.JobFactory
 */
public class AutowiringSpringBeanJobFactory extends SpringBeanJobFactory {
    
    // Spring's bean factory used for autowiring job instances
    private final AutowireCapableBeanFactory beanFactory;

    /**
     * Creates a new AutowiringSpringBeanJobFactory with the specified bean factory.
     *
     * @param beanFactory The Spring bean factory to be used for autowiring job instances
     * @throws IllegalArgumentException if beanFactory is null
     */
    public AutowiringSpringBeanJobFactory(AutowireCapableBeanFactory beanFactory) {
        if (beanFactory == null) {
            throw new IllegalArgumentException("BeanFactory must not be null");
        }
        this.beanFactory = beanFactory;
    }

    /**
     * Creates a new job instance and applies Spring autowiring to it.
     * This method is called by Quartz when a new job instance needs to be created.
     *
     * <p>The process involves:
     * <ol>
     *   <li>Creating the job instance using the superclass implementation</li>
     *   <li>Applying Spring autowiring to the created instance</li>
     *   <li>Returning the fully configured job instance</li>
     * </ol>
     *
     * @param bundle The TriggerFiredBundle containing job creation details
     * @return A fully configured job instance with autowired dependencies
     * @throws Exception if job instance creation fails
     */
    @NotNull
    @Override
    protected Object createJobInstance(@NotNull TriggerFiredBundle bundle) throws Exception {
        // Create the job instance using the default SpringBeanJobFactory behavior
        Object job = super.createJobInstance(bundle);
        
        // Apply Spring autowiring to the created job instance
        beanFactory.autowireBean(job);
        
        return job;
    }
} 