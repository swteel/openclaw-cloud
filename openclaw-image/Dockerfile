FROM node:22-slim

RUN apt-get update && apt-get install -y \
    chromium \
    curl \
    gettext-base \
    git \
    ca-certificates \
    --no-install-recommends \
    && rm -rf /var/lib/apt/lists/*

# Rewrite SSH git URLs to HTTPS so npm can fetch git dependencies without SSH keys
RUN git config --system --add url."https://github.com/".insteadOf "ssh://git@github.com/" \
    && git config --system --add url."https://github.com/".insteadOf "git@github.com:" \
    && npm install -g openclaw --registry=https://registry.npmmirror.com

COPY config-template.json /etc/openclaw/config-template.json
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

EXPOSE 18789

HEALTHCHECK --interval=10s --timeout=5s --retries=6 \
  CMD curl -f http://localhost:18789/health || exit 1

ENTRYPOINT ["/entrypoint.sh"]
