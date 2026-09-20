package com.agent.hopaw.tool.baidusearch;

import com.agent.hopaw.infra.plugin.AbstractAgentPlugin;

/**
 * 百度搜索插件主体。
 */
public class BaiduSearchPlugin extends AbstractAgentPlugin {

    public BaiduSearchPlugin() {
        // 插件作为工具工厂：分发本插件提供的工具实例
        tools(new BaiduSearchTool());
    }

    @Override
    public String getId() {
        return "baiduSearch";
    }

    @Override
    public String getDescription() {
        return "百度搜索网页信息，返回相关的网页标题和摘要内容。请到：https://console.bce.baidu.com/ai-search/resource/manage 申请key";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getIcon() {
        return "baidu-search-tool.svg";
    }

    @Override
    public String getKeyword() {
        return "搜索,百度,千帆,联网,web,搜索,查找,查询";
    }
}
