package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.model.dto.ToolConfigItem;
import com.agent.hopaw.infra.model.entity.SysConfig;
import com.agent.hopaw.infra.tool.AgentTool;
import com.agent.hopaw.infra.tool.AgentToolService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ToolConfigService {

    private final AgentToolService agentToolService;
    private final SysConfigService sysConfigService;

    public ToolConfigService(AgentToolService agentToolService, SysConfigService sysConfigService) {
        this.agentToolService = agentToolService;
        this.sysConfigService = sysConfigService;
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

        Map<String, String> values = new HashMap<>();
        String prefix = tool.getConfigPrefix();
        for (ToolConfigItem item : configItems) {
            String key = prefix + item.getKey();
            SysConfig config = sysConfigService.getByKey(key);
            String value = config != null ? config.getConfigValue() : item.getDefaultValue();
            values.put(item.getKey(), value);
        }
        result.put("values", values);

        return result;
    }

    public void saveToolConfig(String toolName, Map<String, String> params) {
        AgentTool tool = findToolByName(toolName);
        if (tool == null) {
            throw new IllegalArgumentException("工具不存在：" + toolName);
        }

        List<ToolConfigItem> configItems = tool.getConfigItems();
        String prefix = tool.getConfigPrefix();

        for (ToolConfigItem item : configItems) {
            String key = prefix + item.getKey();
            
            if (item.getType() == ToolConfigItem.ConfigType.CHECKBOX) {
                // 处理多选
                List<String> values = new ArrayList<>();
                for (Map.Entry<String, String> entry : params.entrySet()) {
                    if (entry.getKey().startsWith("config_" + item.getKey() + "_")) {
                        values.add(entry.getValue());
                    }
                }
                String joinedValue = String.join(",", values);
                saveOrUpdateConfig(key, joinedValue, tool.getName() + " - " + item.getLabel());
            } else {
                // 处理其他类型
                String value = params.get("config_" + item.getKey());
                if (value != null) {
                    saveOrUpdateConfig(key, value, tool.getName() + " - " + item.getLabel());
                }
            }
        }
    }

    private void saveOrUpdateConfig(String key, String value, String description) {
        SysConfig config = sysConfigService.getByKey(key);
        if (config == null) {
            config = new SysConfig(key, value, description);
            sysConfigService.save(config);
        } else {
            config.setConfigValue(value);
            sysConfigService.update(config);
        }
    }

    private AgentTool findToolByName(String toolName) {
        List<AgentTool> tools = agentToolService.getAgentTools();
        for (AgentTool tool : tools) {
            if (tool.getName().equals(toolName)) {
                return tool;
            }
        }
        return null;
    }

    public List<String> getToolsWithConfig() {
        List<String> result = new ArrayList<>();
        List<AgentTool> tools = agentToolService.getAgentTools();
        for (AgentTool tool : tools) {
            if (!tool.getConfigItems().isEmpty()) {
                result.add(tool.getName());
            }
        }
        return result;
    }
}
