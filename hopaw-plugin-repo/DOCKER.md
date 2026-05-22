# Hopaw Plugin Repo - Docker 部署说明

## 📁 目录结构

```
hopaw-plugin-repo/
├── Dockerfile                    # Docker 镜像构建文件
├── docker-compose.yml            # Docker Compose 配置
├── .dockerignore                 # Docker 构建忽略文件
├── hopaw-plugin-repo-1.0.0.jar  # 构建好的 JAR 文件 ⭐
├── bin/                          # 起停脚本
│   ├── start.sh
│   ├── stop.sh
│   └── restart.sh
├── logs/                         # 日志目录（自动创建）
└── plugin-packages/              # 插件包目录（自动创建）
```

## 🚀 快速开始

### 1. 准备工作

确保 JAR 文件已放置到项目根目录：

```bash
# 从 target 目录复制
cp target/hopaw-plugin-repo-1.0.0.jar .
```

### 2. 使用 Docker Compose 启动（推荐）

```bash
# 构建并启动
docker-compose up -d

# 查看日志
docker-compose logs -f

# 停止
docker-compose down
```

### 3. 使用 Docker 命令启动

```bash
# 构建镜像
docker build -t hopaw-plugin-repo:1.0.0 .

# 运行容器
docker run -d \
  --name hopaw-plugin-repo \
  -p 8081:8081 \
  -v $(pwd)/logs:/app/logs \
  -v $(pwd)/plugin-packages:/app/plugin-packages \
  hopaw-plugin-repo:1.0.0

# 查看日志
docker logs -f hopaw-plugin-repo
```

## 📊 数据持久化

### 使用命名卷（推荐，避免 Windows 问题）

docker-compose.yml 已配置命名卷 `plugin-db-data`，数据库文件存储在 Docker 管理的卷中：

```yaml
volumes:
  - plugin-db-data:/app/data
```

**查看数据卷：**
```bash
# 查看卷信息
docker volume ls | grep plugin-db-data

# 查看卷详情
docker volume inspect hopaw-plugin-repo_plugin-db-data
```

### 备份数据库

```bash
# 从卷中备份数据库
docker run --rm \
  -v hopaw-plugin-repo_plugin-db-data:/source:ro \
  -v $(pwd):/backup \
  alpine cp /source/plugin-repo.db /backup/plugin-repo-backup.db
```

### 恢复数据库

```bash
# 停止容器
docker-compose down

# 恢复数据库
docker run --rm \
  -v hopaw-plugin-repo_plugin-db-data:/target \
  -v $(pwd):/source \
  alpine cp /source/plugin-repo.db /target/plugin-repo.db

# 启动容器
docker-compose up -d
```

## 🔄 更新应用

```bash
# 1. 停止容器
docker-compose down

# 2. 替换 JAR 文件
cp /path/to/new/hopaw-plugin-repo-1.0.0.jar .

# 3. 重新启动
docker-compose up -d

# 4. 查看日志
docker-compose logs -f
```

## ⚙️ 配置说明

### 环境变量

| 变量名 | 说明 | 默认值 |
|--------|------|--------|
| TZ | 时区 | Asia/Shanghai |
| JAVA_OPTS | JVM 参数 | -Xms256m -Xmx256m ... |
| PLUGIN_REPO_DB_PATH | 数据库路径 | /app/data/plugin-repo.db |

### 端口映射

- **8081**: Web 服务端口

### 数据卷

| 卷名 | 容器路径 | 说明 |
|------|----------|------|
| plugin-db-data | /app/data | 数据库文件（Docker 命名卷） |
| ./logs | /app/logs | 日志文件 |
| ./plugin-packages | /app/plugin-packages | 插件包 |

## 🛠️ 常用命令

```bash
# 查看容器状态
docker-compose ps

# 查看实时日志
docker-compose logs -f

# 进入容器
docker-compose exec hopaw-plugin-repo sh

# 重启容器
docker-compose restart

# 重新构建并启动
docker-compose up -d --build

# 停止并删除所有资源（包括卷）
docker-compose down -v

# 仅停止容器（保留数据卷）
docker-compose down
```

## ⚠️ 注意事项

1. **Windows 用户**：使用命名卷而非直接映射 `.db` 文件，避免 Docker 创建目录而非文件
2. **权限问题**：Linux 环境下确保当前用户有读写权限
3. **端口占用**：确保 8081 端口未被占用
4. **数据库备份**：定期备份 `plugin-db-data` 卷中的数据
5. **日志轮转**：已配置日志最大 10MB，保留 3 个文件

## 🏥 健康检查

容器内置健康检查，每 30 秒检测一次服务状态：

```bash
# 查看健康状态
docker inspect --format='{{.State.Health.Status}}' hopaw-plugin-repo
```

## 📞 访问服务

启动成功后访问：http://localhost:8081
