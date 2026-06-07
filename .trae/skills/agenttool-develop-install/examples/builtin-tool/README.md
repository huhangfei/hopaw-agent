# 内置工具示例 (BUILT_IN)

## 目录结构

```
builtin-tool/
├── pom.xml
└── src/main/java/com/agent/hopaw/biz/tool/example/ExampleTool.java
```

## pom.xml

复用 `hopaw-biz` 父模块即可，无需独立 pom。

## ExampleTool.java

完整实现见 [SKILL.md 第一节"实现 AgentTool 接口"](../SKILL.md#1-实现-agenttool-接口)。

## 特点

- 必须添加 `@Component("exampleTool")`
- 放在 `hopaw-biz` 模块下
- 随应用启动自动注册
- 工具管理页面 `来源` 列显示 `内置工具`
- 修改后需重启应用

## 使用场景

- 工具与 HoPaw 核心功能紧密耦合
- 需要访问应用内部 Service / Mapper
- 频繁迭代，与主版本同步发布
