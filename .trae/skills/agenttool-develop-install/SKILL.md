---
name: "agenttool-develop-install"
description: "Guides development of new AgentTool plugins and explains automated and manual installation paths (via LLM, /tools page, plugin store, or export/import). Invoke when user wants to develop, package, install, upgrade, export, or troubleshoot an AgentTool plugin, or asks how to add a new tool to HoPaw Agent."
version: "1.0.0"
author: "HoPaw Agent Team"
created: "2026-06-03"
updated: "2026-06-03"
applies_to: "HoPaw Agent 1.0.0+"
audience: "LLM agents, plugin developers, ops engineers"
related_interfaces:
  - "com.agent.hopaw.infra.tool.IAgentToolService"
  - "com.agent.hopaw.infra.tool.AgentTool"
  - "com.agent.hopaw.biz.tool.plugin.ToolManagerTool"
depends_on:
  - "hopaw-contract"
  - "hopaw-biz"
  - "hopaw-app"
status: "stable"
language: "zh-CN"
repository: "https://gitee.com/hgflydream/hopaw-agent"
support: "https://gitee.com/hgflydream/hopaw-agent/issues"
---

# AgentTool 插件开发与安装指南

## 目录

1. [概述](#概述)
2. [开发规范](#开发规范)
3. [高级能力：IAgentExecutorService](#高级能力iagentexecutorservice)
4. [方式一：内置工具开发（BUILT_IN）](#方式一内置工具开发built_in)
5. [方式二：插件工具开发（PLUGIN）](#方式二插件工具开发plugin)
6. [安装方式](#安装方式)
7. [API 参考](#api-参考)
8. [快速检查清单](#快速检查清单)
9. [示例工程](#示例工程)

---

## 概述

HoPaw Agent 支持两种工具类型：

| 类型 | 标识 | 存放位置 | 注册方式 |
|------|------|----------|----------|
| 内置工具 | `BUILT_IN` | `hopaw-biz` 模块 | Spring `@Component` 自动注册 |
| 插件工具 | `PLUGIN` | 独立 JAR / `plugins/` 目录 | `JarPluginLoader` 动态加载 |

无论哪种类型，核心开发规范完全相同——都实现 `AgentTool` 接口。

---

## 开发规范

### 1. 实现 AgentTool 接口

```java
package com.agent.hopaw.biz.tool.example;

import com.agent.hopaw.infra.tool.AgentTool;
import com.agent.hopaw.infra.tool.ToolSecurityLevel;
import com.agent.hopaw.infra.model.dto.ToolConfigItem;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.SearchBehavior;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.invocation.InvocationParameters;
import org.springframework.stereotype.Component;

import java.util.List;

@Component("exampleTool") // 仅内置工具需要 @Component
public class ExampleTool implements AgentTool {

    // ========== 元数据方法 ==========

    @Override
    public String getName() {
        return "exampleTool";          // 工具唯一标识（必填）
    }

    @Override
    public String getDescription() {
        return "示例工具，演示 AgentTool 开发规范";  // 工具描述（必填）
    }

    @Override
    public String getKeyword() {
        return "示例";                  // 中文关键字，便于智能体匹配
    }

    @Override
    public String getVersion() {
        return "1.0.0";                // 版本号
    }

    @Override
    public String getAuthor() {
        return "Your Name";
    }

    @Override
    public String getIcon() {
        return "example-tool.svg";     // 图标文件名（放在 /icons/tools/ 下）
    }

    // ========== Tool 方法 ==========

    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(value = "查询示例", searchBehavior = SearchBehavior.ALWAYS_VISIBLE)
    public String queryExample(
            @P(description = "查询参数") String param) {
        return "查询结果: " + param;
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.PARAM_REQUIRE_APPROVAL)
    @Tool(value = "执行危险操作", searchBehavior = SearchBehavior.ALWAYS_VISIBLE)
    public String dangerousAction(
            @P(description = "操作目标") String target,
            InvocationParameters invocationParameters) {
        // 通过 InvocationParameters 获取调用上下文
        // InvocationParametersWrapper wrapper = InvocationParametersWrapper.create(invocationParameters);
        return "操作完成: " + target;
    }

    // ========== 配置项（可选） ==========

    @Override
    public List<ToolConfigItem> getConfigItems() {
        return List.of(
            new ToolConfigItem("apiKey", "API密钥", "第三方API密钥",
                ToolConfigItem.ConfigType.TEXT_PASSWORD),
            new ToolConfigItem("model", "模型选择", "选择使用的模型",
                ToolConfigItem.ConfigType.SELECT,
                List.of("gpt-4", "gpt-3.5", "claude"))
        );
    }
}
```

### 2. 关键注解说明

| 注解 | 位置 | 说明 |
|------|------|------|
| `@Tool(value, searchBehavior)` | 方法 | 声明该方法为 LLM 可调用的工具。`value` 是供 LLM 理解的方法描述，`searchBehavior` 设为 `ALWAYS_VISIBLE` 确保工具搜索可见 |
| `@ToolSecurityLevel(Level)` | 方法 | 安全等级：`SAFE`（无需审批）、`ALL_REQUIRE_APPROVAL`（全部审批）、`PARAM_REQUIRE_APPROVAL`（参数审批） |
| `@P(description)` | 参数 | 工具参数的描述，供 LLM 理解参数含义 |
| `InvocationParameters` | 参数 | 框架自动注入的调用上下文，可通过 `InvocationParametersWrapper.create()` 获取 userId、agentId 等 |

### 3. 可选方法

| 方法 | 触发时机 | 用途 |
|------|----------|------|
| `asyncInit()` | 加载后异步执行 | 连接第三方服务、预热缓存等 |
| `destroy()` | 卸载时调用 | 释放连接、清理资源 |
| `getConfigItems()` | 工具管理页面加载 | 声明可配置项 |
| `getConfigPrefix()` | 读取配置时 | 默认 `"tool." + getName() + "."` |

---

## 高级能力：IAgentExecutorService

对于长任务（流式输出、可取消）、异步执行、用户审批等场景，框架提供 `IAgentExecutorService` 能力集。通过 `InvocationParameters` 拿到调用上下文后，工具可与执行器协作：

### 1. InvocationParameters 上下文

| 字段 | 类型 | 用途 |
|------|------|------|
| `userId` | String | 当前用户 ID |
| `agentId` | Long | 智能体 ID |
| `sessionId` | String | 会话 ID |
| `toolCallId` | String | 工具调用 ID（与 LLM 工具调用绑定） |
| `requestId` | String | 请求 ID |

通过 `InvocationParametersWrapper.create(invocationParameters)` 获取封装：

```java
InvocationParametersWrapper wrapper = InvocationParametersWrapper.create(invocationParameters);
String sessionId = wrapper.getSessionId();
String toolCallId = wrapper.getToolCallId();
Long agentId = wrapper.getAgentId();
```

### 2. IAgentExecutorService API

| 方法 | 说明 |
|------|------|
| `addToolStopHook(sessionId, callId, hook)` | 注册"工具被停止时"的回调，用于销毁进程、释放资源 |
| `sendToolRunningContent(sessionId, callId, partial)` | **流式推送**工具运行中的部分结果，前端可实时展示 |
| `toolIsCancelled(sessionId, callId)` | 检查工具是否被用户取消（轮询用） |
| `toolApprovalComplete(sessionId, callId, allowed)` | 工具审批完成时通知执行器 |
| `stopTool(sessionId, callId)` | 主动停止工具 |
| `stopAgentExecutor(sessionId)` | 停止整个会话的执行器 |
| `getAgentExecutor(sessionId)` | 获取会话执行器实例 |

> **注意**：`IAgentExecutorService` 是宿主 Spring 容器中的 Bean。**插件工具**需要通过 `beanFactory`（如 `SpringContextHolder`）获取，不能直接 `@Autowired`。

### 3. 完整示例：长任务可取消 + 流式输出

参考 `CommandExecutorTool`，实现一个"流式执行 + 可取消 + 自动清理"的长任务工具：

```java
package com.example.tool;

import com.agent.hopaw.infra.service.IAgentExecutorService;
import com.agent.hopaw.infra.tool.AgentTool;
import com.agent.hopaw.infra.tool.ToolSecurityLevel;
import com.agent.hopaw.infra.util.InvocationParametersWrapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.SearchBehavior;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.invocation.InvocationParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 示例：执行本地系统命令（带流式输出 + 可取消 + 自动清理）。
 * 演示 IAgentExecutorService 的标准用法。
 */
public class CommandExecutorTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(CommandExecutorTool.class);
    private static final int TIMEOUT_SECONDS = 30;
    private static final int MAX_OUTPUT_LINES = 500;

    /** 插件工具通过 SpringContextHolder 拿到宿主 Bean */
    private IAgentExecutorService agentExecutorService() {
        return SpringContextHolder.getBean(IAgentExecutorService.class);
    }

    @Override
    public String getName() { return "commandExecutor"; }

    @Override
    public String getDescription() { return "执行本地系统命令（Windows / Linux / macOS）"; }

    @Override
    public String getKeyword() { return "命令 shell"; }

    @Override
    public String getIcon() { return "command-executor-tool.svg"; }

    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(value = "获取操作系统名称", searchBehavior = SearchBehavior.ALWAYS_VISIBLE)
    public String getOsName() {
        return System.getProperty("os.name");
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.ALL_REQUIRE_APPROVAL)
    @Tool(value = {
            "执行命令",
            "执行本地系统命令并流式返回输出。支持 Windows / Linux / macOS。"
    }, searchBehavior = SearchBehavior.ALWAYS_VISIBLE)
    public String executeCommand(
            @P(description = "要执行的命令") String command,
            @P(description = "超时时间（秒）", required = false) Integer timeout,
            InvocationParameters invocationParameters) {

        InvocationParametersWrapper wrapper = InvocationParametersWrapper.create(invocationParameters);
        String sessionId = wrapper.getSessionId();
        String toolCallId = wrapper.getToolCallId();
        IAgentExecutorService executor = agentExecutorService();

        log.info("Executing command: {} toolCallId={}", command, toolCallId);
        if (command == null || command.trim().isEmpty()) {
            return "错误: 命令不能为空";
        }
        if (timeout == null) timeout = TIMEOUT_SECONDS;

        Process process;
        try {
            String os = System.getProperty("os.name").toLowerCase();
            ProcessBuilder pb = os.contains("win")
                    ? new ProcessBuilder("cmd.exe", "/c", command)
                    : new ProcessBuilder("/bin/sh", "-c", command);
            pb.redirectErrorStream(true);
            process = pb.start();
        } catch (Exception e) {
            return "错误: 启动进程失败 - " + e.getMessage();
        }

        // ========== 注册停止钩子：用户停止/会话关闭时自动销毁进程 ==========
        executor.addToolStopHook(sessionId, toolCallId, callId -> {
            try {
                ProcessHandle handle = process.toHandle();
                handle.descendants().forEach(ProcessHandle::destroyForcibly);
                handle.destroyForcibly();
            } catch (Exception e) {
                process.destroyForcibly();
            }
        });

        StringBuilder output = new StringBuilder();
        Charset charset = os().contains("win") ? Charset.forName("GBK") : Charset.UTF_8;

        // ========== 异步读取输出 + 流式推送 + 取消检查 ==========
        CompletableFuture<Void> reader = CompletableFuture.runAsync(() -> {
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), charset))) {
                String line;
                int count = 0;
                while ((line = br.readLine()) != null) {
                    if (executor.toolIsCancelled(sessionId, toolCallId)) {
                        output.append("错误: 命令执行被用户取消");
                        break;
                    }
                    if (count >= MAX_OUTPUT_LINES) {
                        output.append("\n... (输出已截断，超过 ").append(MAX_OUTPUT_LINES).append(" 行)");
                        break;
                    }
                    // 流式推送到前端
                    executor.sendToolRunningContent(sessionId, toolCallId, line + "\n");
                    output.append(line).append("\n");
                    count++;
                }
            } catch (Exception e) {
                log.error("读取进程输出失败: {}", e.getMessage(), e);
            }
        });

        try {
            if (process.waitFor(timeout, TimeUnit.SECONDS)) {
                reader.get(5, TimeUnit.SECONDS);
                int exit = process.exitValue();
                return output.isEmpty()
                        ? "命令执行成功 (退出码: " + exit + ")，无输出"
                        : "退出码: " + exit + "\n\n" + output;
            } else {
                process.toHandle().descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                return "错误: 超时未执行完成，已强制退出。\n" + output;
            }
        } catch (Exception e) {
            process.destroyForcibly();
            return "错误: " + e.getMessage();
        }
    }

    private static String os() {
        return System.getProperty("os.name").toLowerCase();
    }
}
```

### 4. 关键模式总结

| 模式 | 实现方式 |
|------|----------|
| **流式输出** | 在异步线程里循环调用 `sendToolRunningContent(sessionId, callId, partial)` |
| **用户取消** | 循环里轮询 `toolIsCancelled(sessionId, callId)`，命中即 break 并清理 |
| **资源清理** | `addToolStopHook` 注册 Lambda，框架在工具被停止/会话关闭时回调 |
| **超时控制** | `Process.waitFor(timeout, TimeUnit.SECONDS)` 返回 false 即超时 |
| **输出截断** | 计数器达到上限就 break，避免内存爆炸 |
| **跨平台** | Windows 用 `cmd.exe /c`，其他用 `/bin/sh -c` |
| **编码处理** | Windows 默认 GBK，其他 UTF_8（可读 `LANG` 环境变量优先） |

### 5. 插件工具获取 Spring Bean

由于插件由 `JarPluginLoader` 反射实例化、不会走 Spring `@Autowired`，需要通过工具类主动从 Spring 容器获取 Bean。常见做法：

```java
public class SpringContextHolder implements ApplicationContextAware {
    private static ApplicationContext context;
    @Override public void setApplicationContext(ApplicationContext ctx) {
        context = ctx;
    }
    public static <T> T getBean(Class<T> clazz) {
        return context.getBean(clazz);
    }
}
```

将 `SpringContextHolder` 注册为内置工具或普通 `@Component`，插件中即可使用 `SpringContextHolder.getBean(...)` 拿到宿主任意 Bean。

---

## 方式一：内置工具开发（BUILT_IN）

**适用场景**：工具与 HoPaw 核心功能紧密耦合，随应用一起发布。

### 步骤

1. 在 `hopaw-biz/src/main/java/com/agent/hopaw/biz/tool/` 下创建新包和类
2. 实现 `AgentTool` 接口
3. 添加 `@Component("toolName")` 注解
4. 通过 `@Autowired` 注入需要的 Service
5. 重启应用即可生效

**示例目录结构**：

```
hopaw-biz/src/main/java/com/agent/hopaw/biz/tool/
├── agenttask/AgentTaskTool.java
├── datetime/CurrentDateTimeTool.java
├── mail/MailTool.java
├── memory/MemoryTool.java
├── skill/SkillTool.java
├── sysconfig/SysConfigTool.java
└── plugin/PluginTool.java
```

**无需额外操作** — Spring 自动扫描并注册。工具管理页面 `/tools` 会显示 `来源：内置工具`。

---

## 方式二：插件工具开发（PLUGIN）

**适用场景**：独立分发的工具，通过插件商店或本地文件安装，支持热加载/卸载。

### 步骤

#### 1. 创建独立 Maven 项目

```xml
<!-- pom.xml -->
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.example</groupId>
    <artifactId>my-agent-tool</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>

    <properties>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
    </properties>

    <dependencies>
        <!-- 必须依赖 hopaw-contract -->
        <dependency>
            <groupId>com.hopaw.agent</groupId>
            <artifactId>hopaw-contract</artifactId>
            <version>1.0.0</version>
            <scope>provided</scope>  <!-- 运行时由宿主提供 -->
        </dependency>
        <!-- langchain4j -->
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j</artifactId>
            <version>1.0.0-beta1</version>
            <scope>provided</scope>
        </dependency>
        <!-- SLF4J -->
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-api</artifactId>
            <version>2.0.7</version>
            <scope>provided</scope>
        </dependency>
    </dependencies>
</project>
```

**关键**：所有依赖设置为 `provided`，避免与宿主应用冲突。`PluginClassLoader` 会使用父 ClassLoader 加载这些类。

#### 2. 实现工具类

参照上方"开发规范"，实现 `AgentTool` 接口。**注意**：插件工具**不需要** `@Component` 注解。

```java
package com.example.tool;

import com.agent.hopaw.infra.tool.AgentTool;
import com.agent.hopaw.infra.tool.ToolSecurityLevel;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.SearchBehavior;
import dev.langchain4j.agent.tool.Tool;

public class MyPluginTool implements AgentTool {
    // 不需要 @Component 注解！

    @Override
    public String getName() {
        return "myPluginTool";
    }

    @Override
    public String getDescription() {
        return "我的插件工具";
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(value = "我的插件功能", searchBehavior = SearchBehavior.ALWAYS_VISIBLE)
    public String myAction(@P(description = "输入参数") String input) {
        return "处理结果: " + input;
    }
}
```

#### 3. 打包

```bash
mvn clean package
```

输出 `target/my-agent-tool-1.0.0.jar`。

### 插件加载机制

`JarPluginLoader` 负责动态加载插件：

1. 扫描 JAR 中所有 `.class` 文件（支持标准 JAR 和 Spring Boot FAT JAR）
2. 通过 `PluginClassLoader.scanAgentToolClasses()` 找到所有 `AgentTool` 实现类
3. 反射实例化后调用 `beanFactory.autowireBean(tool)` 注入 Spring 依赖
4. 注册到 `DynamicToolRegistry`

---

## 安装方式

### 方式一：通过 LLM 自动安装（推荐）

用户直接对话，智能体自动调用 `PluginTool`：

| 文件类型 | 调用的 Tool 方法 | 后端调用 |
|----------|------------------|----------|
| `.zip` 压缩包 | `installPluginFromLocal(filePath)` | `IAgentToolService.installPluginFromBytes(byte[])` |
| `.jar` 文件 | `installPluginFromLocalJar(filePath)` | `IAgentToolService.installPluginFromJarFile(Path)` |

**示例对话**：

```
用户：请帮我安装 D:\plugins\my-tool.jar 这个插件
智能体：调用 installPluginFromLocalJar("D:\plugins\my-tool.jar")
→ 安装成功：myPluginTool v1.0.0，新增 2 个工具，文件：my-tool.jar
```

### 方式二：通过工具管理页面手动安装

1. 访问 `/tools`
2. 点击 **"本地安装"** 按钮（上传图标）
3. 选择 `.zip` 或 `.jar` 文件
4. 系统自动调用 `POST /tools/api/local-install` 完成安装

### 方式三：通过插件商店安装

1. 访问 `/tools/plugin-store`
2. 搜索或浏览插件列表
3. 点击 **"安装"** 按钮
4. 系统通过 `POST /tools/api/install-upgrade` 以 SSE 流式进度安装

### 方式四：导出/导入（跨实例迁移）

**导出**：

```bash
# 编程方式
curl -O http://localhost:8080/tools/api/export/{toolName}/{toolVersion}

# 或通过 LLM
用户：请导出 myPluginTool v1.0.0 插件
智能体：调用 exportPlugin("myPluginTool", "1.0.0") → 返回 zip 字节流
```

**导入**：将导出的 `.zip` 文件通过方式一或方式二安装。

---

## API 参考

### 服务层接口

| 方法 | 参数 | 说明 |
|------|------|------|
| `installPluginFromBytes(byte[])` | zip 字节数组 | 安装 zip 格式插件包（内部含 .json 清单 + .jar） |
| `installPluginFromJarFile(Path)` | jar 文件路径 | 直接安装 .jar 文件，元数据从 JAR 内扫描 |
| `exportPlugin(name, version)` | 工具名, 版本号 | 导出插件为 zip 字节数组 |
| `unloadPlugin(jarFileName)` | jar 文件名 | 卸载插件 |
| `addToolStopHook(sessionId, callId, hook)` | 会话/调用/钩子 | 注册工具停止回调（用于清理资源） |
| `sendToolRunningContent(sessionId, callId, partial)` | 会话/调用/部分结果 | 流式推送工具运行中内容 |
| `toolIsCancelled(sessionId, callId)` | 会话/调用 | 查询工具是否被用户取消 |
| `toolApprovalComplete(sessionId, callId, allowed)` | 会话/调用/是否通过 | 通知执行器工具审批结果 |
| `stopTool(sessionId, callId)` | 会话/调用 | 主动停止工具 |

### REST 端点

| 端点 | 方法 | 说明 |
|------|------|------|
| `GET /tools` | 页面 | 工具管理页面 |
| `GET /tools/api/list` | JSON | 获取所有工具集列表 |
| `POST /tools/api/local-install` | Multipart | 本地安装插件（上传文件） |
| `POST /tools/api/install-upgrade` | SSE | 在线安装/升级（带进度） |
| `GET /tools/api/export/{name}/{version}` | 下载 | 导出插件 zip |
| `POST /tools/api/unload` | JSON | 卸载插件 |
| `GET /tools/plugin-store` | 页面 | 插件商店 |
| `GET /tools/plugin-store/api/plugins` | JSON | 获取商店插件列表 |

### PluginInstallResult 字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `success` | boolean | 是否成功 |
| `toolName` | String | 工具名 |
| `version` | String | 版本号 |
| `fileName` | String | JAR 文件名 |
| `toolCount` | int | 新增工具方法数 |
| `isUpgrade` | boolean | 是否为升级 |
| `previousVersion` | String | 升级前的版本 |
| `message` | String | 结果消息 |
| `conflictInfo` | PluginConflictInfo | 冲突信息 |

---

## 快速检查清单

开发完成后确认：

- [ ] 实现了 `AgentTool` 接口的所有必需方法（`getName()`, `getDescription()`）
- [ ] 每个 `@Tool` 方法标注了 `@ToolSecurityLevel`
- [ ] `@Tool` 的 `searchBehavior` 设为 `ALWAYS_VISIBLE`
- [ ] 每个参数有 `@P(description = "...")` 注解
- [ ] 插件项目依赖 `hopaw-contract` 且 scope 为 `provided`
- [ ] 内置工具添加了 `@Component("toolName")`，插件工具**不要**添加
- [ ] 打包为 JAR 后测试安装
- [ ] 长任务工具已注册 `addToolStopHook` 清理资源
- [ ] 长任务工具已实现 `toolIsCancelled` 轮询和 `sendToolRunningContent` 流式输出
- [ ] 插件工具通过 `SpringContextHolder.getBean()` 获取宿主 Bean，未直接 `@Autowired`

---

## 示例工程

参考 `examples/` 目录下的完整样例：

- `examples/builtin-tool/`：内置工具样例（包含 `@Component`）
- `examples/plugin-tool/`：独立插件工程（不含 `@Component`）
