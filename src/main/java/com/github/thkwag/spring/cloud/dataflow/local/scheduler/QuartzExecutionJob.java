package com.github.thkwag.spring.cloud.dataflow.local.scheduler;

import org.quartz.*;
import org.springframework.cloud.dataflow.core.LaunchResponse;
import org.springframework.cloud.dataflow.server.service.TaskExecutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.quartz.QuartzJobBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Quartz Job implementation for executing Spring Cloud Data Flow tasks.
 * This job handles the execution of scheduled tasks within the Spring Cloud Data Flow environment.
 *
 * <p>Key features:
 * <ul>
 *   <li>Prevents concurrent execution of the same job instance</li>
 *   <li>Integrates with Spring Cloud Data Flow's task execution service</li>
 *   <li>Supports task properties and command-line arguments</li>
 *   <li>Provides proper error handling and logging</li>
 * </ul>
 *
 * <p>The job expects the following data in the JobDataMap:
 * <ul>
 *   <li>'properties': A JSON string containing:
 *     <ul>
 *       <li>definition.name: The task definition name</li>
 *       <li>deploymentProperties: Map of deployment properties</li>
 *       <li>commandlineArguments: List of command-line arguments</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <p>Example JobDataMap properties JSON:
 * <pre>{@code
 * {
 *   "definition": {
 *     "name": "myTask"
 *   },
 *   "deploymentProperties": {
 *     "app.myTask.logging.level": "DEBUG"
 *   },
 *   "commandlineArguments": ["--param1=value1"]
 * }
 * }</pre>
 *
 * @see org.springframework.cloud.dataflow.server.service.TaskExecutionService
 * @see org.springframework.cloud.deployer.spi.task.TaskLauncher
 */
@DisallowConcurrentExecution
public class QuartzExecutionJob extends QuartzJobBean {
    
    private static final Logger logger = LoggerFactory.getLogger(QuartzExecutionJob.class);
    private final TaskExecutionService taskService;
    private final ObjectMapper objectMapper;

    /**
     * Creates a new QuartzExecutionJob with the specified task execution service.
     *
     * @param taskService The Spring Cloud Data Flow task execution service
     */
    public QuartzExecutionJob(TaskExecutionService taskService) {
        this.taskService = taskService;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Executes the scheduled task using Spring Cloud Data Flow's task execution service.
     * This method is called by the Quartz scheduler when the trigger fires.
     *
     * <p>The execution process involves:
     * <ol>
     *   <li>Extracting task information from the job data</li>
     *   <li>Converting deployment properties and arguments</li>
     *   <li>Launching the task through TaskExecutionService</li>
     *   <li>Logging the execution results</li>
     * </ol>
     *
     * @param context The job execution context containing job data and runtime information
     * @throws JobExecutionException if task execution fails
     */
    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        String scheduleName = context.getJobDetail().getKey().getName();
        
        try {
            // Parse task execution information from job data
            JsonNode jsonNode = objectMapper.readTree(
                context.getMergedJobDataMap().getString("properties"));
            
            String taskName = jsonNode.path("definition").path("name").asText();
            
            // Convert deployment properties with type safety
            Map<String, String> properties = jsonNode.path("deploymentProperties").isEmpty()
                ? new HashMap<>()
                : objectMapper.convertValue(jsonNode.path("deploymentProperties"), 
                    new TypeReference<Map<String, String>>() {});
            
            // Convert command-line arguments with type safety
            List<String> arguments = jsonNode.path("commandlineArguments").isEmpty()
                ? new ArrayList<>()
                : objectMapper.convertValue(jsonNode.path("commandlineArguments"), 
                    new TypeReference<List<String>>() {});
            
            // Launch the task through Spring Cloud Data Flow
            LaunchResponse response = taskService.executeTask(taskName, properties, arguments);
            logger.info("Scheduled task launched - name: {}, schedule: {}, executionId: {}", 
                taskName, scheduleName, response.getExecutionId());
            
        } catch (Exception e) {
            logger.error("Failed to launch scheduled task: {} - {}", scheduleName, e.getMessage());
            throw new JobExecutionException(e);
        }
    }
} 