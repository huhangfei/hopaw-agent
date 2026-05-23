package com.agent.hopaw.infra.tool;

import com.agent.hopaw.infra.model.dto.ToolConfigItem;

import java.util.List;

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

    /**
     * 获取工具配置项定义
     * @return 配置项列表
     */
    default List<ToolConfigItem> getConfigItems() {
        return List.of();
    }

    /**
     * 获取配置键前缀
     * @return 配置键前缀
     */
    default String getConfigPrefix() {
        return "tool." + getName() + ".";
    }
    
    /**
     * 配置变更通知
     * 当工具配置保存后会调用此方法，方便工具重建内部对象
     */
    default void onConfigChanged() {
        return;
    }
}
