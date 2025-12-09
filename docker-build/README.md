# VLC Android Docker Build Environment

This directory contains a Docker Compose configuration to build the VLC Android application in an isolated environment.

## Prerequisites

- Docker
- Docker Compose

## Usage

1. Start the build environment:
   ```bash
   cd docker-build
   docker-compose up -d
   ```

2. Run a build command:
   ```bash
   docker-compose exec vlc-builder gradle :application:app:assembleDebug
   ```

3. Access the shell to run other commands:
   ```bash
   docker-compose exec vlc-builder bash
   ```

4. Stop the environment:
   ```bash
   docker-compose down
   ```

## Notes

- The source code is mounted at `/workspace`.
- Gradle cache is persisted in a docker volume `vlc-android-unified-view_gradle_cache`.
- The image used is the official VLC Android build image from VideoLAN registry.
