package com.agent.hopaw.infra.tool;

import com.agent.hopaw.infra.model.dto.ToolConfigItem;

import java.util.List;

/**
 * 工具接口：一个实现类 = 一个工具集（ToolSet）。
 *
 * <p>本接口只描述「工具」本身：名称、描述、图标、关键字、工具级配置与 {@code @Tool} 方法。
 * 插件身份相关内容（版本、作者、来源、插件描述、插件级配置、通用 invoke 通道、
 * 插件生命周期）全部归 {@link com.agent.hopaw.infra.plugin.AgentPlugin}，本接口不再承载。</p>
 *
 * <p>内置工具（Spring Bean 形式注册，非插件）与插件工具都实现本接口。
 * 工具级钩子 {@link #asyncInit()} / {@link #destroy()} 保留，供内置工具（无插件父节点）
 * 与需要独立资源的工具使用。插件工具由插件创建，框架调用顺序为：
 * 插件 asyncInit → 工具 asyncInit；工具 destroy → 插件 destroy。</p>
 *
 * <p>配置键归属：插件工具的配置挂在**所属插件根下**（{@code plugin.<pluginId>.tool.<工具集名>.<key>}），
 * 内置工具无插件宿主（{@code tool.<工具集名>.<key>}）。插件工具应继承 {@link AbstractAgentTool}
 * 以接收框架注入的 pluginId。</p>
 */
public interface AgentTool {
    public static final String TOOL_SEARCH_TOOL_NAME = "tool_search_tool";
    public static final String TOOL_SEARCH_TOOL_DESCRIPTION = "查询工具箱";
    public static final String DEFAULT_ICON="agent-tool.svg";

    /** 插件级配置键根：{@code plugin.<pluginId>.<key>} */
    String PLUGIN_CONFIG_ROOT = "plugin.";
    /** 工具级配置键段：内置工具为 {@code tool.<工具集名>.<key>}，插件工具挂在插件根下 {@code plugin.<pluginId>.tool.<工具集名>.<key>} */
    String TOOL_CONFIG_ROOT = "tool.";

    String getName();
    String getDescription();

    /**
     * 所属插件标识（pluginId）。内置工具无插件宿主，返回 {@code null}。
     *
     * <p>由框架在加载插件工具时注入（见 {@link #setPluginId(String)}），工具自身不要设置。</p>
     */
    default String getPluginId() {
        return null;
    }

    /**
     * 框架注入所属插件标识（可选钩子，仅插件工具会收到）。
     *
     * <p>调用时机在工具 {@link #asyncInit()} <b>之前</b>，保证 {@code asyncInit} 中读配置时
     * {@link #getConfigPrefix()} 已是新前缀。默认实现为空操作——直接实现本接口的工具应改为
     * 继承 {@link AbstractAgentTool} 以获得存储。</p>
     */
    default void setPluginId(String pluginId) {
        // 默认无可存之处：继承 AbstractAgentTool 才能接收注入
    }

    /**
     * 插件级配置键前缀：{@code plugin.<pluginId>.}。
     * 同时是插件卸载时"一次扫清"该插件全部配置（含其工具级配置）的清理前缀。
     */
    static String pluginConfigPrefix(String pluginId) {
        return PLUGIN_CONFIG_ROOT + pluginId + ".";
    }

    /**
     * 工具级配置键前缀：插件工具 {@code plugin.<pluginId>.tool.<工具集名>.}；内置工具 {@code tool.<工具集名>.}。
     *
     * @param pluginId    所属插件标识，可为空（内置工具）
     * @param toolSetName 工具集名
     */
    static String toolConfigPrefix(String pluginId, String toolSetName) {
        if (pluginId == null || pluginId.isBlank()) {
            return TOOL_CONFIG_ROOT + toolSetName + ".";
        }
        return pluginConfigPrefix(pluginId) + TOOL_CONFIG_ROOT + toolSetName + ".";
    }

    /**
     * 工具集图标：jar 内 static/icons/tools/ 下的文件名，或 svg 代码片段。
     */
    default String getIcon() {
        return DEFAULT_ICON;
    }

    /**
     * 声明关键字，便于匹配
     * @return
     */
    default String getKeyword(){
        return "";
    }

    /**
     * 异步初始化（可选钩子）。
     * 插件工具无需在此处理插件级共享资源，共享资源应由所属插件构建后注入。
     */
    default void asyncInit(){ return;}

    /**
     * 工具释放时清理自身持有的资源（可选钩子）。
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
     * 获取工具级配置键前缀（框架统一计算，工具只读不覆盖）。
     *
     * <p>插件工具挂在所属插件根下，即 {@code plugin.<pluginId>.tool.<工具集名>.}，
     * 与插件级配置 {@code plugin.<pluginId>.<key>} 同根不同段；内置工具无插件宿主，为
     * {@code tool.<工具集名>.}。</p>
     */
    default String getConfigPrefix() {
        return toolConfigPrefix(getPluginId(), getName());
    }

    /**
     * 配置变更通知
     * 当工具配置保存后会调用此方法，方便工具重建内部对象
     */
    default void onConfigChanged() {
        return;
    }
}
