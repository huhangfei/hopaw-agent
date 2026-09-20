package com.agent.hopaw.tool.demo;

import com.agent.hopaw.infra.plugin.AbstractAgentPlugin;

/**
 * 演示插件主体。
 *
 * <p>迁移说明（S2 样板）：插件身份元数据（id / 版本 / 作者 / 图标 / 关键字 / 描述）
 * 从原 AgentTool 实现类上移到本类；工具类只保留「工具」本身的职责。
 * 插件 id 沿用原工具集名，保证前端 invoke 路由与插件商店的已安装判定口径一致。</p>
 */
public class DemoPlugin extends AbstractAgentPlugin {

    public DemoPlugin() {
        // 插件作为工具工厂：分发本插件提供的工具实例（提供 0..N 个）
        tools(new DemoTool());
    }

    @Override
    public String getId() {
        return "demoPluginTool";
    }

    @Override
    public String getDescription() {
        return "插件式演示插件，提供系统信息查询功能（动态加载），并示范映射组结构配置的读取";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getKeyword() {
        return "系统,信息,状态";
    }
}
