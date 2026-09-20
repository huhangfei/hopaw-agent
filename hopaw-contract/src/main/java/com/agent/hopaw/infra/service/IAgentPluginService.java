package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.model.dto.PluginDescriptor;
import com.agent.hopaw.infra.plugin.AgentPlugin;

import java.util.List;
import java.util.Map;

/**
 * 插件管理（一级）服务：以 {@link AgentPlugin} 为主体，负责插件级查询与启停。
 *
 * <p>安装/升级/卸载/导出等文件操作属于基础设施细节，由 infra 层的
 * PluginManagerService 提供完整实现（含配置清理、冲突检测），本接口仅收敛
 * 跨模块消费所需的稳定契约。</p>
 */
public interface IAgentPluginService {

    /**
     * 全部已安装插件（含禁用状态，按 id 排序）。
     */
    List<PluginDescriptor> getPlugins();

    /**
     * 按 pluginId 查询插件，不存在返回 null。
     */
    PluginDescriptor getPlugin(String pluginId);

    /**
     * 查询插件启用状态；未记录视为启用。
     */
    boolean isEnabled(String pluginId);

    /**
     * 启用/禁用插件：禁用后该插件的工具集不进 ToolSets、前端资产不注入；
     * 智能体已绑定的工具集成为悬空绑定，由展示层提示。
     */
    void setEnabled(String pluginId, boolean enabled);

    /**
     * 插件通用调用入口（POST /api/plugins/{pluginId}/invoke 的服务侧契约）。
     *
     * @param pluginId 插件标识
     * @param toolRef  多工具插件的路由键（工具集名），可为空
     * @param params   调用参数
     */
    Map<String, Object> invoke(String pluginId, String toolRef, Map<String, Object> params);
}
