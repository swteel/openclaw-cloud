#!/bin/bash
set -e

mkdir -p /root/.openclaw

# Render config from template using environment variables
envsubst < /etc/openclaw/config-template.json > /root/.openclaw/openclaw.json

echo "Starting openclaw gateway..."
exec openclaw gateway --bind lan
