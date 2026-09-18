package com.agent.hopaw.infra.tool;

import com.agent.hopaw.infra.model.dto.ToolConfigItem;

import java.util.List;
import java.util.Map;

public interface AgentTool {
    public static final String TOOL_SEARCH_TOOL_NAME = "tool_search_tool";
    public static final String TOOL_SEARCH_TOOL_DESCRIPTION = "查询工具箱";
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

    /**
     * 插件公共调用入口：客户端可通过统一 API（{@code POST /api/plugins/{toolName}/invoke}）
     * 直接调用插件，向前端插件发送数据或从插件获取数据。
     *
     * <p>默认实现返回「不支持」，现有插件无需改动即兼容；需要此能力的插件按需覆盖。</p>
     *
     * @param params 客户端传入的参数（JSON 对象反序列化为 Map，可能为空）
     * @return 返回给客户端的结果（序列化为 JSON 对象返回）
     */
    default Map<String, Object> invoke(Map<String, Object> params) {
        return Map.of(
                "success", false,
                "message", "工具 " + getName() + " 不支持 invoke 调用");
    }
}
