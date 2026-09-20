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
 */
public interface AgentTool {
    public static final String TOOL_SEARCH_TOOL_NAME = "tool_search_tool";
    public static final String TOOL_SEARCH_TOOL_DESCRIPTION = "查询工具箱";
    public static final String DEFAULT_ICON="agent-tool.svg";

    String getName();
    String getDescription();

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
     * 插件级配置前缀为 {@code plugin.<pluginId>.}，见 AgentPlugin。
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
