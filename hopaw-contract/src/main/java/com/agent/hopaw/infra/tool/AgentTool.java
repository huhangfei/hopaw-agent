package com.agent.hopaw.infra.tool;

public interface AgentTool {
    public static final String DEFAULT_ICON="agent-tool.svg";
    String getName();
    String getDescription();

    default String getIcon() {
        return DEFAULT_ICON;
    }

    default String getVersion() {
        return "1.0.0";
    }

    default String getAuthor() {
        return "Agent Tool";
    }
    default String getUrl() {
        return "https://gitee.com/hgflydream/hopaw-agent";
    }
    /**
     * 声明关键字，便于匹配
     * @return
     */
    default String getKeyword(){
        return "";
    }

    /**
     * 异步初始化
     */
    default void asyncInit(){ return;}
    /**
     * 插件卸载时清理资源
     */
    default void destroy(){ return;}
}
