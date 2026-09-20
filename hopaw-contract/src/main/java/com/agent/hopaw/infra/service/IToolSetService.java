package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.model.dto.ToolSetInfo;
import com.agent.hopaw.infra.tool.AgentTool;

import java.util.List;
import java.util.Map;

/**
 * 工具集管理（二级）服务：以 {@link AgentTool} 为主体，负责工具集扫描与组装。
 *
 * <p>与插件管理（一级，{@link IAgentPluginService}）的分工：</p>
 * <ul>
 *   <li>一级管「装了什么插件」——安装/升级/卸载/启停/插件级配置；</li>
 *   <li>二级管「插件提供了哪些工具集」——工具集元数据扫描、按智能体组装、工具级配置。</li>
 * </ul>
 *
 * <p>智能体使用侧仍以工具集（{@link ToolSetInfo}）为主体：{@code Agent.tools} 存工具集名，
 * 执行链路消费 {@code List<ToolSetInfo>}。禁用插件的工具集不进入结果集，其绑定成为悬空绑定，
 * 由展示层提示。</p>
 */
public interface IToolSetService {

    /**
     * 全部可用工具集：内置工具（非插件）+ 已启用插件提供的工具集。
     * 顺序稳定：内置工具按名称排序在前，插件工具按 pluginId 排序分组在后。
     */
    List<ToolSetInfo> getToolSets();

    /**
     * 指定插件的工具集；pluginId 为 null 时返回内置工具（无插件父节点）。
     */
    List<ToolSetInfo> getToolSetsByPlugin(String pluginId);

    /**
     * 按工具集名查找工具集，不存在返回 null。
     */
    ToolSetInfo getToolSet(String toolSetName);

    /**
     * 全部可用工具实例（内置 + 已启用插件提供的）。
     */
    List<AgentTool> getAgentTools();

    /**
     * 按工具集名查找工具实例，不存在返回 null。
     */
    AgentTool getAgentTool(String toolSetName);

    /**
     * {@code @Tool} 方法名 → 描述 的映射（供聊天记录展示工具中文描述）。
     */
    Map<String, String> getToolNameAndDescriptionMap();
}
