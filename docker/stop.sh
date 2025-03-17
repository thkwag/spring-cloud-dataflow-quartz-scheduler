#!/bin/bash

# Get the directory where the script is located
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

echo "Stopping docker compose from: $SCRIPT_DIR"
cd "$SCRIPT_DIR" && \
docker-compose \
-f docker-compose.yml \
-f docker-compose-dood.yml \
-f docker-compose-postgres.yml \
-f docker-compose-rabbitmq.yml \
-f docker-compose-quartz.yml down