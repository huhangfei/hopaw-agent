# Hopaw Agent

## 项目介绍

Hopaw Agent 是一个基于 Java 和 LangChain4j 的智能 Agent 开发学习项目。该项目演示了如何使用 Spring Boot 和 LangChain4j 构建具有工具调用能力的 AI Agent 系统，支持多 Agent 管理、实时通信和流式响应等功能。

## 技术栈

- **Java 17** - 编程语言
- **Spring Boot 2.7.18** - 应用框架
- **LangChain4j 1.13.1** - AI Agent 框架
- **MyBatis** - ORM 框架
- **SQLite** - 轻量级数据库
- **WebSocket** - 实时通信
- **Thymeleaf** - 模板引擎
- **Spring Security** - 安全框架
- **JSch** - SSH 远程连接

## 功能特性

### 🤖 Agent 管理
- 创建、编辑、删除自定义 Agent
- 为 Agent 分配特定工具集
- Agent 配置持久化存储
- 支持流式对话响应
- 实时 Thinking 过程展示

### 🛠️ 内置工具
- **命令执行工具** (`command`) - 执行本地命令
- **日期时间工具** (`currentDateTime`) - 获取当前日期时间
- **记忆管理工具** (`memory`) - 长期记忆存取
- **网页抓取工具** (`webPage`) - 抓取并解析网页
- **网络搜索工具** (`webSearch`) - 搜索网络信息
- **SSH 远程工具** (`ssh`) - SSH 远程连接与命令执行

### 💬 交互功能
- 支持普通对话和流式响应
- WebSocket 实时通信
- 聊天历史记录保存（最近100条）
- ToolCall 执行状态实时展示
- Thinking 过程可视化

## 项目结构

```
hopaw-agent/
├── src/main/java/com/agent/hopaw/
│   ├── config/              # 配置类
│   │   ├── DataInitializer.java         # 数据初始化
│   │   ├── SecurityConfig.java          # 安全配置
│   │   ├── WebSocketConfig.java         # WebSocket 配置
│   │   └── ChatModelFactoryConfig.java  # ChatModel 工厂配置
│   ├── controller/          # 控制器
│   │   └── AgentController.java         # Agent 相关接口
│   ├── mapper/              # 数据访问层
│   │   ├── AgentMapper.java             # Agent 数据映射
│   │   ├── ChatHistoryMapper.java       # 聊天历史数据映射
│   │   ├── ChatMemoryMapper.java        # 聊天记忆映射
│   │   └── LongTermMemoryMapper.java    # 长期记忆映射
│   ├── model/               # 数据模型
│   │   ├── Agent.java                    # Agent 实体
│   │   ├── ChatHistory.java              # 聊天历史实体
│   │   ├── LongTermMemory.java           # 长期记忆实体
│   │   ├── ThinkingInfo.java             # 思考信息实体
│   │   ├── ToolCallInfo.java             # 工具调用信息实体
│   │   ├── ChatModelFactory.java         # ChatModel 工厂接口
│   │   └── OpenAiChatModelFactory.java  # OpenAI ChatModel 工厂实现
│   ├── service/             # 业务逻辑
│   │   ├── AgentService.java            # Agent 核心服务
│   │   └── LongTermMemoryService.java   # 长期记忆服务
│   ├── tools/               # 工具实现
│   │   ├── AgentTool.java               # 工具接口
│   │   ├── CommandExecutorTool.java     # 命令执行工具
│   │   ├── CurrentDateTimeTool.java     # 日期时间工具
│   │   ├── MemoryTool.java              # 记忆管理工具
│   │   ├── SshTool.java                 # SSH 远程工具
│   │   ├── WebPageTool.java             # 网页抓取工具
│   │   └── WebSearchTool.java           # 网络搜索工具
│   ├── websocket/           # WebSocket 处理
│   │   └── ChatWebSocketHandler.java    # WebSocket 处理器
│   └── AgentDemoApplication.java        # 启动类
├── src/main/resources/
│   ├── mapper/              # MyBatis XML 映射文件
│   ├── static/              # 静态资源
│   │   ├── css/             # 样式文件
│   │   └── js/              # JavaScript 文件
│   ├── templates/           # Thymeleaf 模板
│   └── application.properties    # 应用配置
└── pom.xml                  # Maven 依赖配置
```

## 环境要求

