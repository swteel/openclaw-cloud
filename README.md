# OpenClaw Cloud

基于 [OpenClaw](https://github.com/openclaw/openclaw) 的多租户云平台，每个用户独享一个隔离的 OpenClaw 容器实例，通过统一门户代理访问。

## 架构

```
Browser
  │
  ▼
Portal (port 8081)          ← 登录页、反向代理（HTTP + WebSocket）
  │  cookie: openclaw_token
  │
  ├── /portal/login         ← 登录，设置 JWT Cookie
  ├── /api/register         ← 注册（代理到 Manager）
  ├── /portal/upload/{path} ← 上传文件到用户 workspace
  └── /app/**               ← 反向代理到用户专属容器
        │
        ▼
Manager (port 8080, 内部)   ← 用户管理、容器生命周期、JWT 签发
  │
  └── Docker Daemon         ← 为每个用户创建 openclaw 容器
```

### 服务说明

| 服务 | 端口 | 说明 |
|------|------|------|
| portal | 8081 | 对外唯一入口，登录页 + 反向代理 |
| manager | 8080 | 内部服务，不对外暴露 |
| openclaw-user-{id} | 20000+ | 每用户一个容器，仅 Docker 网络内可达 |

## 快速部署

### 前提条件

- Docker & Docker Compose
- Java 17 + Maven（构建时需要）
- 已构建的 `openclaw-platform:latest` 镜像（见下方）

### 1. 构建 OpenClaw 镜像

```bash
cd openclaw-image
docker build -t openclaw-platform:latest .
```

`openclaw-image/` 目录包含：
- `Dockerfile`：基于 node:22-slim，安装 openclaw 和 Chromium
- `entrypoint.sh`：启动时从模板生成配置，然后运行 `openclaw gateway --bind lan`
- `config-template.json`：OpenClaw 配置模板，支持环境变量替换

### 2. 配置环境变量

```bash
cp .env.example .env
# 编辑 .env，填入以下内容：
```

| 变量 | 说明 | 示例 |
|------|------|------|
| `INTERNAL_TOKEN` | Manager ↔ Portal 内部通信密钥 | 随机字符串 |
| `JWT_SECRET` | JWT 签名密钥 | 随机字符串（64字符以上） |
| `DASHSCOPE_API_KEY` | DashScope API Key（所有容器共享） | `sk-xxx` |
| `MAX_CONTAINERS` | 最大并发容器数 | `100` |
| `PORT_RANGE_START` | 容器端口分配起始 | `20000` |
| `PORT_RANGE_END` | 容器端口分配结束 | `25000` |

### 3. 构建 Java 服务

```bash
mvn package -DskipTests
```

### 4. 构建并启动

```bash
docker-compose up --build -d
```

访问 `http://localhost:8081` 即可看到登录页面。

## 使用说明

### 注册与登录

1. 打开 `http://<host>:8081`
2. 切换到"注册"标签，填写用户名（≥3位）和密码（≥6位），点击"注册并进入"
   - 注册时自动为该用户创建专属 OpenClaw 容器
3. 注册成功后自动登录，跳转到 OpenClaw 控制界面

### 文件上传

将本地文件上传到用户的 workspace（容器内 `/root/.openclaw/` 目录映射到宿主机 `/workspace/user-{id}/`）：

```bash
# 需要先登录拿到 token
TOKEN="your-jwt-token"

curl -X POST http://localhost:8081/portal/upload/path/to/file.txt \
  -H "Cookie: openclaw_token=$TOKEN" \
  -F "file=@/local/path/file.txt"
```

### 容器生命周期

- **自动启动**：注册时自动创建并启动容器
- **自动停止**：用户超过 24 小时未活跃，容器自动停止（每 5 分钟检查一次）
- **自动唤醒**：已停止的容器在用户下次访问时自动唤醒
- **自动清理**：超过 30 天未活跃，容器自动删除（保留数据 volume）

## 项目结构

```
openclaw-platform/
├── pom.xml                        # 父 POM（多模块）
├── docker-compose.yml             # 服务编排
├── .env.example                   # 环境变量示例
├── manager/                       # 管理服务（port 8080，仅内部）
│   ├── pom.xml
│   └── src/main/java/com/openclaw/manager/
│       ├── config/                # 配置（JWT、Docker、平台参数）
│       ├── controller/            # API（AuthController、AdminController、InternalController）
│       ├── service/               # 业务逻辑（AuthService、ContainerLifecycleService）
│       ├── domain/                # 实体和 Repository（User、Container）
│       ├── security/              # JWT Filter、内部 Token Filter
│       └── scheduler/             # 容器 GC 定时任务
├── portal/                        # 门户代理服务（port 8081，对外）
│   ├── pom.xml
│   └── src/main/java/com/openclaw/portal/
│       ├── config/                # WebSocket 配置、Spring Security
│       ├── controller/            # 登录、注册、文件上传
│       ├── filter/                # JWT 认证过滤器
│       ├── handler/               # HTTP 代理、WebSocket 代理
│       └── service/               # ManagerClient（调用 Manager 内部 API）
│   └── src/main/resources/
│       └── static/index.html      # 登录/注册页面
└── openclaw-image/                # OpenClaw 容器镜像
    ├── Dockerfile
    ├── entrypoint.sh
    └── config-template.json       # OpenClaw 配置模板
```

## Manager API

### 公开接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/register` | 注册用户（自动创建容器） |
| POST | `/api/auth/login` | 登录，返回 JWT |

### JWT 认证接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/containers/my` | 查看我的容器状态 |
| POST | `/api/containers/my/start` | 唤醒已停止的容器 |

### 管理员接口（需 ADMIN 角色）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/containers` | 所有容器列表 |
| POST | `/api/admin/containers/{uid}/stop` | 强制停止容器 |
| POST | `/api/admin/containers/{uid}/remove` | 删除容器（保留数据） |
| GET | `/api/admin/stats` | 平台统计 |

## Portal API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/` | 登录/注册页面 |
| POST | `/portal/login` | 登录，设置 Cookie |
| GET | `/portal/logout` | 退出登录 |
| POST | `/api/register` | 注册（代理到 Manager） |
| POST | `/portal/upload/{path}` | 上传文件到用户 workspace |
| ANY | `/app/**` | HTTP 反向代理到用户容器 |
| WS | `/app/**` | WebSocket 代理到用户容器 |

## 设置管理员账号

```bash
# 将某用户设置为管理员（直接修改 DB）
docker exec openclaw-platform_manager_1 \
  sh -c "echo \"UPDATE users SET role='ADMIN' WHERE username='admin';\" | sqlite3 /data/openclaw.db"
```

## 注意事项

- **不要将 `.env` 提交到版本控制**，它包含 API Key 等敏感信息
- Manager 服务不应对外暴露（仅 Docker 网络内访问）
- 用户容器通过 Docker 网络互相隔离，无法直接通信
- 每个容器分配独立的 `openclaw-data-{userId}` volume，数据持久化
