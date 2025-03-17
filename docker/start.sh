#!/bin/bash

# Get the directory where the script is located
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_DIR="$( cd "$SCRIPT_DIR/.." && pwd )"

echo "Building project from: $PROJECT_DIR"
cd "$PROJECT_DIR" && ./gradlew clean build

# Copy JAR file to docker/lib directory
JAR_FILE=$(find "$PROJECT_DIR/build/libs" -name "*.jar" | head -n 1)
JAR_FILENAME=$(basename "$JAR_FILE")
LIB_DIR="$SCRIPT_DIR/lib"

echo "Copying JAR file: $JAR_FILENAME to $LIB_DIR"
# Remove existing JAR file or directory with the same name if it exists
if [ -e "$LIB_DIR/$JAR_FILENAME" ]; then
    echo "Removing existing file or directory: $LIB_DIR/$JAR_FILENAME"
    rm -rf "$LIB_DIR/$JAR_FILENAME"
fi
# Copy the new JAR file
cp "$JAR_FILE" "$LIB_DIR/"

echo "Starting docker compose from: $SCRIPT_DIR"
cd "$SCRIPT_DIR" && \
docker-compose \
-f docker-compose.yml \
-f docker-compose-dood.yml \
-f docker-compose-rabbitmq.yml \
-f docker-compose-postgres.yml \
-f docker-compose-quartz.yml down && \
docker-compose \
-f docker-compose.yml \
-f docker-compose-dood.yml \
-f docker-compose-rabbitmq.yml \
-f docker-compose-postgres.yml \
-f docker-compose-quartz.yml up -d