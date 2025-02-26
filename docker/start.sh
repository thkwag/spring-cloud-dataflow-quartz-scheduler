#!/bin/bash

# Get the directory where the script is located
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_DIR="$( cd "$SCRIPT_DIR/.." && pwd )"

echo "Building project from: $PROJECT_DIR"
cd "$PROJECT_DIR" && ./gradlew clean build

echo "Starting docker compose from: $SCRIPT_DIR"
cd "$SCRIPT_DIR" && \
docker-compose \
-f docker-compose.yml \
-f docker-compose-rabbitmq.yml \
-f docker-compose-postgres.yml \
-f docker-compose-quartz.yml down && \
docker-compose \
-f docker-compose.yml \
-f docker-compose-rabbitmq.yml \
-f docker-compose-postgres.yml \
-f docker-compose-quartz.yml up -d