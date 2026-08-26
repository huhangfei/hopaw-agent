# AGENTS.md

## Project Overview

Java 17 + Spring Boot 2.7.18 + LangChain4j 1.14 AI Agent platform. Multi-module Maven project with SQLite backend.

## Build & Run

```bash
mvn clean install                          # build all modules
mvn -pl hopaw-app spring-boot:run          # run app (port 8080)
```

App entry point: `hopaw-app/src/main/java/com/agent/hopaw/AgentApplication.java`

Quick start/stop on Windows: `run.bat` / `run.bat stop` / `stop.bat`

## Module Structure

```
hopaw-contract   # interfaces, DTOs, entities (no deps)
    ↓
hopaw-infra      # data access, AI model factories, plugin loader, memory, services
    ↓
hopaw-biz        # built-in AgentTools, business logic
    ↓
hopaw-app        # web layer, WebSocket, Thymeleaf UI, controllers
    ↓
hopaw-avatar     # avatar/TTS module (depends on hopaw-infra)

tools/           # independently packaged plugin tools (each is a separate Maven module)
hopaw-plugin-repo  # separate deployable plugin registry service (has its own Docker setup)
```

Dependency flow: `contract → infra → biz → app`. Tool modules depend only on `hopaw-contract`.

## Tool System

Two types of tools, both implement `AgentTool` interface from `hopaw-contract`:

| Type | Location | Registration | Annotation |
|------|----------|-------------|------------|
| Built-in | `hopaw-biz/src/main/java/.../biz/tool/` | `@Component("toolName")` | Uses `@Tool` + `@ToolSecurityLevel` |
| Plugin | `tools/hopaw-tool-*/` → JAR in `plugins/` | `JarPluginLoader` dynamic loading | Same interface, no `@Component` |

**Adding a new built-in tool:**
1. Create class in `hopaw-biz` implementing `AgentTool`
2. Annotate with `@Component("yourToolName")`
3. Annotate methods with `@Tool("description")` and `@ToolSecurityLevel(Level.SAFE)`

**Adding a new plugin tool:**
1. Create module under `tools/` implementing `AgentTool`
2. Add module to root `pom.xml`
3. Build: `mvn clean install -pl tools/hopaw-tool-yourmodule`
4. Copy JAR to `plugins/` directory

See `.trae/skills/agenttool-develop-install/SKILL.md` for full plugin development guide.

## Key Architecture Notes

- **AI Model Providers:** OpenAI, DeepSeek, Anthropic, Google, QianWen, ZhiPu, Moonshot, MiniMax — each has a factory in `hopaw-infra/.../chat/`
- **Plugin Loading:** `JarPluginLoader` + `PluginClassLoader` in `hopaw-infra/.../plugin/`
- **Memory:** Short-term (SQLite chat memory) + Long-term (JVector vector store with BGE embeddings)
- **WebSocket:** `ChatWebSocketHandler` in `hopaw-app` for streaming responses
- **MCP:** `langchain4j-mcp` integration in `hopaw-infra`
- **Scheduled Tasks:** `@EnableScheduling` + `DynamicTaskService`

## Runtime Config

- SQLite database: `agent.db` in project root (auto-created)
- Plugin directory: `plugins/`
- Attachment directory: `attachments/` (config: `hopaw.attachment.dir`)
- Project space directory: `project-spaces/` (config: `hopaw.project.space.dir`) — each project gets a subdir by project ID as its workspace; task execution prompts restrict file ops to within the project's space
- Profiles: `dev` (default, DEBUG logging), `prod` (INFO logging)
- Config: `hopaw-app/src/main/resources/application.properties`
- Dev-specific: `application-dev.properties` (not committed — see `.gitignore`)

## Plugin Repository (hopaw-plugin-repo)

Separate Spring Boot app for hosting plugin packages. Deploy via Docker:

```bash
cd hopaw-plugin-repo
docker-compose up -d
```

See `hopaw-plugin-repo/DOCKER.md` for full deployment guide.

## Conventions

- Package root: `com.agent.hopaw` (sub-packages: `infra`, `biz`, `app`, `avatar`)
- Service interfaces in `hopaw-contract`, implementations in `hopaw-infra`
- MyBatis mapper XMLs: `classpath*:mapper/**/*Mapper.xml` (scan across all modules)
- Tool security levels: `SAFE`, `ALL_REQUIRE_APPROVAL`, `PARAM_REQUIRE_APPROVAL`
- No test suite exists — `spring-boot-starter-test` is in deps but no test files
- No CI/CD pipeline configured
- Language: code comments and docs are primarily in Chinese

## Gotchas

- `hopaw-tool-webpage` module is **commented out** in root `pom.xml` (not built)
- `application-dev.properties` is gitignored — won't be in repo
- `agent.db`, `plugins/`, `plugin-packages/`, `skills/`, `uploads/`, `attachments/`, `project-spaces/` are all gitignored runtime artifacts
- Java compiler plugin sets source/target to 16, but project property is Java 17 — trust `java.version=17` in pom.xml properties
