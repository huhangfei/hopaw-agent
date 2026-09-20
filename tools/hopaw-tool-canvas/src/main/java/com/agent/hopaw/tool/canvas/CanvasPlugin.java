package com.agent.hopaw.tool.canvas;

import com.agent.hopaw.infra.plugin.AbstractAgentPlugin;

/**
 * 画布绘图插件主体。
 */
public class CanvasPlugin extends AbstractAgentPlugin {

    public CanvasPlugin() {
        // 插件作为工具工厂：分发本插件提供的工具实例
        tools(new CanvasTool());
    }

    @Override
    public String getId() {
        return "canvas";
    }

    @Override
    public String getDescription() {
        return "画布绘图工具：在浏览器前端并排展示实时画布并绘制图形，结束时获取画布结果图";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getKeyword() {
        return "画布,绘图,canvas,绘制";
    }

    @Override
    public String getIcon() {
        return "canvas-tool.svg";
    }
}
