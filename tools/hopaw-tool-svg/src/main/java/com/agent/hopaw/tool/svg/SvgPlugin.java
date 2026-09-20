package com.agent.hopaw.tool.svg;

import com.agent.hopaw.infra.plugin.AbstractAgentPlugin;

/**
 * SVG 工具插件主体。
 *
 * <p>从图片操作插件中抽取的 SVG 相关能力独立成插件：既能把 SVG 代码/文件渲染为位图，
 * 也能在会话前端插槽中并排预览、编辑源码并下载。</p>
 */
public class SvgPlugin extends AbstractAgentPlugin {

    public SvgPlugin() {
        // 插件作为工具工厂：分发本插件提供的工具实例
        tools(new SvgTool());
    }

    @Override
    public String getId() {
        return "svg";
    }

    @Override
    public String getDescription() {
        return "SVG 工具集：在会话侧边插槽中预览/编辑/下载 SVG，并将 SVG 代码或 SVG 文件渲染保存为图片";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getKeyword() {
        return "svg,矢量图,矢量,绘图,图标";
    }

    @Override
    public String getIcon() {
        return "svg.svg";
    }
}
