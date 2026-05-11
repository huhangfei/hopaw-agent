package com.agent.hopaw.tools;

public interface AgentTool {
    String getName();
    String getDescription();
    default String getIcon() {
        return "agent-tool";
    }

    /**
     * 声明关键字，便于匹配
     * @return
     */
    default String getKeyword(){
        return "";
    }
}
