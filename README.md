# OpenClaw Cloud

基于 [OpenClaw](https://github.com/openclaw/openclaw) 的多租户云平台，每个用户独享一个隔离的 OpenClaw 容器实例，通过统一门户代理访问。

---

## 目录

- [架构概览](#架构概览)
- [前提条件](#前提条件)
- [部署步骤（详细）](#部署步骤详细)
- [配置说明](#配置说明)
- [管理员操作](#管理员操作)
- [用户使用说明](#用户使用说明)
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
  ├── GET  /              → user-ui (React SPA，用户门户)
  ├── GET  /admin/        → admin-ui (React SPA，管理台)
  ├── ANY  /app/**        → HTTP 反向代理到用户专属 openclaw 容器
  ├── WS   /app/**        → WebSocket 代理到用户专属 openclaw 容器
  ├── POST /portal/login  → 登录，写 Cookie
  ├── POST /api/register  → 注册（转发到 Manager）
  └── ANY  /api/**        → 代理到 Manager（携带 JWT）
        │
        ▼
Manager (port 8080，仅内网)
  │   用户管理、JWT 签发、容器生命周期
  │
  └── Docker Daemon
        └── openclaw-user-{id}  (port 20000+，每用户一个容器)
```

### 服务说明

| 服务 | 端口 | 说明 |
|------|------|------|
| portal | 8081 | 对外唯一入口，只暴露此端口 |
| manager | 8080 | 仅 Docker 内网，不对外暴露 |
| openclaw-user-{id} | 20000+ | 每用户一容器，仅 Docker 网络内可达 |

---

## 前提条件

| 软件 | 版本要求 | 用途 |
|------|---------|------|
| Docker | ≥ 24.x | 运行所有服务和用户容器 |
| Docker Compose | ≥ 1.29 或 v2 | 服务编排 |
| Java | 17 | 编译 Spring Boot 服务 |
| Maven | ≥ 3.8 | Java 项目构建 |
| Node.js | ≥ 18 | 编译前端（admin-ui / user-ui） |

> **注意**：如果当前用户不在 `docker` 组，所有 `docker` / `docker-compose` 命令前需加 `sudo`，
> 或执行 `sudo usermod -aG docker $USER` 并重新登录。

---

## 部署步骤（详细）

### 第一步：获取代码

```bash
git clone <仓库地址>
cd openclaw-platform
```

### 第二步：构建 OpenClaw 用户容器镜像

每个用户容器运行 openclaw，需要先构建这个基础镜像：

```bash
cd openclaw-image
docker build -t openclaw-platform:latest .
cd ..
```

> 这一步需要下载 Chromium 和 npm 包，国内建议挂代理或配置 npm 镜像（Dockerfile 已配置 npmmirror）。
> 构建完成后用 `docker images | grep openclaw-platform` 确认镜像存在。

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

### 第五步：编译 Java 服务

```bash
mvn package -DskipTests
```

> 编译产物在 `manager/target/manager-*.jar` 和 `portal/target/portal-*.jar`。

### 第六步：构建 Docker 镜像并启动

```bash
docker-compose build
docker-compose up -d
```

或者使用一键脚本（自动完成第五、六步）：

```bash
./deploy.sh
```

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

### 设置管理员账号

系统没有自动管理员注册入口，需要手动提升：

```bash
docker exec openclaw-platform_manager_1 python3 -c "
import sqlite3
conn = sqlite3.connect('/data/openclaw.db')
conn.execute(\"UPDATE users SET role='ADMIN' WHERE username='your_username'\")
conn.commit()
print('Done:', conn.execute(\"SELECT username, role FROM users WHERE username='your_username'\").fetchone())
conn.close()
"
```

执行后重新登录，即可访问 `http://<host>:8081/admin/`。

### 查询数据库

```bash
# 查看所有用户
docker exec openclaw-platform_manager_1 python3 -c "
import sqlite3
conn = sqlite3.connect('/data/openclaw.db')
for row in conn.execute('SELECT id, username, role, created_at FROM users'):
    print(row)
conn.close()
"

# 查看所有容器
docker exec openclaw-platform_manager_1 python3 -c "
import sqlite3
conn = sqlite3.connect('/data/openclaw.db')
for row in conn.execute('SELECT user_id, container_name, status, started_at FROM containers'):
    print(row)
conn.close()
"
```

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
| GET | `/api/containers/my` | 查看我的容器状态（含 gatewayToken） |
| POST | `/api/containers/my/start` | 启动已停止的容器 |
| GET | `/portal/files` | 列出工作区文件 |
| POST | `/portal/upload/{*path}` | 上传文件 |
| DELETE | `/portal/files/{*path}` | 删除文件 |
| ANY | `/app/**` | HTTP/WS 代理到容器 |

### 管理员接口（需 ADMIN 角色）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/containers` | 所有容器列表 |
| POST | `/api/admin/containers/{uid}/stop` | 停止容器 |
| POST | `/api/admin/containers/{uid}/remove` | 删除容器（保留数据） |
| GET | `/api/admin/users` | 用户列表 |
| GET | `/api/admin/config` | 读取平台配置 |
| PUT | `/api/admin/config` | 更新平台配置 |

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
