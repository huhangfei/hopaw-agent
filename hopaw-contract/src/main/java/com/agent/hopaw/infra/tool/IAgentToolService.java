package com.agent.hopaw.infra.tool;

import com.agent.hopaw.infra.model.dto.PluginInstallResult;
import com.agent.hopaw.infra.model.dto.PluginUpdateInfo;
import com.agent.hopaw.infra.model.dto.ToolSetInfo;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public interface IAgentToolService {
    List<AgentTool> getAgentTools();

    List<ToolSetInfo> getToolSets();

    Map<String,String> getToolNameAndDescriptionMap();

    boolean unloadPlugin(String jarFileName);

    PluginInstallResult installOrUpgradePlugin(PluginUpdateInfo updateInfo);

    PluginInstallResult installOrUpgradePlugin(PluginUpdateInfo updateInfo,
                                               Consumer<String> stageCallback,
                                               Consumer<Integer> downloadProgressCallback);

    byte[] exportPlugin(String toolName, String toolVersion);

    PluginInstallResult installPluginFromBytes(byte[] zipBytes) throws Exception;

    /**
     * 直接从本地 .jar 文件安装插件（无需打包成 zip）。
     * 插件元数据（工具名、版本）会从 JAR 内部扫描得到。
     *
     * @param jarPath 本地 jar 文件路径
     * @return 安装结果
     */
    PluginInstallResult installPluginFromJarFile(Path jarPath) throws Exception;
}
