#!/bin/bash
set -e

cd "$(dirname "$0")"

echo "==> Building Java artifacts..."
mvn package -DskipTests -q

echo "==> Building Docker images..."
docker-compose build

echo "==> Starting services..."
docker-compose up -d

echo "==> Done. Portal: http://localhost:8081"
