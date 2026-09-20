package com.agent.hopaw.util;

import com.agent.hopaw.infra.model.dto.PluginDescriptor;
import com.agent.hopaw.infra.model.dto.ToolSetInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具集视图分组工具：把扁平的 {@link ToolSetInfo} 列表整理成「插件 → 工具集」两级结构。
 *
 * <p>插件管理为一级、工具集为二级，页面（tools.html、智能体编辑表单）需要按插件分组渲染，
 * 故在此统一分组，避免各控制器重复拼装。</p>
 */
public final class ToolSetViewUtil {

    private ToolSetViewUtil() {
    }

    /**
     * 按 pluginId 分组（仅插件来源的工具集），保留入参顺序。
     *
     * <p>每个插件都会预置一个（可能为空的）列表，模板里 {@code pluginToolSets.get(pluginId)}
     * 永远非 null，纯前端插件也能正常渲染「该插件未提供工具集」。</p>
     *
     * @param toolSets 当前可用工具集（已禁用插件的工具集已被过滤）
     * @param plugins  插件列表，用于保证分组 key 齐全
     * @return pluginId → 该插件下的工具集列表
     */
    public static Map<String, List<ToolSetInfo>> byPlugin(List<ToolSetInfo> toolSets, List<PluginDescriptor> plugins) {
        Map<String, List<ToolSetInfo>> grouped = new LinkedHashMap<>();
        if (plugins != null) {
            for (PluginDescriptor plugin : plugins) {
                grouped.put(plugin.getId(), new ArrayList<>());
            }
        }
        if (toolSets != null) {
            for (ToolSetInfo toolSet : toolSets) {
                String pluginId = toolSet.getPluginId();
                if (pluginId == null || pluginId.isEmpty()) {
                    continue;
                }
                grouped.computeIfAbsent(pluginId, key -> new ArrayList<>()).add(toolSet);
            }
        }
        return grouped;
    }

    /**
     * 内置工具集：没有插件父节点的工具（hopaw-biz 中 {@code @Component} 注册的内置工具）。
     */
    public static List<ToolSetInfo> builtin(List<ToolSetInfo> toolSets) {
        List<ToolSetInfo> builtins = new ArrayList<>();
        if (toolSets == null) {
            return builtins;
        }
        for (ToolSetInfo toolSet : toolSets) {
            if (toolSet.getPluginId() == null || toolSet.getPluginId().isEmpty()) {
                builtins.add(toolSet);
            }
        }
        return builtins;
    }
}
