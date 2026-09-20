package com.agent.hopaw.tool.webpage;

import com.agent.hopaw.infra.plugin.AbstractAgentPlugin;

/**
 * 网页内容获取插件主体（Playwright 实现）。
 */
public class WebPagePlaywrightPlugin extends AbstractAgentPlugin {

    public WebPagePlaywrightPlugin() {
        // 插件作为工具工厂：分发本插件提供的工具实例
        tools(new WebPagePlaywrightTool());
    }

    @Override
    public String getId() {
        return "webPagePlaywright";
    }

    @Override
    public String getDescription() {
        return "使用Playwright获取网页内容，输入URL地址，返回网页的纯文本内容。适用于获取网页文章、文档等文本内容。";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getIcon() {
        return "web-page-tool.svg";
    }

    @Override
    public String getKeyword() {
        return "网页,url";
    }
}
