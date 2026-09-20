package com.agent.hopaw.tool.ssh;

import com.agent.hopaw.infra.plugin.AbstractAgentPlugin;

/**
 * SSH 插件主体。
 */
public class SshPlugin extends AbstractAgentPlugin {

    public SshPlugin() {
        // 插件作为工具工厂：分发本插件提供的工具实例
        tools(new SshTool());
    }

    @Override
    public String getId() {
        return "ssh";
    }

    @Override
    public String getDescription() {
        return "SSH远程连接、命令执行与文件传输工具。";
    }

    @Override
    public String getVersion() {
        return "1.0.2";
    }

    @Override
    public String getIcon() {
        return "ssh-tool.svg";
    }

    @Override
    public String getKeyword() {
        return "SSH, SFTP";
    }
}
