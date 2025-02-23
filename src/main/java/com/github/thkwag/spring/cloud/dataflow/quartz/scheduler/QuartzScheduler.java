package com.github.thkwag.spring.cloud.dataflow.quartz.scheduler;

import org.quartz.*;
import org.quartz.impl.matchers.GroupMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.deployer.spi.scheduler.Scheduler;
import org.springframework.cloud.deployer.spi.scheduler.ScheduleInfo;
import org.springframework.cloud.deployer.spi.scheduler.ScheduleRequest;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Implementation of Spring Cloud Data Flow's Scheduler interface using Quartz.
 * This class provides task scheduling capabilities through Quartz for Spring Cloud Data Flow.
 *
 * <p>Key features:
 * <ul>
 *   <li>Schedule management (create, delete, list)</li>
 *   <li>Cron-based scheduling</li>
 *   <li>Task property and argument support</li>
 *   <li>Integration with Spring Cloud Data Flow's task platform</li>
 * </ul>
 *
 * <p>The scheduler stores the following information for each task:
 * <ul>
 *   <li>Task definition name</li>
 *   <li>Cron expression</li>
 *   <li>Deployment properties</li>
 *   <li>Command-line arguments</li>
 * </ul>
 *
 * @see org.springframework.cloud.deployer.spi.scheduler.Scheduler
 * @see org.quartz.Scheduler
 * @see QuartzExecutionJob
 */
public class QuartzScheduler implements Scheduler {

    private static final Logger logger = LoggerFactory.getLogger(QuartzScheduler.class);
    private final org.quartz.Scheduler scheduler;
    private final ObjectMapper objectMapper;

