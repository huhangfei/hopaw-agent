package com.agent.hopaw.tool.command;

import com.agent.hopaw.infra.plugin.AbstractAgentPlugin;

/**
 * 命令执行插件主体。
 */
public class CommandExecutorPlugin extends AbstractAgentPlugin {

    public CommandExecutorPlugin() {
        // 插件作为工具工厂：分发本插件提供的工具实例
        tools(new CommandExecutorTool());
    }

    @Override
    public String getId() {
        return "command";
    }

    @Override
    public String getDescription() {
        return "执行本地系统命令";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getKeyword() {
        return "本地命令";
    }

    @Override
    public String getIcon() {
        return "command-executor-tool.svg";
    }
}
