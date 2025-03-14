#!/bin/bash

# Constants
DATAFLOW_SERVER_URL="http://localhost:9393"
TEST_TASK_NAME="timestamp-task"
TEST_SCHEDULE_NAME="timestamp-test-10sec"
TEST_CRON_EXPRESSION="*/10 * * * * ?"
TEST_TASK_PLATFORM="default"
TEST_TASK_FORMAT="YYYY/MM/dd-HH:mm:ss"

# Function to format JSON - uses jq if available, otherwise falls back to a more sophisticated formatting approach
format_json() {
    if command -v jq &> /dev/null; then
        # jq is available, use it
        echo "$1" | jq '.'
    else
        # jq is not available, use a more sophisticated approach for indentation
        echo "$1" | python3 -m json.tool 2>/dev/null || 
        python -m json.tool 2>/dev/null <<< "$1" || 
        # If Python is not available, use this awk-based formatter
        echo "$1" | awk '
        BEGIN { FS=""; RS=""; indent=0; instring=0; }
        {
            for(i=1; i<=length($0); i++) {
                c=substr($0,i,1);
                if(c=="\"" && substr($0,i-1,1)!="\\") instring=!instring;
                if(!instring) {
                    if(c=="{" || c=="[") {
                        printf("%s\n%s", c, sprintf("%*s", ++indent*2, ""));
                    } else if(c=="}" || c=="]") {
                        printf("\n%s%s", sprintf("%*s", --indent*2, ""), c);
                    } else if(c==",") {
                        printf("%s\n%s", c, sprintf("%*s", indent*2, ""));
                    } else {
                        printf("%s", c);
                    }
                } else {
                    printf("%s", c);
                }
            }
        }' ||
        # Last resort: basic formatting with sed
        echo "$1" | sed 's/,/,\n/g' | sed 's/{/{\n/g' | sed 's/}/\n}/g' | sed 's/\[/\[\n/g' | sed 's/\]/\n\]/g'
    fi
}

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
echo "Schedule details for ${TEST_SCHEDULE_NAME}:"
SCHEDULE_RESPONSE=$(curl -s "${DATAFLOW_SERVER_URL}/tasks/schedules/${TEST_SCHEDULE_NAME}")
format_json "$SCHEDULE_RESPONSE"

echo -e "\n5. Waiting for executions..."
sleep 10
echo "Task executions:"
EXECUTIONS_RESPONSE=$(curl -s "${DATAFLOW_SERVER_URL}/tasks/executions")
format_json "$EXECUTIONS_RESPONSE"