- JDK 17 或更高版本
- Maven 3.6+
- 推荐使用支持 Java 17 的 IDE（如 IntelliJ IDEA）

## 安装教程

### 1. 克隆项目
```bash
git clone <your-repository-url>
cd hopaw-agent
```

### 2. 配置 AI Provider
在 `src/main/resources/application.properties` 文件中配置 AI 提供商：

```properties
# API Provider 配置 (openai / deepseek / qwen 等)
api.provider=openai

# OpenAI Configuration
openai.api.key=your_api_key_here
openai.base.url=https://api.openai.com/v1
openai.model.name=gpt-3.5-turbo
```

> 💡 **提示**: 支持多种 AI 提供商，通过 `api.provider` 配置切换。当前已实现 OpenAI 厂商，可扩展其他厂商。

### 3. 构建项目
```bash
mvn clean install
```

### 4. 运行项目
```bash
mvn spring-boot:run
```

或者直接运行打包后的 JAR 文件：
```bash
mvn package
java -jar target/agent-demo-1.0.0.jar
```

### 5. 访问应用
打开浏览器访问：http://localhost:8080

## 使用说明

### 1. 创建 Agent
1. 访问首页 http://localhost:8080
2. 填写 Agent 名称和描述
3. 选择要分配给该 Agent 的工具
4. 点击创建按钮

### 2. 与 Agent 对话
1. 从列表中选择已创建的 Agent
2. 在输入框中输入问题
3. 点击发送查看 Agent 回复
4. 聊天记录会自动保存
5. 可实时看到 Thinking 过程和 ToolCall 执行状态

### 3. 管理 Agent
- **编辑**: 修改 Agent 的名称、描述或工具配置
- **删除**: 删除不需要的 Agent（同时会清除相关聊天记录和记忆）
- **清空聊天**: 清除特定 Agent 的聊天记录

### 4. SSH 远程连接
```
连接: {"action":"connect","host":"192.168.1.100","port":22,"username":"user","password":"pass"}
执行: {"action":"exec","sessionKey":"192.168.1.100:22","command":"ls -la"}
断开: {"action":"disconnect","sessionKey":"192.168.1.100:22"}
```

## 配置说明

### 数据库配置
项目使用 SQLite 作为默认数据库，数据库文件 `agent.db` 会自动创建在项目根目录。

```properties
spring.datasource.url=jdbc:sqlite:agent.db
spring.datasource.driver-class-name=org.sqlite.JDBC
```

### 自定义工具
实现 `AgentTool` 接口：
```java
@Service
public class MyTool implements AgentTool {
    @Tool("工具描述")
    public String myMethod(String param) {
        // 实现逻辑
    }

    @Override
    public String getName() {
        return "myTool";
    }

    @Override
    public String getDescription() {
        return "工具描述";
    }
}
```

### 扩展 ChatModel 厂商
1. 实现 `ChatModelFactory` 接口
2. 在 `ChatModelFactoryConfig` 中注册新厂商

## API 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/` | 首页 |
| POST | `/agent/create` | 创建 Agent |
| POST | `/agent/update` | 更新 Agent |
| POST | `/agent/delete` | 删除 Agent |
| POST | `/chat` | 发送消息 |
| GET | `/chat/clear` | 清空聊天记录 |
| WS | `/ws/chat` | WebSocket 实时通信 |

## 数据库表

| 表名 | 说明 |
|------|------|
| agents | Agent 配置 |
| chat_history | 聊天历史（text/thinking/tool_call 类型） |
| chat_memory | 短期对话记忆 |
| long_term_memory | 长期记忆（按哈希去重） |
| memory_process_log | 记忆处理日志 |

## 开发指南

### 添加新工具
1. 在 `src/main/java/com/agent/hopaw/tools/` 目录下创建工具类
2. 实现 `AgentTool` 接口，使用 `@Tool` 注解标记方法
3. 添加 `@Service` 注解让 Spring 自动扫描

### 扩展 AI 厂商
1. 实现 `ChatModelFactory` 接口
2. 在 `ChatModelFactoryConfig` 中注册

## 常见问题

### Q: API 调用失败怎么办？
A: 检查 `application.properties` 中的 API 配置是否正确，确保网络连接正常。

### Q: 如何查看日志？
A: 启动后控制台会输出应用日志，可调整日志级别。

### Q: SSH 连接失败？
A: 确保目标服务器 SSH 服务正常运行，检查用户名密码是否正确。
