package com.agent.hopaw.tool.gomoku;

import com.agent.hopaw.infra.plugin.AbstractAgentPlugin;

/**
 * 五子棋对弈插件主体。
 */
public class GomokuPlugin extends AbstractAgentPlugin {

    public GomokuPlugin() {
        // 插件作为工具工厂：分发本插件提供的工具实例
        tools(new GomokuTool());
    }

    @Override
    public String getId() {
        return "gomoku";
    }

    @Override
    public String getDescription() {
        return "五子棋对弈工具：LLM 与用户在浏览器前端对弈五子棋，支持开局、落子、等待用户落子、查棋盘、结束对局";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getKeyword() {
        return "五子棋,gomoku,棋,对弈,下棋";
    }

    @Override
    public String getIcon() {
        return "gomoku-tool.svg";
    }

    @Override
    public java.util.Map<String, Object> invoke(String toolRef, java.util.Map<String, Object> params) {
        return ((GomokuTool) getTools().get(0)).invoke(params);
    }
}
