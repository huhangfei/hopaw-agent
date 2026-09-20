package com.agent.hopaw.tool.dingtalk;

import com.agent.hopaw.infra.plugin.AbstractAgentPlugin;

/**
 * 钉钉通知插件主体。
 */
public class DingTalkPlugin extends AbstractAgentPlugin {

    public DingTalkPlugin() {
        // 插件作为工具工厂：分发本插件提供的工具实例
        tools(new DingTalkTool());
    }

    @Override
    public String getId() {
        return "dingtalkNotify";
    }

    @Override
    public String getDescription() {
        return "钉钉机器人通知工具，支持发送文本和Markdown消息到钉钉群";
    }

    @Override
    public String getVersion() {
        return "1.0.1";
    }

    @Override
    public String getIcon() {
        return "dingtalk-notify.svg";
    }

    @Override
    public String getKeyword() {
        return "钉钉,通知,消息,dingtalk,群消息,机器人";
    }
}
