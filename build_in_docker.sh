#!/bin/bash

# Check if docker is installed
if ! command -v docker &> /dev/null; then
    echo "Error: docker is not installed."
    exit 1
fi

# Check if docker-compose is installed
if ! command -v docker-compose &> /dev/null; then
    # Try docker compose (plugin)
    if ! docker compose version &> /dev/null; then
        echo "Error: docker-compose is not installed."
        exit 1
    fi
    DOCKER_COMPOSE="docker compose"
else
    DOCKER_COMPOSE="docker-compose"
fi

echo "Starting build environment..."
cd docker-build
$DOCKER_COMPOSE up -d

echo "Running build..."
$DOCKER_COMPOSE exec vlc-builder git config --global --add safe.directory /workspace
$DOCKER_COMPOSE exec vlc-builder gradle :application:app:assembleDebug

echo "Build complete. Artifacts are in application/app/build/outputs/apk/debug/"
