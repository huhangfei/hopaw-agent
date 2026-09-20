package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.model.dto.ToolConfigItem;
import com.agent.hopaw.infra.tool.AgentTool;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具级配置服务：配置键前缀 {@code tool.<工具集名>.}。
 *
 * <p>具体存取/校验逻辑委托给 {@link ConfigItemStore}，本类只负责以工具集为主体组织数据。</p>
 */
@Service
public class ToolConfigService {

    private final IToolSetService toolSetService;
    private final ConfigItemStore configItemStore;

    public ToolConfigService(IToolSetService toolSetService, ConfigItemStore configItemStore) {
        this.toolSetService = toolSetService;
        this.configItemStore = configItemStore;
    }

    public Map<String, Object> getToolConfig(String toolName) {
        AgentTool tool = findToolByName(toolName);
        if (tool == null) {
            throw new IllegalArgumentException("工具不存在：" + toolName);
        }

        List<ToolConfigItem> configItems = tool.getConfigItems();
        Map<String, Object> result = new HashMap<>();
        result.put("toolName", tool.getName());
        result.put("toolDescription", tool.getDescription());
        result.put("configItems", configItems);
        ConfigItemStore.Loaded loaded = configItemStore.load(tool.getConfigPrefix(), configItems);
        result.put("values", loaded.values());
        result.put("mapValues", loaded.mapValues());
        return result;
    }

    public void saveToolConfig(String toolName, Map<String, String> params) {
        AgentTool tool = findToolByName(toolName);
        if (tool == null) {
            throw new IllegalArgumentException("工具不存在：" + toolName);
        }
        configItemStore.save(tool.getConfigPrefix(), tool.getName(), tool.getConfigItems(), params);
    }

    /**
     * 清理某工具集的全部配置键（含 MAP 散键），供插件卸载时按工具集逐个调用。
     */
    public void deleteToolConfig(String toolName) {
        AgentTool tool = findToolByName(toolName);
        if (tool == null) {
            return;
        }
        configItemStore.deleteAll(tool.getConfigPrefix(), tool.getConfigItems());
    }

    public List<String> getToolsWithConfig() {
        List<String> result = new ArrayList<>();
        for (AgentTool tool : toolSetService.getAgentTools()) {
            if (!tool.getConfigItems().isEmpty()) {
                result.add(tool.getName());
            }
        }
        return result;
    }

    private AgentTool findToolByName(String toolName) {
        return toolSetService.getAgentTool(toolName);
    }
}