    /**
     * Creates a new QuartzScheduler with the specified factory bean.
     *
     * @param schedulerFactoryBean The factory bean that creates the Quartz Scheduler
     */
    public QuartzScheduler(SchedulerFactoryBean schedulerFactoryBean) {
        this.scheduler = schedulerFactoryBean.getObject();
        this.objectMapper = new ObjectMapper()
            .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);
	}

	/**
	 * Schedules a new task with the specified configuration.
	 * If a schedule with the same name already exists, it will be replaced.
	 *
	 * @param scheduleRequest The request containing schedule configuration
	 * @throws IllegalStateException if scheduling fails
	 * @throws IllegalArgumentException if cron expression is missing
	 */
	@Override
	public void schedule(ScheduleRequest scheduleRequest) {
        try {
            String scheduleName = scheduleRequest.getScheduleName();
            String taskDefinitionName = scheduleRequest.getDefinition().getName();
            Map<String, String> properties = new HashMap<>(scheduleRequest.getDeploymentProperties());
            
            logger.info("Scheduling task - name: {}, definition: {}, properties: {}", 
                scheduleName, taskDefinitionName, properties);
            
            // Check and remove existing job
            JobKey jobKey = new JobKey(scheduleName);
            if (scheduler.checkExists(jobKey)) {
                logger.info("Found existing job with name: {}. Current triggers: {}", 
                    scheduleName, scheduler.getTriggersOfJob(jobKey));
                logger.info("Deleting existing job: {}", scheduleName);
                scheduler.deleteJob(jobKey);
            }

            // List current jobs for debugging
            logger.info("Current jobs before scheduling:");
            for (JobKey existingJobKey : scheduler.getJobKeys(GroupMatcher.anyGroup())) {
                logger.info("Job: {}, Triggers: {}", existingJobKey.getName(), 
                    scheduler.getTriggersOfJob(existingJobKey));
            }
            
            // Process cron expression from various possible properties
            String cronExpression = properties.get("spring.cloud.scheduler.cron.expression");
            if (cronExpression == null) {
                cronExpression = properties.get("scheduler.cron.expression");
            }
            if (cronExpression == null) {
                cronExpression = properties.get("spring.cloud.deployer.cron.expression");
            }
            
            if (cronExpression == null || cronExpression.trim().isEmpty()) {
                logger.error("Cron expression not found in properties: {}", properties);
                throw new IllegalArgumentException(
                    "Cron expression must be specified in deployment properties using one of:\n" +
                    "- spring.cloud.scheduler.cron.expression\n" +
                    "- scheduler.cron.expression");
            }

            // Clean up cron expression properties
            properties.remove("spring.cloud.scheduler.cron.expression");
            properties.remove("spring.cloud.deployer.cron.expression");
            properties.remove("scheduler.cron.expression");

            logger.info("Using cron expression: {}", cronExpression);

            // Create job data with required information
            Map<String, Object> jobData = new HashMap<>();
            Map<String, Object> schedulerData = new HashMap<>();
            schedulerData.put("definition", Map.of("name", taskDefinitionName));
            schedulerData.put("deploymentProperties", properties);
            schedulerData.put("commandlineArguments", scheduleRequest.getCommandlineArguments());
            schedulerData.put("cronExpression", cronExpression);
            jobData.put("properties", objectMapper.writeValueAsString(schedulerData));

            // Create and configure job
            JobDetail jobDetail = JobBuilder.newJob(QuartzExecutionJob.class)
                .withIdentity(scheduleName)
                .usingJobData(new JobDataMap(jobData))
                .build();

            // Create and configure trigger
            CronTrigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(scheduleName)
                .withSchedule(CronScheduleBuilder.cronSchedule(cronExpression))
                .build();

            // Schedule the job
            scheduler.scheduleJob(jobDetail, trigger);
            
            logger.info("Successfully scheduled task - name: {}, cron: {}", scheduleName, cronExpression);
            
        } catch (Exception e) {
            logger.error("Failed to schedule task", e);
            throw new IllegalStateException("Failed to schedule task: " + e.getMessage(), e);
		}
	}

	/**
	 * Unschedules (deletes) a scheduled task.
	 *
	 * @param scheduleName The name of the schedule to delete
	 * @throws IllegalStateException if unscheduling fails
	 */
	@Override
	public void unschedule(String scheduleName) {
        try {
            JobKey jobKey = new JobKey(scheduleName);
            if (scheduler.checkExists(jobKey)) {
                scheduler.deleteJob(jobKey);
                logger.info("Unscheduled task: {}", scheduleName);
            } else {
                logger.warn("No job found to unschedule: {}", scheduleName);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to unschedule task: " + scheduleName, e);
		}
	}

	/**
	 * Lists all schedules for a specific task definition.
	 *
	 * @param taskDefinitionName The task definition name to filter by
	 * @return List of schedule information matching the task definition
	 */
	@Override
	public List<ScheduleInfo> list(String taskDefinitionName) {
        try {
            return scheduler.getJobKeys(GroupMatcher.anyGroup()).stream()
                .map(jobKey -> createScheduleInfo(jobKey, taskDefinitionName))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("Failed to list schedules", e);
            return new ArrayList<>();
        }
	}

	/**
	 * Lists all schedules in the system.
	 *
	 * @return List of all schedule information
	 */
	@Override
	public List<ScheduleInfo> list() {
        try {
            return scheduler.getJobKeys(GroupMatcher.anyGroup()).stream()
                .map(jobKey -> createScheduleInfo(jobKey, null))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("Failed to list schedules", e);
            return new ArrayList<>();
        }
    }

    /**
     * Creates a ScheduleInfo object from a job key.
     * This helper method extracts schedule information from the Quartz job data.
     *
     * @param jobKey The job key to get information for
     * @param filterTaskDefinitionName Optional task definition name to filter by
     * @return ScheduleInfo object or null if the job doesn't match criteria
     */
    private ScheduleInfo createScheduleInfo(JobKey jobKey, String filterTaskDefinitionName) {
        try {
            JobDetail jobDetail = scheduler.getJobDetail(jobKey);
            if (jobDetail == null) return null;

            JobDataMap jobDataMap = jobDetail.getJobDataMap();
            String properties = jobDataMap.getString("properties");
            if (properties == null) return null;

            List<? extends Trigger> triggers = scheduler.getTriggersOfJob(jobKey);
            if (triggers == null || triggers.isEmpty()) return null;

            Trigger trigger = triggers.get(0);
            if (!(trigger instanceof CronTrigger)) return null;

            JsonNode rootNode = objectMapper.readTree(properties);
            String taskDefinitionName = rootNode.path("definition").path("name").asText();
            
            // Filter by task definition name if specified
            if (filterTaskDefinitionName != null && !filterTaskDefinitionName.equals(taskDefinitionName)) {
                return null;
            }
            if (taskDefinitionName == null || taskDefinitionName.isEmpty()) return null;

            String cronExpression = ((CronTrigger) trigger).getCronExpression();
            ScheduleInfo scheduleInfo = new ScheduleInfo();
            scheduleInfo.setScheduleName(jobKey.getName());
            scheduleInfo.setTaskDefinitionName(taskDefinitionName);

            // Set schedule properties
            Map<String, String> scheduleProperties = new HashMap<>();
            scheduleProperties.put("spring.cloud.scheduler.cron.expression", cronExpression);
            scheduleProperties.put("platform", "local");

            // Add deployment properties
            JsonNode deploymentPropertiesNode = rootNode.path("deploymentProperties");
            if (!deploymentPropertiesNode.isMissingNode()) {
                Map<String, String> deploymentProperties = objectMapper.convertValue(
                    deploymentPropertiesNode,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {}
                );
                scheduleProperties.putAll(deploymentProperties);
            }

            scheduleInfo.setScheduleProperties(scheduleProperties);
            return scheduleInfo;
        } catch (Exception e) {
            logger.warn("Failed to get schedule info for job: {}", jobKey.getName(), e);
            return null;
        }
	}
}