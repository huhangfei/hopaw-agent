# Hopaw Agent

## Project Introduction

Hopaw Agent is a Java and LangChain4j-based intelligent Agent development learning project. This project demonstrates how to build AI Agent systems with tool-calling capabilities using Spring Boot and LangChain4j, supporting multi-Agent management, real-time communication, and streaming responses.

## Tech Stack

- **Java 17** - Programming Language
- **Spring Boot 2.7.18** - Application Framework
- **LangChain4j 1.13.1** - AI Agent Framework
- **MyBatis** - ORM Framework
- **SQLite** - Lightweight Database
- **WebSocket** - Real-time Communication
- **Thymeleaf** - Template Engine
- **Spring Security** - Security Framework
- **JSch** - SSH Remote Connection

## Features

### 🤖 Agent Management
- Create, edit, and delete custom Agents
- Assign specific tool sets to Agents
- Persistent storage of Agent configurations
- Streaming dialogue response support
- Real-time Thinking process display

### 🛠️ Built-in Tools
- **Command Executor** (`command`) - Execute local commands
- **Date Time** (`currentDateTime`) - Get current date and time
- **Memory Manager** (`memory`) - Long-term memory storage
- **Web Page Scraper** (`webPage`) - Fetch and parse web pages
- **Web Search** (`webSearch`) - Search internet information
- **SSH Remote** (`ssh`) - SSH remote connection and command execution

### 💬 Interaction Features
- Support for normal and streaming responses
- WebSocket real-time communication
- Chat history saved (latest 100 messages)
- ToolCall execution status in real-time
- Thinking process visualization

## Project Structure

```
hopaw-agent/
├── src/main/java/com/agent/hopaw/
│   ├── config/              # Configuration classes
│   │   ├── DataInitializer.java         # Data initialization
│   │   ├── SecurityConfig.java          # Security configuration
│   │   ├── WebSocketConfig.java         # WebSocket configuration
│   │   └── ChatModelFactoryConfig.java  # ChatModel factory config
│   ├── controller/          # Controllers
│   │   └── AgentController.java         # Agent related endpoints
│   ├── mapper/              # Data access layer
│   │   ├── AgentMapper.java             # Agent data mapping
│   │   ├── ChatHistoryMapper.java       # Chat history mapping
│   │   ├── ChatMemoryMapper.java        # Chat memory mapping
│   │   └── LongTermMemoryMapper.java    # Long-term memory mapping
│   ├── model/               # Data models
│   │   ├── Agent.java                    # Agent entity
│   │   ├── ChatHistory.java              # Chat history entity
│   │   ├── LongTermMemory.java           # Long-term memory entity
│   │   ├── ThinkingInfo.java             # Thinking info entity
│   │   ├── ToolCallInfo.java             # Tool call info entity
│   │   ├── ChatModelFactory.java         # ChatModel factory interface
│   │   └── OpenAiChatModelFactory.java  # OpenAI ChatModel factory impl
│   ├── service/             # Business logic
│   │   ├── AgentService.java            # Agent core service
│   │   └── LongTermMemoryService.java   # Long-term memory service
│   ├── tools/              # Tool implementations
│   │   ├── AgentTool.java               # Tool interface
│   │   ├── CommandExecutorTool.java    # Command executor tool
│   │   ├── CurrentDateTimeTool.java     # Date time tool
│   │   ├── MemoryTool.java             # Memory manager tool
│   │   ├── SshTool.java                # SSH remote tool
│   │   ├── WebPageTool.java            # Web page scraper tool
│   │   └── WebSearchTool.java          # Web search tool
│   ├── websocket/          # WebSocket handling
│   │   └── ChatWebSocketHandler.java   # WebSocket handler
│   └── AgentDemoApplication.java       # Application entry
├── src/main/resources/
│   ├── mapper/              # MyBatis XML mapping files
│   ├── static/              # Static resources
│   │   ├── css/             # Stylesheets
│   │   └── js/              # JavaScript files
│   ├── templates/           # Thymeleaf templates
│   └── application.properties    # Application configuration
└── pom.xml                  # Maven dependencies
```

