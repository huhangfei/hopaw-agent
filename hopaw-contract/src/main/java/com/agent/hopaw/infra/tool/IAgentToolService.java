package com.agent.hopaw.infra.tool;

import com.agent.hopaw.infra.model.dto.ToolSetInfo;

import java.util.List;

public interface IAgentToolService {
    List<AgentTool> getAgentTools();

    List<ToolSetInfo> getToolSets();

    boolean unloadPlugin(String jarFileName);
}
