package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.model.dto.PluginDescriptor;
import com.agent.hopaw.infra.model.dto.PluginInstallResult;
import com.agent.hopaw.infra.model.dto.PluginUpdateInfo;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 插件管理（一级）服务：以 {@link com.agent.hopaw.infra.plugin.AgentPlugin} 为主体。
 *
 * <p>职责：插件安装/升级/卸载/导出、启用与禁用、插件级配置信息、插件通用 invoke 入口。
 * 插件下的工具集（{@link IToolSetService}）属于二级，不在此接口。</p>
 *
 * <p>所有以插件为单位的操作均以 <b>pluginId</b> 为标识；JAR 文件名只是物理属性。</p>
 */
public interface IAgentPluginService {

    // ==================== 查询 ====================

    /**
     * 全部已安装插件（含禁用状态，按 pluginId 排序）。
     */
    List<PluginDescriptor> getPlugins();

    /**
     * 按 pluginId 查询插件，不存在返回 null。
     */
    PluginDescriptor getPlugin(String pluginId);

    // ==================== 启停 ====================

    /**
     * 查询插件启用状态；未记录视为启用。
     */
    boolean isEnabled(String pluginId);

    /**
     * 启用/禁用插件：禁用后该插件的工具集不进 ToolSets、前端资产不注入；
     * 智能体已绑定的工具集成为悬空绑定，由展示层提示。
     */
    void setEnabled(String pluginId, boolean enabled);

    // ==================== 安装 / 升级 / 卸载 / 导出 ====================

    PluginInstallResult installOrUpgradePlugin(PluginUpdateInfo updateInfo);

    PluginInstallResult installOrUpgradePlugin(PluginUpdateInfo updateInfo,
                                               Consumer<String> stageCallback,
                                               Consumer<Integer> downloadProgressCallback);

    PluginInstallResult installPluginFromBytes(byte[] zipBytes) throws Exception;

    /**
     * 从本地 .jar 文件安装插件（无需打包成 zip），插件元数据由 JAR 内 AgentPlugin 提供。
     */
    PluginInstallResult installPluginFromJarFile(Path jarPath) throws Exception;

    /**
     * 从本地 .jar 文件安装插件，并指定落库的目标文件名。
     */
    PluginInstallResult installPluginFromJarFile(Path jarPath, String jarFileName) throws Exception;

    /**
     * 按 pluginId 卸载插件。
     *
     * @param cleanConfig 是否同时清理配置：插件级 {@code plugin.<id>.*} + 该插件下<b>所有</b>工具集的
     *                    {@code tool.<工具集名>.*}（含 MAP 散键）
     * @return true 表示确实卸载了已注册插件
     */
    boolean unloadPlugin(String pluginId, boolean cleanConfig);

    /**
     * 导出整插件包（zip：清单 json + 插件 JAR），以 pluginId 为维度。
     *
     * @return zip 字节；插件不存在返回 null
     */
    byte[] exportPlugin(String pluginId);

    // ==================== 配置信息 / invoke ====================

    /**
     * 插件配置信息（供前端确认卸载是否需要清理配置）：{@code hasConfig} + {@code configKeys}。
     */
    Map<String, Object> getPluginConfigInfo(String pluginId);

    /**
     * 插件通用调用入口（POST /api/plugins/{pluginId}/invoke 的服务侧契约）。
     *
     * @param pluginId 插件标识
     * @param toolRef  多工具插件的路由键（工具集名），可为空
     * @param params   调用参数
     */
    Map<String, Object> invoke(String pluginId, String toolRef, Map<String, Object> params);
}
