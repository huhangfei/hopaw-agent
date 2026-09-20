package com.agent.hopaw.tool.http;

import com.agent.hopaw.infra.plugin.AbstractAgentPlugin;

/**
 * HTTP 请求插件主体。
 */
public class HttpPlugin extends AbstractAgentPlugin {

    public HttpPlugin() {
        // 插件作为工具工厂：分发本插件提供的工具实例
        tools(new HttpTool());
    }

    @Override
    public String getId() {
        return "httpTool";
    }

    @Override
    public String getDescription() {
        return "HTTP请求工具集，支持GET/POST/PUT/DELETE/PATCH/HEAD/OPTIONS等常见HTTP方法，用于接口调用测试";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getKeyword() {
        return "http,接口,api,request";
    }

    @Override
    public String getIcon() {
        return "http-tool.svg";
    }
}
