#!/bin/bash

echo "Building the maven project..."
if ! ./mvnw clean install; then
  echo "Maven build failed. Exiting."
  exit 1
fi

IMAGE_TAG=$(./mvnw help:evaluate -Dexpression=imageTag -q -DforceStdout)

echo "Building Docker image with tag $IMAGE_TAG..."
if ! docker build -t "$IMAGE_TAG" .; then
  echo "Docker build failed. Exiting."
  exit 1
fi

echo "Docker image built successfully with tag $IMAGE_TAG."
