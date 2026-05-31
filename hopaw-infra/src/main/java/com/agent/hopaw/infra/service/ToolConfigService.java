package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.model.dto.ToolConfigItem;
import com.agent.hopaw.infra.model.dto.ValidationResult;
import com.agent.hopaw.infra.model.entity.SysConfig;
import com.agent.hopaw.infra.tool.AgentTool;
import com.agent.hopaw.infra.tool.AgentToolService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ToolConfigService {

    private final AgentToolService agentToolService;
    private final ISysConfigService sysConfigService;

    public ToolConfigService(AgentToolService agentToolService, ISysConfigService sysConfigService) {
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
                List<String> values = new ArrayList<>();
                for (Map.Entry<String, String> entry : params.entrySet()) {
                    if (entry.getKey().startsWith("config_" + item.getKey() + "_")) {
                        values.add(entry.getValue());
                    }
                }
                String joinedValue = String.join(",", values);
                validateAndSave(item, joinedValue, key, tool.getName() + " - " + item.getLabel());
            } else if (item.getType() == ToolConfigItem.ConfigType.SELECT_MULTI) {
                List<String> values = new ArrayList<>();
                for (Map.Entry<String, String> entry : params.entrySet()) {
                    if (entry.getKey().startsWith("config_" + item.getKey() + "[")) {
                        values.add(entry.getValue());
                    }
                }
                String joinedValue = String.join(",", values);
                validateAndSave(item, joinedValue, key, tool.getName() + " - " + item.getLabel());
            } else if (item.getType() == ToolConfigItem.ConfigType.TEXT_MULTI || 
                      item.getType() == ToolConfigItem.ConfigType.TEXT_PASSWORD_MULTI) {
                List<String> values = params.entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith("config_" + item.getKey() + "_"))
                    .map(Map.Entry::getValue)
                    .filter(v -> v != null && !v.trim().isEmpty())
                    .collect(Collectors.toList());
                
                String joinedValue = String.join(",", values);
                validateAndSave(item, joinedValue, key, tool.getName() + " - " + item.getLabel());
            } else {
                String value = params.get("config_" + item.getKey());
                if (value != null) {
                    validateAndSave(item, value, key, tool.getName() + " - " + item.getLabel());
                }
            }
        }
    }

    private void validateAndSave(ToolConfigItem item, String value, String key, String description) {
        ValidationResult result = item.validate(value);
        if (!result.isValid()) {
            throw new IllegalArgumentException(result.getMessage());
        }
        saveOrUpdateConfig(key, value, description);
    }

    private void saveOrUpdateConfig(String key, String value, String description) {
        SysConfig config = sysConfigService.getByKey(key);
        if (config == null) {
            config = new SysConfig(key, value, description);
            sysConfigService.insert(config);
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
