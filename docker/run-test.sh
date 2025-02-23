#!/bin/bash

# Constants
DATAFLOW_SERVER_URL="http://localhost:9393"
TEST_TASK_NAME="timestamp-task"
TEST_SCHEDULE_NAME="timestamp-test-10sec"
TEST_CRON_EXPRESSION="*/10 * * * * ?"
TEST_TASK_PLATFORM="default"
TEST_TASK_FORMAT="YYYY/MM/dd-HH:mm:ss"

echo "0. Cleaning up existing resources..."
# Delete specific schedule if exists
echo "Checking for schedule: ${TEST_SCHEDULE_NAME}"
if curl -s "${DATAFLOW_SERVER_URL}/tasks/schedules/${TEST_SCHEDULE_NAME}" | grep -q "scheduleName"; then
    echo "Deleting schedule: ${TEST_SCHEDULE_NAME}"
    curl -s -X DELETE "${DATAFLOW_SERVER_URL}/tasks/schedules/${TEST_SCHEDULE_NAME}"
    sleep 2
fi

curl -s -X DELETE "${DATAFLOW_SERVER_URL}/tasks/definitions/${TEST_TASK_NAME}" || true
sleep 2
curl -s -X DELETE "${DATAFLOW_SERVER_URL}/apps/task/timestamp" || true
sleep 2

echo -e "\n1. Registering timestamp task application..."
RESPONSE=$(curl -s -X POST "${DATAFLOW_SERVER_URL}/apps/task/timestamp" \
    -d 'uri=maven://io.spring:timestamp-task:3.0.0' \
    -H 'Content-Type: application/x-www-form-urlencoded')

if [ $? -ne 0 ]; then
    echo "Failed to register timestamp task application"
    exit 1
fi
echo "Application registered successfully"

echo -e "\n2. Creating task definition..."
sleep 2
RESPONSE=$(curl -s -X POST "${DATAFLOW_SERVER_URL}/tasks/definitions" \
    -d "name=${TEST_TASK_NAME}&definition=timestamp" \
    -H 'Content-Type: application/x-www-form-urlencoded')

if [ $? -ne 0 ]; then
    echo "Failed to create task definition"
    exit 1
fi
echo "Task definition created successfully"

echo -e "\n3. Creating schedule..."
sleep 2
SCHEDULE_PROPERTIES="scheduler.cron.expression=${TEST_CRON_EXPRESSION},app.timestamp.spring.cloud.task.platform=${TEST_TASK_PLATFORM}"
RESPONSE=$(curl -s -X POST "${DATAFLOW_SERVER_URL}/tasks/schedules" \
    -d "scheduleName=${TEST_SCHEDULE_NAME}&taskDefinitionName=${TEST_TASK_NAME}&platform=${TEST_TASK_PLATFORM}&properties=${SCHEDULE_PROPERTIES}&arguments=--format=${TEST_TASK_FORMAT}" \
    -H 'Content-Type: application/x-www-form-urlencoded')

if [ $? -ne 0 ]; then
    echo "Failed to create schedule"
    exit 1
fi
echo "Schedule created successfully"

echo -e "\n4. Checking schedule status..."
sleep 2
curl -s "${DATAFLOW_SERVER_URL}/tasks/schedules/${TEST_SCHEDULE_NAME}" | jq '.'

echo -e "\n5. Waiting for executions..."
sleep 10
curl -s "${DATAFLOW_SERVER_URL}/tasks/executions" | jq '.'
