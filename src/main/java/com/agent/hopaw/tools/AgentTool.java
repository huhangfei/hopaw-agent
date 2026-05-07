package com.agent.hopaw.tools;

public interface AgentTool {
    String getName();
    String getDescription();
    default String getIcon() {
        return "agent-tool";
    }
}
