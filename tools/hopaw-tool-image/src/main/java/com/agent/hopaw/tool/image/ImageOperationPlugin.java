package com.agent.hopaw.tool.image;

import com.agent.hopaw.infra.plugin.AbstractAgentPlugin;

/**
 * 图片操作插件主体。
 */
public class ImageOperationPlugin extends AbstractAgentPlugin {

    public ImageOperationPlugin() {
        // 插件作为工具工厂：分发本插件提供的工具实例
        tools(new ImageOperationTool());
    }

    @Override
    public String getId() {
        return "image";
    }

    @Override
    public String getDescription() {
        return "图片操作工具集，支持读取图片为base64、将SVG代码或SVG文件渲染保存为图片、缩放、压缩、旋转、裁剪、格式转换等图片操作";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getKeyword() {
        return "图片";
    }

    @Override
    public String getIcon() {
        return "image-operation-tool.svg";
    }
}
