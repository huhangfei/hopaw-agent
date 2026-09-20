package com.agent.hopaw.tool.file;

import com.agent.hopaw.infra.plugin.AbstractAgentPlugin;

/**
 * 文件操作插件主体。
 */
public class FileOperationPlugin extends AbstractAgentPlugin {

    public FileOperationPlugin() {
        // 插件作为工具工厂：分发本插件提供的工具实例
        tools(new FileOperationTool());
    }

    @Override
    public String getId() {
        return "file";
    }

    @Override
    public String getDescription() {
        return "文件操作工具集，支持读取、写入、删除、移动、复制、高性能搜索等文件操作";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getKeyword() {
        return "文件";
    }

    @Override
    public String getIcon() {
        return "file-operation-tool.svg";
    }
}
