# Spring Cloud Data Flow Quartz Scheduler

A scheduling solution for Spring Cloud Data Flow that works in any environment - local, VM, or Kubernetes.

## Why This Project?

Spring Cloud Data Flow only supports scheduling in Kubernetes environments. According to the [official documentation](https://dataflow.spring.io/docs/feature-guides/batch/scheduling/#scheduling-a-batch-job):

> "Spring Cloud Data Flow does not offer an out-of-the-box solution for scheduling task launches on the local platform."

This project provides a complete scheduling solution that:
- Works in any environment (local, VM, cloud) without Kubernetes
- Integrates seamlessly with Spring Cloud Data Flow
- Uses Quartz for reliable and flexible scheduling


![alt text](docs/images/scdf-quartz-scheduler.png)


## Features

- **Simple Setup**: Easy to run in any environment
- **Full Integration**: Works with existing Spring Cloud Data Flow tasks
- **Flexible Scheduling**: Supports cron expressions and various triggers
- **Dashboard**: Includes UI for schedule management
- **High Availability**: Supports clustered environments
- **Job History**: Tracks all task executions

## How It Works

This library automatically configures the Quartz scheduler to be used instead of the local scheduler when the platform type is set to `quartz`. The implementation:

1. Provides a Quartz Scheduler implementation for Spring Cloud Data Flow
2. Automatically creates the necessary database tables
3. Sets the Quartz scheduler as the primary scheduler with higher priority
4. Overrides the local scheduler with a no-op implementation
5. Supports all standard SCDF scheduling operations

## Quick Start

### Prerequisites

- JDK 17+
- Spring Cloud Data Flow Server
- MySQL/PostgreSQL
- Docker and Docker Compose (for the quickest setup)

### Run with Docker

The project includes shell scripts to manage the complete lifecycle:

```bash
# Start the environment (builds the project and starts all containers)
./docker/start.sh

# Run a test to verify scheduler functionality
./docker/run-test.sh

# Stop all containers
./docker/stop.sh
```

### Integration into Existing SCDF Setup

To integrate this scheduler into your existing Spring Cloud Data Flow setup:

1. Add the JAR file to your SCDF server's classpath
2. Configure the following properties (reference `docker/docker-compose-quartz.yml` for examples):

```yaml
# SCDF Scheduler Configuration
spring.cloud.dataflow.features.schedules-enabled: true
spring.cloud.dataflow.features.tasks-enabled: true
spring.cloud.dataflow.task.scheduler.local.platform-type: quartz

# Quartz Configuration
spring.quartz.job-store-type: jdbc
spring.quartz.jdbc.initialize-schema: always
spring.quartz.auto-startup: true

# Database-specific Quartz Configuration (PostgreSQL example)
spring.quartz.properties.org.quartz.jobStore.driverDelegateClass: org.quartz.impl.jdbcjobstore.PostgreSQLDelegate
spring.quartz.properties.org.quartz.jobStore.class: org.quartz.impl.jdbcjobstore.JobStoreTX
spring.quartz.properties.org.quartz.jobStore.useProperties: true
spring.quartz.properties.org.quartz.scheduler.instanceName: spring-cloud-dataflow-scheduler
spring.quartz.properties.org.quartz.scheduler.instanceId: AUTO
spring.quartz.properties.org.quartz.jobStore.tablePrefix: QRTZ_
spring.quartz.properties.org.quartz.jobStore.isClustered: true
spring.quartz.properties.org.quartz.threadPool.class: org.quartz.simpl.SimpleThreadPool
spring.quartz.properties.org.quartz.threadPool.threadCount: 10
```

## Documentation

- [Spring Cloud Data Flow](https://dataflow.spring.io/docs/feature-guides/batch/scheduling/)
- [Quartz Scheduler](http://www.quartz-scheduler.org/documentation/)

## License

MIT License - see the [LICENSE](LICENSE) file for details. 