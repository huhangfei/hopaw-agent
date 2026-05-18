# Hopaw Agent

基于 Java 和 LangChain4j 的智能 Agent 开发框架，支持多 Agent 管理、工具调用和流式响应。

## 技术栈

- **Java 17** | **Spring Boot 2.7.18** | **LangChain4j 1.14.0**
- SQLite | MyBatis | WebSocket | Thymeleaf
- Playwright | JSch | Jsoup

## 核心功能

- 🤖 **Agent 管理** - 创建、编辑、删除 Agent，分配工具集
- 🛠️ **工具系统** - 命令执行、SSH 远程、网页抓取、网络搜索、记忆管理、日期时间
- 💬 **实时交互** - WebSocket 通信、流式响应、Thinking 过程可视化
- 🧠 **记忆系统** - 短期对话记忆 + 长期记忆存储

## 项目结构

```
hopaw-agent/
├── hopaw-contract/      # 契约层：接口、DTO、枚举定义
├── hopaw-infra/         # 基础设施层：数据访问、工具服务、AI 模型配置
├── hopaw-biz/           # 业务逻辑层：Agent 服务、记忆管理
├── hopaw-app/           # 应用层：Web 接口、WebSocket、前端页面
└── tools/               # 扩展工具模块
    ├── hopaw-tool-webpage/  # 网页抓取工具（Playwright）
    └── hopaw-tool-ssh/      # SSH 远程工具
```

## 快速开始

### 1. 配置 AI Provider

编辑 `hopaw-app/src/main/resources/application.properties`：

```properties
api.provider=openai
openai.api.key=your_api_key_here
openai.base.url=https://api.openai.com/v1
openai.model.name=gpt-3.5-turbo
```

### 2. 构建与运行

```bash
# 构建项目
mvn clean install

# 运行应用
mvn -pl hopaw-app spring-boot:run
```

### 3. 访问应用

浏览器打开：http://localhost:8080

## 配置说明

### 数据库

使用 SQLite，数据库文件 `agent.db` 自动创建于项目根目录。

### 自定义工具

实现 `AgentTool` 接口并使用 `@Tool` 注解：

```java
@Service
public class MyTool implements AgentTool {
    @Tool("工具描述")
    public String myMethod(String param) {
        return "result";
    }
    
    @Override public String getName() { return "myTool"; }
    @Override public String getDescription() { return "我的工具"; }
}
```

## API 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/` | 首页 |
| POST | `/agent/create` | 创建 Agent |
| POST | `/agent/update` | 更新 Agent |
| POST | `/agent/delete` | 删除 Agent |
| POST | `/chat` | 发送消息 |
| WS | `/ws/chat` | WebSocket 实时通信 |

## 开发指南

### 模块依赖关系

```
hopaw-contract ← hopaw-infra ← hopaw-biz ← hopaw-app
                        ↖ hopaw-tool-webpage
                        ↖ hopaw-tool-ssh
```

- **hopaw-contract**: 定义接口、DTO、枚举
- **hopaw-infra**: 数据访问、工具服务、AI 模型
- **hopaw-biz**: Agent 业务逻辑、记忆管理
- **hopaw-app**: Web 层、前端界面

### 添加新工具模块

1. 在 `tools/` 目录创建模块
2. 实现 `AgentTool` 接口
3. 在父 pom.xml 中添加 module
4. 在 `hopaw-biz/pom.xml` 中引入依赖

## 常见问题

**Q: API 调用失败？**  
A: 检查 `application.properties` 中的 API 配置和网络连接。

**Q: 如何扩展 AI 厂商？**  
A: 实现 `ChatModelFactory` 接口并在配置中注册。

**Q: SSH 连接失败？**  
A: 确认目标服务器 SSH 服务正常，凭据正确。