## Requirements

- JDK 17 or higher
- Maven 3.6+
- Recommended: IDE with Java 17 support (e.g., IntelliJ IDEA)

## Installation

### 1. Clone the project
```bash
git clone <your-repository-url>
cd hopaw-agent
```

### 2. Configure AI Provider
Configure the AI provider in `src/main/resources/application.properties`:

```properties
# API Provider config (openai / deepseek / qwen etc.)
api.provider=openai

# OpenAI Configuration
openai.api.key=your_api_key_here
openai.base.url=https://api.openai.com/v1
openai.model.name=gpt-3.5-turbo
```

> 💡 **Tip**: Supports multiple AI providers, switch via `api.provider` config. Currently implemented OpenAI provider, extensible for others.

### 3. Build the project
```bash
mvn clean install
```

### 4. Run the project
```bash
mvn spring-boot:run
```

Or run the packaged JAR file:
```bash
mvn package
java -jar target/agent-demo-1.0.0.jar
```

### 5. Access the application
Open browser: http://localhost:8080

## Usage

### 1. Create an Agent
1. Visit homepage http://localhost:8080
2. Fill in Agent name and description
3. Select tools to assign to this Agent
4. Click create button

### 2. Chat with Agent
1. Select a created Agent from the list
2. Enter question in input box
3. Click send to view Agent response
4. Chat history is saved automatically
5. You can see Thinking process and ToolCall execution status in real-time

### 3. Manage Agents
- **Edit**: Modify Agent name, description, or tool configuration
- **Delete**: Delete unwanted Agent (clears related chat history and memory)
- **Clear Chat**: Clear chat history for specific Agent

### 4. SSH Remote Connection
```
Connect: {"action":"connect","host":"192.168.1.100","port":22,"username":"user","password":"pass"}
Exec: {"action":"exec","sessionKey":"192.168.1.100:22","command":"ls -la"}
Disconnect: {"action":"disconnect","sessionKey":"192.168.1.100:22"}
```

## Configuration

### Database Configuration
The project uses SQLite as the default database, the database file `agent.db` is automatically created in the project root.

```properties
spring.datasource.url=jdbc:sqlite:agent.db
spring.datasource.driver-class-name=org.sqlite.JDBC
```

### Custom Tools
Implement `AgentTool` interface:
```java
@Service
public class MyTool implements AgentTool {
    @Tool("Tool description")
    public String myMethod(String param) {
        // Implementation logic
    }

    @Override
    public String getName() {
        return "myTool";
    }

    @Override
    public String getDescription() {
        return "Tool description";
    }
}
```

### Extending ChatModel Providers
1. Implement `ChatModelFactory` interface
2. Register in `ChatModelFactoryConfig`

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/` | Homepage |
| POST | `/agent/create` | Create Agent |
| POST | `/agent/update` | Update Agent |
| POST | `/agent/delete` | Delete Agent |
| POST | `/chat` | Send message |
| GET | `/chat/clear` | Clear chat history |
| WS | `/ws/chat` | WebSocket real-time communication |

## Database Tables

| Table | Description |
|-------|-------------|
| agents | Agent configuration |
| chat_history | Chat history (text/thinking/tool_call types) |
| chat_memory | Short-term chat memory |
| long_term_memory | Long-term memory (deduplicated by hash) |
| memory_process_log | Memory processing log |

## Development Guide

### Adding New Tools
1. Create tool class in `src/main/java/com/agent/hopaw/tools/`
2. Implement `AgentTool` interface, use `@Tool` annotation
3. Add `@Service` annotation for Spring auto-scanning

### Extending AI Providers
1. Implement `ChatModelFactory` interface
2. Register in `ChatModelFactoryConfig`

## FAQ

### Q: API call failed, what to do?
A: Check if API configuration in `application.properties` is correct, ensure network connection is normal.

### Q: How to view logs?
A: Console outputs application logs after startup, adjust log level in `application.properties`.

### Q: SSH connection failed?
A: Ensure target server SSH service is running, check if username and password are correct.
