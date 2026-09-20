package com.agent.hopaw.tool.aliyun;

import com.agent.hopaw.infra.plugin.AbstractAgentPlugin;

/**
 * 阿里云文生图插件主体。
 */
public class AliyunImageGenPlugin extends AbstractAgentPlugin {

    public AliyunImageGenPlugin() {
        // 插件作为工具工厂：分发本插件提供的工具实例
        tools(new AliyunImageGenTool());
    }

    @Override
    public String getId() {
        return "aliyunImageGen";
    }

    @Override
    public String getDescription() {
        return "阿里云百炼文生图工具，基于通义万相 qwen-image 模型，根据文字描述生成图片";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getKeyword() {
        return "文生图,生成图片,画图,AI绘画,通义万相,阿里云,aliyun,qwen-image";
    }

    @Override
    public String getIcon() {
        return "aliyun-image-gen.svg";
    }
}
