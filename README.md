# OpenClaw Cloud

基于 [OpenClaw](https://github.com/openclaw/openclaw) 的多租户云平台，每个用户独享一个隔离的 OpenClaw 容器实例，通过统一门户代理访问。

---

## 目录

- [架构概览](#架构概览)
- [前提条件](#前提条件)
- [部署步骤（详细）](#部署步骤详细)
- [配置说明](#配置说明)
- [管理员操作](#管理员操作)
- [页面操作指导](#页面操作指导)
- [用户使用说明](#用户使用说明)
- [水平扩展（多主机部署）](#水平扩展多主机部署)
- [API 参考](#api-参考)
- [项目结构](#项目结构)
- [关键踩坑记录](#关键踩坑记录)

---

## 架构概览

```
Browser
  │
  ▼
Portal (port 8081) ─── 对外唯一入口
  │
  ├── GET  /                        → user-ui (React SPA，用户门户)
  ├── GET  /admin/                  → admin-ui (React SPA，管理台)
  ├── ANY  /app/**                  → HTTP 代理到用户第一个 openclaw 容器
  ├── ANY  /app-c/{containerName}/** → HTTP 代理到指定名称的 openclaw 容器
  ├── ANY  /admin-proxy/{uid}/**    → 管理员专用代理，到指定用户第一个容器
  ├── WS   /app/**                  → WebSocket 代理到用户专属 openclaw 容器
  ├── POST /portal/login            → 登录，写 Cookie
  ├── POST /api/register            → 注册（转发到 Manager）
  └── ANY  /api/**                  → 代理到 Manager（携带 JWT）
        │
        ▼
Manager (port 8080，仅内网)
  │   用户管理、JWT 签发、容器生命周期
  │
  └── Docker Daemon
        ├── openclaw-{userId}-{uuid8}  容器1（每用户可有多个）
        └── openclaw-{userId}-{uuid8}  容器2
```

### 服务说明

| 服务 | 端口 | 说明 |
|------|------|------|
| portal | 8081 | 对外唯一入口，只暴露此端口 |
| manager | 8080 | 仅 Docker 内网，不对外暴露 |
| openclaw-user-{id} | 20000+ | 每用户一容器，仅 Docker 网络内可达 |

---

## 前提条件

### 主机配置建议

| 资源 | 最低 | 推荐 |
|------|------|------|
| CPU | 2 核 | 4 核+ |
| 内存 | 4 GB | 8 GB+（每个运行中的 openclaw 容器约占 400-800 MB） |
| 磁盘 | 20 GB | 40 GB+（openclaw 镜像构建后约 2 GB，每个数据 volume 额外占用） |

### 软件依赖

| 软件 | 版本要求 | 用途 |
|------|---------|------|
| Docker | ≥ 24.x | 运行所有服务和用户容器 |
| Docker Compose | v2（推荐）或 v1.29 | 服务编排 |
| Java | 17 | 编译 Spring Boot 服务 |
| Maven | ≥ 3.8 | Java 项目构建 |
| Node.js | ≥ 18 | 编译前端（admin-ui / user-ui） |

### Ubuntu/Debian 一键安装依赖

```bash
# Docker（官方脚本，自动适配版本）
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER
newgrp docker   # 当前会话立即生效，或重新登录

# Java 17 + Maven
sudo apt install -y openjdk-17-jdk maven

# Node.js 18
curl -fsSL https://deb.nodesource.com/setup_18.x | sudo -E bash -
sudo apt install -y nodejs

# python3-bcrypt（初始化管理员密码时需要）
sudo apt install -y python3-pip && pip3 install bcrypt
```

> **Docker Compose v2 说明**：Ubuntu 22.04+ 默认只有 `docker compose`（插件），
> 无 `docker-compose`（v1 命令）。本项目脚本使用 `docker-compose`，若报 `command not found`，
> 请安装 v1：`sudo apt install docker-compose`，或将所有 `docker-compose` 替换为 `docker compose`。

> **防火墙**：云主机（阿里云/腾讯云/AWS 等）需在安全组放开 **TCP 8081** 端口。
> 端口 20000-25000 为 Docker 内网通信，**不要**对外暴露。

---

## 部署步骤（详细）

### 第一步：获取代码

```bash
git clone https://github.com/swteel/openclaw-cloud.git openclaw-platform
cd openclaw-platform
```

> **重要**：目录名必须为 `openclaw-platform`（或修改后续 `.env` 中的 `PLATFORM_NETWORK`）。
> Docker Compose 默认网络名 = `{目录名}_default`，Manager 通过 `PLATFORM_NETWORK` 变量
> 把用户容器加入同一网络。若目录名不一致，所有用户容器将无法被 Portal 代理到。

### 第二步：构建 OpenClaw 用户容器镜像

每个用户容器运行 openclaw，需要先构建这个基础镜像：

```bash
cd openclaw-image
docker build -t openclaw-platform:latest .
cd ..
```

> 这一步需要下载 Chromium（约 300 MB）和 npm 包，国内建议挂代理或配置 apt/npm 镜像（Dockerfile 已配置 npmmirror）。
> 构建耗时约 5-15 分钟（取决于网速），构建完成后用以下命令确认镜像存在：
>
> ```bash
> docker images | grep openclaw-platform
> # 预期：openclaw-platform   latest   ...
> ```
>
> **此步骤必须在第六步之前完成**，否则用户注册时自动创建容器会失败。

### 第三步：配置环境变量

```bash
cp .env.example .env
```

用编辑器打开 `.env`，**必须修改**以下内容：

```env
# Manager <-> Portal 内部通信密钥，随机字符串即可
INTERNAL_TOKEN=换成随机字符串

# JWT 签名密钥，至少 32 个字符，越长越安全
JWT_SECRET=换成随机长字符串至少32位

# DashScope API Key（阿里云百炼平台申请）
DASHSCOPE_API_KEY=sk-your-key-here

# 以下可保持默认
MAX_CONTAINERS=100
PORT_RANGE_START=20000
PORT_RANGE_END=25000
CONTAINER_IMAGE=openclaw-platform:latest
```

> **重要**：`.env` 文件包含密钥，已被 `.gitignore` 排除，不要提交到版本控制。
>
> **重要**：`JWT_SECRET` 一旦设置后不要修改，否则所有已登录用户的 session 会失效，需要重新登录。

### 第四步：编译前端

```bash
# 编译用户门户
cd user-ui && npm install && npm run build && cd ..

# 编译管理台
cd admin-ui && npm install && npm run build && cd ..
```

> 编译产物会自动输出到 `portal/src/main/resources/static/` 目录。
>
> **注意**：`deploy.sh` 不包含前端编译，必须手动先完成此步骤再执行后续构建，
> 否则 Portal 中不会包含 UI 资源。

### 第五步：编译 Java 服务

```bash
mvn package -DskipTests
```

> 编译产物在 `manager/target/manager-*.jar` 和 `portal/target/portal-*.jar`。

### 第六步：构建 Docker 镜像并启动

```bash
# Docker Compose v1
docker-compose build && docker-compose up -d

# Docker Compose v2（Ubuntu 22.04+）
docker compose build && docker compose up -d
```

> **注意**：`deploy.sh` 仅自动执行第五、六步（Java 编译 + Docker 构建/启动），
> 不包含前端编译（第四步）和 openclaw 镜像构建（第二步）。
> 完整流程必须按顺序手动执行每个步骤。

### 第七步：验证部署

```bash
# 检查服务运行状态
docker-compose ps

# 预期：manager 和 portal 均 Up

# 检查 portal 健康
curl http://localhost:8081/actuator/health
# 预期：{"status":"UP"}
```

访问 `http://localhost:8081` 进入用户门户，`http://localhost:8081/admin/` 进入管理台。

---

## 配置说明

### 环境变量（`.env`）

| 变量 | 默认值 | 必填 | 说明 |
|------|--------|------|------|
| `INTERNAL_TOKEN` | `changeme-internal-token` | 是 | Manager <-> Portal 内部通信密钥 |
| `JWT_SECRET` | `changeme-jwt-secret-...` | 是 | JWT 签名密钥，至少 32 字符 |
| `DASHSCOPE_API_KEY` | 空 | 是 | 阿里云 DashScope API Key |
| `MAX_CONTAINERS` | `100` | 否 | 最大并发运行容器数 |
| `PORT_RANGE_START` | `20000` | 否 | 容器端口分配起始值 |
| `PORT_RANGE_END` | `25000` | 否 | 容器端口分配结束值 |
| `CONTAINER_IMAGE` | `openclaw-platform:latest` | 否 | 用户容器镜像名 |

### Manager 配置（`manager/src/main/resources/application.yml`）

```yaml
spring:
  datasource:
    url: jdbc:sqlite:/data/openclaw.db   # 数据库路径（Docker volume 持久化）
  jpa:
    hibernate:
      ddl-auto: update                   # 自动建表，无需手动初始化数据库

platform:
  jwt-expiration-ms: ${JWT_EXPIRATION_MS:86400000}  # JWT 有效期，默认 24 小时
```

### Portal 配置（`portal/src/main/resources/application.yml`）

```yaml
portal:
  manager-url: ${MANAGER_URL:http://localhost:8080}   # Manager 内部地址
  workspace-base: ${WORKSPACE_BASE:/workspace}         # 文件上传根目录

spring:
  servlet:
    multipart:
      max-file-size: 100MB    # 单文件上传上限
```

### 数据库

使用 SQLite，数据库文件在容器内 `/data/openclaw.db`，通过 Docker volume `manager-data` 持久化。
**无需手动建库**，首次启动时 Hibernate 自动创建所有表。

| 表名 | 说明 |
|------|------|
| `users` | 用户信息（id、username、password_hash、role、gateway_token、last_active_at 等） |
| `containers` | 容器记录（container_id、user_id、host_port、status、started_at 等） |
| `port_allocations` | 端口分配记录，防止端口冲突 |
| `platform_config` | 平台动态配置（key-value，如 dashscope_api_key） |

### openclaw 容器配置注入

每次启动用户容器时，Manager 通过 Docker API 将以下配置写入容器 `/etc/openclaw/config-template.json`：

```json
{
  "gateway": {
    "mode": "local",
    "auth": { "mode": "token", "token": "${OPENCLAW_GATEWAY_TOKEN}" },
    "controlUi": { "allowedOrigins": ["*"] },
    "trustedProxies": ["172.0.0.0/8", "10.0.0.0/8", "192.168.0.0/16"]
  },
  "models": {
    "providers": {
      "qwen-portal": {
        "baseUrl": "https://dashscope.aliyuncs.com/compatible-mode/v1",
        "apiKey": "${DASHSCOPE_API_KEY}",
        "models": [
          { "id": "coder-model", "name": "Qwen Coder", ... },
          { "id": "vision-model", "name": "Qwen Vision", ... }
        ]
      }
    }
  }
}
```

`trustedProxies` 使 Portal 的代理 IP 被信任，结合 WebSocket 代理发送的
`X-Forwarded-For: 127.0.0.1`，让 openclaw 自动完成设备配对，无需用户手动操作。

---

## 管理员操作

### 初始化管理员账号

首次部署后，需要手动初始化管理员账号（manager 容器内无 python3/sqlite3，需借助 alpine 临时容器操作）：

```bash
# 0. 确保 python3-bcrypt 已安装（主机上执行）
pip3 install bcrypt 2>/dev/null || sudo apt install -y python3-bcrypt

# 1. 停止 manager（避免 DB 写冲突）
docker stop openclaw-platform-manager-1 2>/dev/null \
  || docker stop openclaw-platform_manager_1 2>/dev/null

# 2. 生成密码哈希（以密码 "yourpassword" 为例）
HASH=$(python3 -c "import bcrypt; print(bcrypt.hashpw(b'yourpassword', bcrypt.gensalt(10)).decode())")

# 3. 写入数据库
docker run --rm -v openclaw-platform_manager-data:/data alpine sh -c "
apk add --no-cache sqlite 2>/dev/null | tail -1
cat > /tmp/init.sql << 'SQLEOF'
INSERT INTO users (username, password_hash, dashscope_key, role, gateway_token, is_deleted, created_at, last_active_at)
VALUES ('admin', '${HASH}', '', 'ADMIN', 'admin-no-container', 0, datetime('now'), datetime('now'));
SQLEOF
sqlite3 /data/openclaw.db < /tmp/init.sql
"

# 4. 重启 manager
docker-compose up -d manager 2>/dev/null || docker compose up -d manager
```

> **注意**：管理员账号无对应 Docker 容器，只能访问管理台 `/admin/`，不能使用工作台。
>
> **容器名说明**：Docker Compose v1 生成的容器名为 `openclaw-platform_manager_1`（下划线），
> v2 生成的为 `openclaw-platform-manager-1`（短横线）。
> 用 `docker ps | grep manager` 查看实际容器名。

### 提升现有用户为管理员

```bash
# 停止 manager（查看实际容器名：docker ps | grep manager）
docker stop openclaw-platform-manager-1 2>/dev/null \
  || docker stop openclaw-platform_manager_1 2>/dev/null

docker run --rm -v openclaw-platform_manager-data:/data alpine sh -c "
apk add --no-cache sqlite 2>/dev/null | tail -1
sqlite3 /data/openclaw.db \"UPDATE users SET role='ADMIN' WHERE username='your_username';\"
"

docker-compose up -d manager 2>/dev/null || docker compose up -d manager
```

### 查询数据库

manager 容器内没有 python3/sqlite3，需用 alpine 临时容器访问：

```bash
# 查看所有用户
docker run --rm -v openclaw-platform_manager-data:/data alpine sh -c "
apk add --no-cache sqlite 2>/dev/null | tail -1
sqlite3 /data/openclaw.db 'SELECT id, username, role, created_at FROM users ORDER BY id'
"

# 查看所有容器
docker run --rm -v openclaw-platform_manager-data:/data alpine sh -c "
apk add --no-cache sqlite 2>/dev/null | tail -1
sqlite3 /data/openclaw.db 'SELECT user_id, container_name, status, host_port FROM containers ORDER BY user_id'
"

# 查看端口分配
docker run --rm -v openclaw-platform_manager-data:/data alpine sh -c "
apk add --no-cache sqlite 2>/dev/null | tail -1
sqlite3 /data/openclaw.db 'SELECT user_id, port FROM port_allocations ORDER BY user_id'
"
```

---

---

## 页面操作指导

### 管理台 — 容器列表（`/admin/containers`）

| 操作 | 说明 |
|------|------|
| **统计卡片** | 顶部显示总数、运行中、已停止数量，实时反映当前状态 |
| **创建容器** | 点击页面顶部"创建容器"按钮，弹框选择目标用户，确认后为其新建一个独立容器实例；同一用户可创建多个容器 |
| **访问 WebUI** | 在运行中的容器行点击，在新标签页打开该容器的 OpenClaw 界面（自动携带 gateway token，无需手动粘贴） |
| **停止** | 停止运行中的容器（保留数据），操作按容器 DB ID 执行，不影响同用户其他容器 |
| **删除** | 强制停止并删除容器记录及端口分配（保留数据 volume），同样按容器 ID 独立操作 |

> **注意**：停止/删除操作通过 `/api/admin/containers/id/{cid}/stop|remove` 按容器 ID 操作，不再通过 userId，避免影响同用户其他容器。

---

### 管理台 — 用户列表（`/admin/users`）

| 操作 | 说明 |
|------|------|
| **拥有容器列** | 显示该用户所有容器名称（逗号分隔），无容器显示"—" |
| **角色下拉** | 直接切换 USER / ADMIN，立即生效 |
| **创建容器**（无容器时显示） | 为该用户创建第一个容器，跳转到容器列表可查看 |
| 容器的停止/删除 | 已移至**容器列表页**按容器粒度操作，用户列表不再提供 |

---

### 管理台 — 访问用户 WebUI 说明

点击容器列表中的"访问 WebUI"后：
1. 浏览器打开 `/admin-proxy/{uid}/`
2. Portal 自动重定向到 `/admin-proxy/{uid}/chat?token={gatewayToken}`（服务端注入 token）
3. 页面加载时清除浏览器 localStorage（防止跨容器历史污染）
4. OpenClaw 从 URL 读取 token 完成认证，显示该用户**真实的**服务端历史

> **注意**：静态资源（`.js`、`.css`）和 XHR/API 调用不会触发重定向，只有浏览器页面导航（`Accept: text/html`）才会。

---

### 用户端 — Dashboard（`/`）

| 操作 | 说明 |
|------|------|
| **实例列表** | 展示当前用户所有容器，每条显示容器名、状态 Tag、启动时间 |
| **启动** | 容器 STOPPED 时显示，点击后唤醒容器（约 2 秒后刷新状态） |
| **进入工作台** | 容器 RUNNING 时显示，携带 `containerName` 跳转到工作台 |
| **刷新** | 手动重新拉取状态 |
| 无容器 | 显示"暂无容器，请联系管理员" |

---

### 用户端 — 工作台（`/workbench`）

- 从 Dashboard 点击"进入工作台"时，会将 `containerName` 通过路由状态传递
- 工作台 iframe 加载 `/app-c/{containerName}/chat?token={gatewayToken}`，每个容器路由独立
- 若未携带 `containerName`（直接访问 `/workbench`），回退到 `/app/chat`（兼容旧行为）

---

### OpenClaw Heartbeat 说明

每个用户容器会在工作区生成 `HEARTBEAT.md`。OpenClaw 内置定时心跳，周期性向 AI 发送：
```
Read HEARTBEAT.md if it exists. If nothing needs attention, reply HEARTBEAT_OK.
```
这会在对话列表产生自动心跳会话，属于 **OpenClaw 自身行为**，不影响正常使用。
如需关闭，在 `HEARTBEAT.md` 中保持只有注释行（即当前默认状态，OpenClaw 应跳过实际 API 调用，但仍可能创建会话记录）。

---

## 用户使用说明

1. 访问 `http://<host>:8081`，注册账号（用户名 ≥3 位，密码 ≥6 位）
2. 注册后自动创建容器并登录，跳转到 Dashboard
3. 容器 RUNNING 后点"进入工作台"
4. 工作台左侧管理文件，右侧嵌入个人 openclaw 实例

### 容器生命周期

| 事件 | 行为 |
|------|------|
| 注册 | 自动创建并启动容器 |
| 访问 | 自动唤醒已停止的容器 |
| 超过 24h 未活跃 | 容器自动停止（每 5 分钟检查） |
| 超过 30 天未活跃 | 容器自动删除（保留数据 volume） |

---

## 水平扩展（多主机部署）

### 扩展策略说明

本平台采用 **粘性路由（Sticky Routing）** 方案实现多主机扩展：
- 每台应用主机独立部署完整的 Portal + Manager + 用户容器，互不共享数据
- Nginx 统一入口，**按用户 Cookie 哈希**将同一用户的所有请求路由到同一台主机
- 新用户首次访问时由 Nginx 自动分配主机，此后固定
- **平台代码零改动**，只需配置 Nginx 和各主机的 `.env`

```
公共域名（your-domain.com）
         │
         ▼
    Nginx 服务器（独立主机或复用其中一台）
         │
         ├─── hash(openclaw_token cookie) ──► Host-A:8081
         │                                    Portal-A + Manager-A
         │                                    用户容器群A
         │
         └─── hash(openclaw_token cookie) ──► Host-B:8081
                                              Portal-B + Manager-B
                                              用户容器群B
```

> **局限性**：某台应用主机宕机时，该主机上的用户无法自动切换到其他主机，
> 需要管理员手动迁移数据（见[故障转移](#故障转移)节）。
> 如需无感知故障转移，需要更复杂的架构改造（共享数据库 + 跨主机容器路由）。

---

### 第一步：部署各应用主机

每台应用主机（Host-A、Host-B……）按照[部署步骤（详细）](#部署步骤详细)完整执行一遍。

**各主机 `.env` 的关键差异**：

```env
# ✅ 必须相同：所有主机使用同一套密钥，保证跨主机登录状态一致
JWT_SECRET=同一个随机字符串至少32位
INTERNAL_TOKEN=同一个随机字符串

# ✅ 必须相同：用户容器镜像名一致
CONTAINER_IMAGE=openclaw-platform:latest

# ⚙️  各主机按需设置，互不影响
MAX_CONTAINERS=100
PORT_RANGE_START=20000
PORT_RANGE_END=25000
DASHSCOPE_API_KEY=sk-your-key   # 同一个 API Key 即可
```

> **重要**：`JWT_SECRET` 必须所有主机相同。
> Portal 登录时签发的 JWT 带有该密钥签名，Nginx 把用户路由到不同主机时，
> 目标主机的 Manager 需要能验证这个 JWT。密钥不同会导致跨主机验证失败（401）。

验证每台主机独立可用：

```bash
# 在每台主机上执行
curl http://localhost:8081/actuator/health
# 预期：{"status":"UP"}
```

---

### 第二步：配置防火墙

**应用主机**（每台 Host-A、Host-B）：
- 开放 TCP **8081**，来源限制为 **Nginx 主机的 IP**（不要对公网开放，避免绕过 Nginx）
- 关闭 8080（Manager 内网端口，对任何外部 IP 均不开放）
- 关闭 20000-25000（用户容器端口，仅 Docker 内网访问）

**Nginx 主机**：
- 开放 TCP **80** 和 **443**（公网）

```bash
# 示例：Ubuntu ufw 规则（在每台应用主机上执行，NGINX_IP 替换为 Nginx 主机实际 IP）
sudo ufw allow from NGINX_IP to any port 8081
sudo ufw deny 8081        # 拒绝其他来源
sudo ufw deny 8080
```

> 云主机还需在控制台的**安全组**中同步配置以上规则。

---

### 第三步：安装并配置 Nginx

在 **Nginx 主机**上执行：

```bash
sudo apt install -y nginx
```

创建配置文件 `/etc/nginx/conf.d/openclaw.conf`：

```nginx
upstream openclaw_backends {
    # 按 openclaw_token cookie 哈希，同一用户始终路由到同一台主机
    hash $cookie_openclaw_token consistent;

    server <Host-A的IP>:8081;
    server <Host-B的IP>:8081;
    # 继续添加更多主机：
    # server <Host-C的IP>:8081;
}

server {
    listen 80;
    server_name your-domain.com;   # 替换为你的域名

    # 客户端超时（WebSocket 长连接需要较长时间）
    proxy_connect_timeout       10s;
    proxy_send_timeout          3600s;
    proxy_read_timeout          3600s;

    location / {
        proxy_pass http://openclaw_backends;

        # 标准代理头
        proxy_set_header Host              $host;
        proxy_set_header X-Real-IP         $remote_addr;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # WebSocket 升级支持（工作台 iframe 使用 WebSocket）
        proxy_http_version 1.1;
        proxy_set_header Upgrade    $http_upgrade;
        proxy_set_header Connection $connection_upgrade;
    }
}

# WebSocket Connection 头处理
map $http_upgrade $connection_upgrade {
    default   upgrade;
    ''        close;
}
```

测试并启动：

```bash
sudo nginx -t          # 检查配置语法
sudo systemctl reload nginx
```

访问 `http://your-domain.com` 验证可以正常打开用户门户。

---

### 第四步：配置 HTTPS（推荐）

使用 Certbot 免费证书：

```bash
sudo apt install -y certbot python3-certbot-nginx
sudo certbot --nginx -d your-domain.com
# 按提示输入邮箱，选择自动重定向 HTTP → HTTPS
sudo systemctl reload nginx
```

Certbot 会自动修改 nginx 配置，加入 SSL 证书和 HTTPS 监听。证书 90 天自动续期：

```bash
sudo certbot renew --dry-run   # 验证自动续期可用
```

---

### 第五步：配置 DNS

在域名注册商（阿里云 DNS、Cloudflare 等）添加 A 记录：

```
your-domain.com.  A  <Nginx 主机的公网 IP>
```

> 如果 Nginx 复用了某台应用主机（Host-A），则 DNS 指向 Host-A 的公网 IP 即可。
> 建议 Nginx 独立部署，避免单点影响整体可用性。

DNS 生效后（通常 1-10 分钟）验证：

```bash
curl https://your-domain.com/actuator/health
# 预期：{"status":"UP"}
```

---

### 第六步：为各主机初始化管理员

每台主机需要单独初始化管理员账号（数据各自独立）：

```bash
# 在每台主机上执行（参考"管理员操作"章节）
HASH=$(python3 -c "import bcrypt; print(bcrypt.hashpw(b'yourpassword', bcrypt.gensalt(10)).decode())")
docker run --rm -v openclaw-platform_manager-data:/data alpine sh -c "
apk add --no-cache sqlite 2>/dev/null | tail -1
sqlite3 /data/openclaw.db \"INSERT INTO users (username, password_hash, dashscope_key, role, gateway_token, is_deleted, created_at, last_active_at) VALUES ('admin', '${HASH}', '', 'ADMIN', 'admin-no-container', 0, datetime('now'), datetime('now'));\"
"
docker compose up -d manager
```

> 管理台（`/admin/`）登录后只能看到**当前主机**的容器和用户，
> 各主机管理台相互独立。如需统一监控，需要自行汇总各主机 API 数据。

---

### 验证多主机路由

```bash
# 用 curl 携带 cookie 模拟同一用户多次请求，确认始终路由到同一台主机
# （在 Nginx 主机上执行）

# 1. 登录获取 cookie
curl -c /tmp/test.cookie -X POST https://your-domain.com/portal/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"testuser","password":"testpass"}'

# 2. 多次带 cookie 请求，观察响应来自同一主机（通过日志或响应特征区分）
for i in $(seq 1 5); do
  curl -b /tmp/test.cookie -s https://your-domain.com/actuator/health
  echo ""
done
# 预期：5 次请求均由同一台主机响应
```

---

### 新增主机

扩容时只需：

1. 在新主机上完整执行[部署步骤](#部署步骤详细)（`.env` 使用相同 `JWT_SECRET` 和 `INTERNAL_TOKEN`）
2. 在 Nginx 配置的 `upstream` 块中增加一行：
   ```nginx
   server <新主机IP>:8081;
   ```
3. 重载 Nginx：`sudo systemctl reload nginx`

新主机加入后，**新注册的用户**会按哈希分配到各主机（包括新主机）。
**已有用户**的 cookie 哈希不变，仍路由到原主机，不受影响。

---

### 故障转移

某台主机宕机时，Nginx 的 `consistent hash` 会将该主机的流量转移到其他主机，
但**用户的数据（账号、容器、聊天历史）留在宕机主机上**，迁移后登录会失败。

**手动数据迁移步骤**（主机恢复后或迁移到新主机）：

```bash
# 1. 备份宕机主机的数据（在宕机主机上，若能访问）
docker run --rm -v openclaw-platform_manager-data:/data \
  -v $(pwd):/backup alpine \
  tar czf /backup/manager-data-backup.tar.gz /data

# 2. 备份用户工作区文件
docker run --rm -v openclaw-platform_workspace-data:/workspace \
  -v $(pwd):/backup alpine \
  tar czf /backup/workspace-backup.tar.gz /workspace

# 3. 将备份文件传到目标主机
scp manager-data-backup.tar.gz workspace-backup.tar.gz user@new-host:~/

# 4. 在目标主机上恢复（先停止服务）
docker compose down
docker run --rm -v openclaw-platform_manager-data:/data \
  -v $(pwd):/backup alpine \
  tar xzf /backup/manager-data-backup.tar.gz -C /
docker run --rm -v openclaw-platform_workspace-data:/workspace \
  -v $(pwd):/backup alpine \
  tar xzf /backup/workspace-backup.tar.gz -C /
docker compose up -d

# 5. 更新 Nginx 配置，将宕机主机替换为目标主机 IP
```

> **用户容器数据**（聊天历史、工作区文件）存储在 Docker named volume（`openclaw-data-{userId}-{uuid8}`）中。
> 这些 volume 在上述步骤中**不会自动备份**，需要额外处理（见下方完整备份方案）。

**完整容器数据备份**（在宕机主机上执行）：

```bash
# 列出所有用户容器的 volume
docker volume ls | grep openclaw-data

# 批量备份所有 volume
for vol in $(docker volume ls --format '{{.Name}}' | grep openclaw-data); do
  docker run --rm -v ${vol}:/data -v $(pwd)/vol-backup:/backup alpine \
    tar czf /backup/${vol}.tar.gz /data
  echo "Backed up: ${vol}"
done
```

---

## API 参考

### Portal 公开接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/` | 用户门户 SPA |
| GET | `/admin/` | 管理台 SPA |
| POST | `/portal/login` | 登录，返回 Cookie |
| GET | `/portal/logout` | 退出登录 |
| POST | `/api/register` | 注册新用户 |

### Portal 认证接口（需 Cookie）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/containers/my` | 查看我的第一个容器状态（含 gatewayToken），无容器返回 `null` |
| GET | `/api/containers/my/all` | 查看我的所有容器列表（含 gatewayToken） |
| POST | `/api/containers/my/start` | 启动已停止的第一个容器 |
| GET | `/portal/files` | 列出工作区文件 |
| POST | `/portal/upload/{*path}` | 上传文件 |
| DELETE | `/portal/files/{*path}` | 删除文件 |
| ANY | `/app/**` | HTTP/WS 代理到用户第一个容器 |
| ANY | `/app-c/{containerName}/**` | HTTP 代理到指定容器名的容器 |

### 管理员接口（需 ADMIN 角色）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/containers` | 所有容器列表（含 `id`、`username` 字段） |
| POST | `/api/admin/containers/{uid}/create` | 为用户创建新容器（同一用户可多次创建） |
| POST | `/api/admin/containers/{uid}/stop` | 按 userId 停止第一个容器 |
| POST | `/api/admin/containers/{uid}/remove` | 按 userId 删除第一个容器 |
| POST | `/api/admin/containers/id/{cid}/stop` | 按容器 DB ID 停止（多容器场景使用） |
| POST | `/api/admin/containers/id/{cid}/start` | 按容器 DB ID 启动 |
| POST | `/api/admin/containers/id/{cid}/remove` | 按容器 DB ID 删除 |
| GET | `/api/admin/users` | 用户列表（`containerNames` 为容器名数组，替代旧的 `containerStatus`） |
| GET | `/api/admin/stats` | 容器统计（total/running/stopped） |
| GET | `/api/admin/config` | 读取平台配置 |
| PUT | `/api/admin/config` | 更新平台配置 |

### 内部接口（需 X-Internal-Token，Portal → Manager 调用）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/internal/auth/verify` | 验证 JWT |
| GET | `/internal/containers/{uid}/address` | 按 userId 获取容器地址和 gatewayToken |
| GET | `/internal/containers/by-name/{name}/address` | 按容器名获取地址和 gatewayToken |
| POST | `/internal/users/{uid}/heartbeat` | 记录活跃时间并唤醒容器 |

---

## 项目结构

```
openclaw-platform/
├── .env.example                        # 环境变量模板（复制为 .env 填写）
├── docker-compose.yml                  # 服务编排
├── deploy.sh                           # 一键构建+部署脚本
├── pom.xml                             # Maven 父 POM
│
├── openclaw-image/                     # 用户容器镜像
│   ├── Dockerfile                      # node:22-slim + chromium + openclaw
│   ├── entrypoint.sh                   # envsubst 渲染配置 → 启动 openclaw
│   └── config-template.json            # openclaw 配置模板（支持 ${ENV_VAR} 替换）
│
├── manager/                            # 管理服务（内部，port 8080）
│   ├── Dockerfile
│   └── src/main/java/com/openclaw/manager/
│       ├── config/
│       │   ├── DockerConfig.java
│       │   └── PlatformProperties.java
│       ├── controller/
│       │   ├── AuthController.java     # /api/auth/register, /api/auth/login
│       │   ├── ContainerController.java
│       │   ├── AdminController.java    # /api/admin/** (ADMIN only)
│       │   └── InternalController.java # /internal/** (内部 Token 鉴权)
│       ├── domain/entity/
│       │   ├── User.java
│       │   ├── Container.java
│       │   ├── PortAllocation.java
│       │   └── PlatformConfig.java
│       ├── service/
│       │   ├── AuthService.java
│       │   ├── ContainerLifecycleService.java
│       │   ├── PortAllocatorService.java
│       │   └── PlatformConfigService.java
│       ├── security/
│       │   ├── JwtTokenProvider.java
│       │   ├── JwtAuthenticationFilter.java
│       │   └── InternalTokenFilter.java
│       └── scheduler/
│           └── ContainerGCScheduler.java  # 定时停止/清理不活跃容器
│
├── portal/                             # 门户代理服务（对外，port 8081）
│   ├── Dockerfile
│   └── src/main/java/com/openclaw/portal/
│       ├── config/
│       │   ├── SecurityConfig.java
│       │   ├── WebSocketConfig.java
│       │   └── WebMvcConfig.java       # SPA 静态资源路由 + fallback
│       ├── controller/
│       │   ├── AuthController.java     # /portal/login, /portal/logout, /api/register
│       │   ├── ApiProxyController.java # /api/admin/**, /api/containers/** → Manager
│       │   ├── FileController.java     # /portal/files
│       │   ├── AppProxyController.java # /app/** → HTTP 代理
│       │   └── SpaController.java      # /admin, /admin/ → admin/index.html
│       ├── filter/
│       │   └── AuthFilter.java         # Cookie JWT 校验
│       ├── handler/
│       │   ├── ProxyHandler.java       # HTTP 反向代理
│       │   └── WsProxyHandler.java     # WebSocket 代理（OkHttp）
│       └── service/
│           └── ManagerClient.java
│   └── src/main/resources/static/
│       ├── index.html                  # user-ui 编译产物
│       ├── assets/
│       └── admin/                      # admin-ui 编译产物
│
├── user-ui/                            # 用户门户 React SPA
│   ├── vite.config.js                  # 输出到 portal/src/main/resources/static/
│   └── src/pages/
│       ├── Login.jsx
│       ├── Dashboard.jsx
│       └── Workbench/
│           ├── FilePanel.jsx
│           └── OpenClawFrame.jsx       # 获取 gatewayToken → 设置 iframe src
│
└── admin-ui/                           # 管理台 React SPA
    ├── vite.config.js                  # 输出到 portal/src/main/resources/static/admin/
    └── src/pages/
        ├── Login.jsx
        ├── ContainerList.jsx
        ├── UserList.jsx
        └── PlatformConfig.jsx
```

---

## 关键踩坑记录

### 坑1：WebSocket 代理必须用 OkHttp，不能用标准 Java WebSocket 客户端

**现象**：openclaw 报 `origin not allowed` 或 `pairing required`，iframe 无法正常连接。

**根本原因**：
- Java 标准 WebSocket 客户端（Tyrus）不发送 `Origin` 头
- Tyrus 无法覆盖 `Host` 头（JVM 将其列为禁止修改的受限头）
- openclaw 的 `isLocalClient` 判断依赖 `Host: localhost:18789`，Tyrus 无法满足

**正确做法**：使用 OkHttp 作为后端 WebSocket 客户端，手动设置三个关键头：

```java
new Request.Builder()
    .url(wsUrl)
    .header("Host",   "localhost:18789")          // 让 openclaw 认为是本地连接
    .header("Origin", "http://localhost:18789")   // 通过 origin 校验
    .header("X-Forwarded-For", "127.0.0.1")       // 配合 trustedProxies 解析为回环地址
    .header("Authorization", "Bearer " + gatewayToken)
    .build()
```

同时 openclaw 配置中需要：
```json
{
  "gateway": {
    "controlUi": { "allowedOrigins": ["*"] },
    "trustedProxies": ["172.0.0.0/8", "10.0.0.0/8", "192.168.0.0/16"]
  }
}
```

**不要做**：修改 openclaw 的 JS 源码打补丁，每次版本更新都需要重新打，不可维护。

---

### 坑2：docker-compose 服务名即 DNS 名，不要用 `docker run` 手动启动

**现象**：Portal 启动后报 `Failed to resolve 'manager'`，注册/登录失败。

**根本原因**：手动 `docker run` 的容器只有容器名，没有 `manager` DNS 别名。
Portal 配置的 `MANAGER_URL=http://manager:8080` 无法解析。

**正确做法**：始终用 `docker-compose up -d`，compose 自动为每个 service 注册 DNS 别名。

如果必须用 `docker run`，加 `--network-alias manager`：
```bash
docker run ... --network openclaw-platform_default --network-alias manager ...
```

---

### 坑3：`docker cp` 替换数据库文件会导致 SQLite 崩溃

**现象**：`docker cp` 将修改后的 `.db` 文件复制回容器后，Manager 报
`SQLITE_READONLY_DBMOVED`，所有写操作失败。

**根本原因**：`docker cp` 写入的是新文件（inode 变化），SQLite 检测到文件被替换后拒绝写入。

**正确做法**：直接在容器内执行 SQL：
```bash
docker exec openclaw-platform_manager_1 python3 -c "
import sqlite3
conn = sqlite3.connect('/data/openclaw.db')
conn.execute(\"UPDATE users SET role='ADMIN' WHERE username='admin'\")
conn.commit()
conn.close()
"
```

**恢复方法**：若已出现此错误，重启 manager 即可：
```bash
docker restart openclaw-platform_manager_1
```

---

### 坑4：JWT_SECRET 变更导致所有用户 session 失效

**现象**：Manager 重启后所有已登录用户请求返回 `Invalid token`。

**原因**：`JWT_SECRET` 改变后，历史 JWT 无法通过校验。

**预防**：首次部署前在 `.env` 设好 `JWT_SECRET`，之后不要修改。

---

### 坑5：`/admin/` 路由 404，`/admin/index.html` 正常

**现象**：Spring ResourceHandler 配置了 `/admin/**` 的 SPA fallback，
但访问 `/admin/` 仍 404。

**根本原因**：`/admin/` 的 resourcePath 是空字符串，`PathResourceResolver` 找到目录
本身（`exists()` 为 true 但不可读），不走 fallback，最终 404。

**解决方案**：在 `SpaController` 显式处理：
```java
@GetMapping(value = {"/admin", "/admin/"}, produces = MediaType.TEXT_HTML_VALUE)
public ResponseEntity<Resource> adminRoot() {
    return ResponseEntity.ok().contentType(MediaType.TEXT_HTML)
        .body(new ClassPathResource("static/admin/index.html"));
}
```

---

### 坑6：镜像名须与 docker-compose.yml 的 `image:` 字段一致

**现象**：`docker build -t openclaw-platform-portal:latest` 后，
`docker-compose up -d` 仍用旧镜像（compose 里写的是 `openclaw-portal:latest`）。

**正确做法**：统一用 `docker-compose build` 构建，镜像名自动与 compose 一致：
```bash
docker-compose build && docker-compose up -d
```

---

### 坑7：SQLite 并发写入导致注册 403（SQLITE_BUSY）

**现象**：用户注册返回 `403 Forbidden from POST http://manager:8080/api/auth/register`。
Manager 日志报 `SQLITE_BUSY: The database file is locked`。

**根本原因**：Portal 对每个认证请求都发送心跳（更新 `last_active_at`），多个并发心跳
与注册事务同时写 SQLite，SQLite 默认零等待直接失败，Spring 返回 500，
WebClient 的异常消息里包含"403"导致误导。

**解决方案**：在 `application.yml` 启用 WAL 模式并设置 busy_timeout：
```yaml
spring:
  datasource:
    hikari:
      connection-init-sql: "PRAGMA journal_mode=WAL; PRAGMA busy_timeout=30000;"
      maximum-pool-size: 1
```
- `journal_mode=WAL`：允许读写并发，减少锁竞争
- `busy_timeout=30000`：等待最多 30 秒而不是立即失败
- `maximum-pool-size=1`：SQLite 单写者模型，序列化所有 DB 访问

---

### 坑8：DB 与 Docker 状态不一致导致端口冲突

**现象**：注册时报 `Bind for 0.0.0.0:XXXXX failed: port is already allocated`，
但 `port_allocations` 表里该端口没有记录。

**根本原因**：多次手动干预（直接删容器、`docker cp` 改 DB、重建容器）后，
DB 中记录的 `host_port` 与实际容器绑定的端口不一致，`port_allocations` 表中
被删容器的端口记录残留或已清除，但 Docker 仍在占用该端口。

**诊断方法**：对比 DB 中 `containers.host_port` 与 Docker 实际端口：
```bash
# 查看Docker实际端口
docker ps --filter "name=openclaw-user" --format "{{.Names}} {{.Ports}}"

# 查看DB记录的端口
docker run --rm -v openclaw-platform_manager-data:/data alpine sh -c "
apk add --no-cache sqlite 2>/dev/null | tail -1
sqlite3 /data/openclaw.db 'SELECT user_id, container_name, host_port, status FROM containers ORDER BY user_id'
"
```

**修复方法**：停止 manager，用 alpine 容器执行 SQL 对齐 DB 与 Docker 真实状态，
重建 `port_allocations` 表后重启。

**预防**：不要手动操作 Docker 容器（删除、重建）；如必须操作，重启 manager 后
让自动同步调度器（每 2 分钟）自动修正 DB 状态。

---

### 坑9：Docker build 缓存导致旧 JAR 被打入新镜像

**现象**：修改 Java 代码、重新 `mvn package`、`docker build`，但运行时行为没有变化。
日志显示的 JAR 修改时间仍是旧的，新功能不生效。

**根本原因**：`docker build` 使用了缓存层，`COPY app.jar /app/app.jar` 那一层没有失效，
实际打入的还是之前构建的旧 JAR。即便文件内容变了，Docker 也可能因 layer hash 未变而复用缓存。

**正确做法**：跳过镜像重建，直接把 JAR 拷进运行中的容器并重启：
```bash
mvn package -DskipTests -pl portal
docker cp portal/target/portal-1.0.0.jar openclaw-platform_portal_1:/app/app.jar
docker restart openclaw-platform_portal_1
```
比重建镜像快 10 倍以上，适合开发期快速迭代。如需完全重建镜像（发布生产），加 `--no-cache`：
```bash
docker build --no-cache -t openclaw-portal:latest portal/
```

---

### 坑10：`@PathVariable` 不加显式名称导致 500

**现象**：访问 `/admin-proxy/2/` 报 500，日志：
`IllegalArgumentException: Name for argument of type [java.lang.Long] not specified`

**根本原因**：Spring MVC 默认通过字节码反射获取参数名，
但 Maven 未配置 `-parameters` 编译选项时，泛型参数名被擦除，
`@PathVariable Long uid` 无法推断变量名为 `uid`。

**解决方案**：显式指定名称：
```java
// 错误
@PathVariable Long uid

// 正确
@PathVariable("uid") Long uid
```

---

### 坑11：AuthFilter `chain.doFilter` 放在 try-catch 内导致下游异常被误判为 401

**现象**：管理员 token 合法，但访问 `/app/**` 或 `/admin-proxy/**` 总是返回 401。

**根本原因**：
```java
try {
    verifyToken();    // 鉴权
    chain.doFilter(); // 继续处理请求
} catch (Exception e) {
    response.sendError(401); // 原意：鉴权失败
}
```
`chain.doFilter()` 在 try 块内，下游（ProxyHandler）抛出的任何异常（如用户无容器、容器不可达）
都会被这个 catch 捕获，错误地返回 401 而不是实际的 502/503。

**解决方案**：把 `chain.doFilter()` 移到 try-catch 外：
```java
try {
    userInfo = verifyToken(); // 只捕获鉴权异常
} catch (Exception e) {
    response.sendError(401, "Invalid token");
    return;
}
// 鉴权成功后，下游异常不再被误捕获
chain.doFilter(req, res);
```

---

### 坑12：openclaw-app 渲染到 Light DOM，不是 Shadow DOM

**现象**：向 `openclaw-app` 的 Shadow Root 注入 CSS / 操作 `.shell` 元素，
完全无效，`appEl.shadowRoot` 始终为 `null`。

**根本原因**：Lit 文档默认行为是使用 Shadow DOM，但 openclaw 重写了 `createRenderRoot()`，
令其返回 `this`（元素本身）而非 shadow root。
所有 `.shell`、`.topbar`、`.nav` 元素都是 `openclaw-app` 的普通子元素，在 Light DOM 中。

**正确做法**：直接用 `document.querySelector('.shell')` 查找，CSS 注入到 `document.head`：
```javascript
// 错误：shadow root 不存在
const shell = appEl.shadowRoot.querySelector('.shell');
appEl.shadowRoot.appendChild(style);

// 正确：直接查 document
const shell = document.querySelector('.shell');
document.head.appendChild(style);
```

**验证方法**：
```javascript
document.querySelector('openclaw-app').shadowRoot; // null → Light DOM
document.querySelector('openclaw-app').childElementCount; // > 0 → 子元素在 Light DOM
```

---

### 坑13：openclaw 的 CSP 策略阻止注入的内联脚本执行

**现象**：在 HTML 响应里注入 `<script>` 隐藏 topbar/nav，脚本不执行，控制台报错：
```
Executing inline script violates Content Security Policy directive 'script-src 'self''
```

**根本原因**：openclaw 的 HTTP 响应头包含严格 CSP，禁止内联脚本（`unsafe-inline`）。
浏览器直接阻止脚本执行，没有任何运行时错误提示。

**解决方案**：在 Portal 代理层剥离 openclaw 的 `Content-Security-Policy` 响应头：
```java
// ProxyHandler.java：复制响应头时直接跳过 CSP
if ("content-security-policy".equals(nameLower)) {
    return; // 不转发此头，允许我们的注入脚本运行
}
```
由于 openclaw 已通过 Portal 代理鉴权，这里去掉 CSP 不会引入实际安全风险。

---

### 坑14：Lit 每次重新渲染会覆盖手动修改的 class 属性

**现象**：用 JavaScript 给 `.shell` 元素加上 `shell--onboarding` class，
几百毫秒后 class 消失，topbar/nav 重新出现。

**根本原因**：Lit 的模板绑定（`class="shell ${r} ${d} ..."`）在每次响应式状态变化时
都会调用 `element.setAttribute('class', 新值)`，完整替换 class 属性，
手动添加的类被清除。

**解决方案**：拦截 `.shell` 元素的 `setAttribute` 方法，强制在每次调用时注入我们的类：
```javascript
const orig = shell.setAttribute.bind(shell);
shell.setAttribute = function(name, value) {
    if (name === 'class') {
        const set = new Set(value.trim().split(/\s+/).filter(Boolean));
        ['shell--onboarding', 'shell--chat-focus'].forEach(c => set.add(c));
        value = Array.from(set).join(' ');
    }
    orig(name, value);
};
```
这样 Lit 无论渲染多少次，我们的 class 始终存在。

---

### 坑15：去掉 unique 约束后忘记同步端口释放逻辑

**现象**：支持多容器后，删除某个容器（`removeContainerKeepVolume`）后该端口仍被占用，
下次创建容器时端口分配失败或重复分配。

**根本原因**：原来端口表按 `userId` 关联，`releaseByUserId(userId)` 释放该用户所有端口。
多容器后每个容器独立占用端口，必须按端口号释放，否则一个用户有 N 个容器时，
删除其中一个会释放全部端口，或完全无法释放。

**解决方案**：`PortAllocationRepository` 新增 `deleteByPort(int port)`，
`PortAllocatorService` 新增 `releasePort(int port)`，
所有删除容器的路径均改为 `portAllocator.releasePort(container.getHostPort())`：
```java
// ContainerLifecycleService.java
portAllocator.releasePort(container.getHostPort());
// ContainerGCScheduler.java（GC 回收也要同步更新）
portAllocator.releasePort(c.getHostPort());
```

---

### 坑16：同源 localStorage 导致多容器 admin WebUI 共享会话数据

**现象**：管理台通过 `/admin-proxy/{uid}/**` 访问不同用户的 OpenClaw 容器，
但 iframe 里的聊天历史、登录状态等是共用的：访问用户 A 的容器再切换到用户 B，
看到的仍是用户 A 的数据。

**根本原因**：Portal 把所有容器的内容都代理到同一个 origin（如 `localhost:8081`），
浏览器的 `localStorage` / `sessionStorage` 是按 origin 隔离的，
所有 `/admin-proxy/**` 路径共用同一份 storage。

**解决方案**：在 ProxyHandler 里给 admin-proxy 路径的 HTML 响应注入清理脚本，
确保每次进入新容器前清空 storage：
```java
// ProxyHandler.java
if (pathPrefix.startsWith("/admin-proxy/")) {
    html = injectClearStorageScript(html);
}

private static String injectClearStorageScript(String html) {
    String script = """
        <script data-portal-clear="1">
        (function(){
          try { localStorage.clear(); sessionStorage.clear(); } catch(_) {}
        })();
        </script>""";
    if (html.contains("<head>")) return html.replace("<head>", "<head>" + script);
    return script + html;
}
```

---

### 坑17：localStorage.clear() 清除了 OpenClaw 鉴权 token 导致"unauthorized"

**现象**：注入 `localStorage.clear()` 解决多容器共享历史问题后，
进入 admin WebUI 弹出"unauthorized: gateway token missing"错误，页面无法加载。

**根本原因**：OpenClaw SPA 在首次加载时把 gateway token 存入 localStorage，
后续 API 请求都从 localStorage 里取 token。
我们注入的 `localStorage.clear()` 在 SPA 初始化之前执行，
清空了 token，SPA 找不到 token 就报 unauthorized。

**解决方案**：使用 URL 参数重新注入 token，让 OpenClaw SPA 在 URL 中读取 token
（`/chat?token={gatewayToken}`）。在 `AdminProxyController` 中，
对首次 HTML 导航请求（无 `?token=` 参数）重定向到带 token 的 URL：
```java
String gatewayToken = containerInfo[1];
String encodedToken = URLEncoder.encode(gatewayToken, StandardCharsets.UTF_8);
response.sendRedirect("/admin-proxy/" + uid + "/chat?token=" + encodedToken);
```

---

### 坑18：重定向逻辑误将静态资源请求也重定向导致空白页

**现象**：加入"首次访问重定向到 `/chat?token=`"逻辑后，页面变成空白，
控制台报 404 或资源加载失败。

**根本原因**：重定向条件只判断"没有 `?token=`"，但 JS/CSS 静态资源（`/assets/main.js`）
和 XHR/API 请求（`/api/auth/status`）也没有 `?token=` 参数，
被错误地重定向到 `/chat?token=`，导致浏览器用 HTML 响应当作 JS/JSON 解析，
或静态资源路径错误，整个 SPA 崩溃。

**解决方案**：利用 HTTP `Accept` 请求头区分浏览器导航（`text/html`）与其他请求：
浏览器 HTML 导航请求的 `Accept` 头始终包含 `text/html`，
而静态资源（`image/*`, `*/*`）和 XHR/fetch（`application/json` 或 `*/*`）均不含 `text/html`。
仅对 HTML 导航请求执行重定向：
```java
String accept = request.getHeader("Accept");
boolean isHtmlNavigation = accept != null && accept.contains("text/html");
if (isHtmlNavigation && (query == null || !query.contains("token="))) {
    // 只有浏览器页面导航才重定向，静态资源和 XHR 直接代理
    response.sendRedirect("/admin-proxy/" + uid + "/chat?token=" + encodedToken);
    return;
}
```

---

### 坑19：OpenClaw 内置心跳机制在聊天列表中创建"Read HEARTBEAT.md"会话

**现象**：每次创建新对话，聊天历史列表里都会出现"Read HEARTBEAT.md if it exists..."
这样一条系统会话，用户困惑这是 bug 还是正常行为。

**根本原因**：OpenClaw 内置心跳（Heartbeat）功能，在 Agent 空闲时会定期读取工作区的
`HEARTBEAT.md` 文件（若存在）以维持会话活跃，该操作会产生可见的 tool-use 记录。
Docker 镜像里默认包含 `HEARTBEAT.md`，因此每个容器都会触发此行为。

**这是 OpenClaw 的内置特性，不是平台 bug**。如需禁用，可在构建镜像时删除
`HEARTBEAT.md` 或修改 OpenClaw 的心跳相关配置。

---

### 坑 20：工作台 iframe 显示 disconnected (1006): no reason

**现象**：用户登录后进入工作台（`/workbench`），iframe 中的 OpenClaw chat 页面显示
`disconnected (1006): no reason`，无法建立 WebSocket 连接。

**根本原因**：
- 前端通过 `/app-c/{containerName}/chat?token={gatewayToken}` 加载 iframe
- URL 参数 `?token=xxx` 是 **gateway token**，用于 OpenClaw 容器内部认证
- 但 Portal 的 WebSocket 握手和 HTTP 认证需要 **JWT token**（存储在 Cookie 中）
- 之前代码混淆了两种 token：
  - `AuthFilter` 尝试从 URL 参数读取 token 并验证为 JWT → 失败
  - `ContainerProxyHandshakeInterceptor` 也从 URL 读取 gateway token 当 JWT → 失败
  - WebSocketConfig 未监听 `/app-c/{containerName}/**` 路径的 WebSocket 升级请求

**解决方案**：

1. **WebSocketConfig.java** - 添加 `/app-c/{containerName}/**` WebSocket 支持：
```java
// 新增 ContainerProxyHandshakeInterceptor
static class ContainerProxyHandshakeInterceptor implements HandshakeInterceptor {
    @Override
    public boolean beforeHandshake(...) {
        // 提取 containerName 从路径：/app-c/{containerName}/...
        String containerName = extractContainerName(path);
        attributes.put("containerName", containerName);
        
        // 从 Cookie 读取 JWT token（NOT 从 URL 参数）
        String token = extractTokenFromCookie(request);
        if (token == null) return false;
        
        // 验证 JWT 获取 userId
        Map<String, Object> userInfo = managerClient.verifyToken(token);
        attributes.put("userId", userInfo.get("userId"));
        return true;
    }
}
```

2. **WsProxyHandler.java** - 支持从 containerName 获取容器信息：
```java
String containerName = (String) attributes.get("containerName");
if (containerName != null) {
    containerInfo = managerClient.getContainerInfoByName(containerName);
} else {
    containerInfo = managerClient.getContainerInfo(userId);
}
```

3. **AuthFilter.java** - 移除从 URL 参数读取 token 的逻辑：
```java
// 只从 Cookie 或 Authorization header 读取 JWT token
// URL 参数 ?token=xxx 是 gateway token，留给 OpenClaw 容器使用
```

**正确流程**：
```
用户登录 → Cookie: openclaw_token={JWT}
  ↓
访问 /workbench → iframe src=/app-c/{containerName}/chat?token={gatewayToken}
  ↓
WebSocket 握手 → Cookie: openclaw_token={JWT} (URL 参数 token 被忽略)
  ↓
ContainerProxyHandshakeInterceptor:
  - 从 Cookie 验证 JWT → userId
  - 提取 containerName
  ↓
WsProxyHandler:
  - 通过 containerName 调用 Manager API 获取 gatewayToken
  - 转发 WebSocket 到 OpenClaw 容器 (Authorization: Bearer {gatewayToken})
```

**关键区别**：
| Token 类型 | 用途 | 传递方式 | 验证方式 |
|-----------|------|---------|---------|
| JWT token | Portal 用户认证 | Cookie: openclaw_token | Manager /internal/auth/verify |
| Gateway token | OpenClaw 容器认证 | URL 参数 ?token= | OpenClaw 网关内部验证 |

**不要做**：
- ❌ 从 URL 参数读取 gateway token 并尝试验证为 JWT
- ❌ 在 WebSocket 握手时忽略 Cookie 中的 JWT token
- ❌ 忘记在 WebSocketConfig 中注册 `/app-c/{containerName}/**` 路径
