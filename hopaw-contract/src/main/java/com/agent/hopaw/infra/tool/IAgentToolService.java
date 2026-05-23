package com.agent.hopaw.infra.tool;

import com.agent.hopaw.infra.model.dto.PluginInstallResult;
import com.agent.hopaw.infra.model.dto.PluginUpdateInfo;
import com.agent.hopaw.infra.model.dto.ToolSetInfo;

import java.util.List;
import java.util.function.Consumer;

public interface IAgentToolService {
    List<AgentTool> getAgentTools();

    List<ToolSetInfo> getToolSets();

    boolean unloadPlugin(String jarFileName);

    PluginInstallResult installOrUpgradePlugin(PluginUpdateInfo updateInfo);

    PluginInstallResult installOrUpgradePlugin(PluginUpdateInfo updateInfo,
                                               Consumer<String> stageCallback,
                                               Consumer<Integer> downloadProgressCallback);

    byte[] exportPlugin(String jarFileName);
}
