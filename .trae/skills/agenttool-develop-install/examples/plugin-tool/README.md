# 插件工具示例 (PLUGIN)

## 目录结构

```
plugin-tool/
├── pom.xml
└── src/main/java/com/example/tool/MyPluginTool.java
```

## 构建

```bash
mvn clean package
```

输出 `target/my-agent-tool-1.0.0.jar`。

## 安装

### 方式 A：LLM 自动安装

```
用户：请帮我安装 D:\path\to\my-agent-tool-1.0.0.jar
智能体：调用 installPluginFromLocalJar("D:\path\to\my-agent-tool-1.0.0.jar")
```

### 方式 B：手动上传

1. 访问 `/tools`
2. 点击"本地安装"按钮
3. 选择 `my-agent-tool-1.0.0.jar`
4. 等待安装完成

## 特点

- **不需要** `@Component` 注解
- 独立 Maven 项目，可单独发布
- 依赖 `scope=provided`，由宿主 ClassLoader 加载
- 支持热加载/卸载
- 工具管理页面 `来源` 列显示 `插件`
