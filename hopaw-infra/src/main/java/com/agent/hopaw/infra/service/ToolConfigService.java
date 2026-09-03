package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.model.dto.ToolConfigItem;
import com.agent.hopaw.infra.model.dto.ToolMapConfigItem;
import com.agent.hopaw.infra.model.dto.ValidationResult;
import com.agent.hopaw.infra.model.entity.SysConfig;
import com.agent.hopaw.infra.tool.AgentTool;
import com.agent.hopaw.infra.tool.AgentToolService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
public class ToolConfigService {

    private static final ObjectMapper objectMapper = new ObjectMapper();

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
        // MAP 结构：itemKey → (mapKey → (子配置key → 值))，保持 mapKey 存储顺序
        Map<String, Map<String, Map<String, String>>> mapValues = new HashMap<>();
        String prefix = tool.getConfigPrefix();
        for (ToolConfigItem item : configItems) {
            String key = prefix + item.getKey();
            if (item instanceof ToolMapConfigItem mapItem) {
                mapValues.put(item.getKey(), loadMapValues(key, mapItem));
            } else {
                SysConfig config = sysConfigService.getByKey(key);
                String value = config != null ? config.getConfigValue() : item.getDefaultValue();
                values.put(item.getKey(), value);
            }
        }
        result.put("values", values);
        result.put("mapValues", mapValues);

        return result;
    }

    /**
     * 加载 MAP 结构配置。
     * <p>当前格式：主体键的值为 JSON 对象 {"mapKey":{子配置key:值,...},...}，整体一条记录，对象顺序即 mapKey 顺序。</p>
     * <p>兼容旧散键格式（主体键为逗号分隔 mapKey 列表 + 主体键:mapKey:子配置key 散键），保存时自动迁移为 JSON。</p>
     */
    private Map<String, Map<String, String>> loadMapValues(String listKey, ToolMapConfigItem mapItem) {
        Map<String, Map<String, String>> groups = new LinkedHashMap<>();
        SysConfig listConfig = sysConfigService.getByKey(listKey);
        String listValue = listConfig != null ? listConfig.getConfigValue() : null;
        if (listValue == null || listValue.isBlank()) {
            return groups;
        }

        // 当前格式：JSON 整体存储
        String trimmed = listValue.trim();
        if (trimmed.startsWith("{")) {
            try {
                Map<String, Map<String, String>> parsed = objectMapper.readValue(trimmed,
                        new TypeReference<LinkedHashMap<String, Map<String, String>>>() {});
                parsed.forEach((mapKey, values) -> {
                    Map<String, String> groupValues = new HashMap<>();
                    for (ToolConfigItem valueItem : mapItem.getValues()) {
                        String v = values != null ? values.get(valueItem.getKey()) : null;
                        groupValues.put(valueItem.getKey(), v != null ? v : valueItem.getDefaultValue());
                    }
                    groups.put(mapKey, groupValues);
                });
            } catch (Exception e) {
                // JSON 解析失败视为无配置
            }
            return groups;
        }

        // 旧散键格式：主体键为逗号分隔的 mapKey 列表，散键为 主体键:mapKey:子配置key
        for (String mapKey : listValue.split(",")) {
            mapKey = mapKey.trim();
            if (mapKey.isEmpty()) {
                continue;
            }
            Map<String, String> groupValues = new HashMap<>();
            for (ToolConfigItem valueItem : mapItem.getValues()) {
                SysConfig vc = sysConfigService.getByKey(listKey + ":" + mapKey + ":" + valueItem.getKey());
                groupValues.put(valueItem.getKey(), vc != null ? vc.getConfigValue() : valueItem.getDefaultValue());
            }
            groups.put(mapKey, groupValues);
        }
        return groups;
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

            if (item instanceof ToolMapConfigItem mapItem) {
                saveMapConfig(tool, item, mapItem, prefix, params);
            } else if (item.getType() == ToolConfigItem.ConfigType.CHECKBOX) {
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

    /**
     * 保存 MAP 结构配置。
     * <p>表单参数约定：mapKey_{itemKey}_{组序号} 为组名，config_{itemKey}_{组序号}_{子配置key} 为组内子配置值。</p>
     * <p>存储：主体键的值为 JSON 对象（整体一条记录，对象顺序即 mapKey 顺序）；保存后清理旧散键格式的遗留数据。</p>
     */
    private void saveMapConfig(AgentTool tool, ToolConfigItem item, ToolMapConfigItem mapItem,
                               String prefix, Map<String, String> params) {
        String listKey = prefix + item.getKey();
        String mapKeyParamPrefix = "mapKey_" + item.getKey() + "_";
        String description = tool.getName() + " - " + item.getLabel();

        // 1. 收集各组 mapKey（按组序号排序）
        Map<Integer, String> idxToMapKey = new TreeMap<>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String k = entry.getKey();
            if (k.startsWith(mapKeyParamPrefix)) {
                try {
                    int idx = Integer.parseInt(k.substring(mapKeyParamPrefix.length()));
                    idxToMapKey.put(idx, entry.getValue() == null ? "" : entry.getValue().trim());
                } catch (NumberFormatException ignored) {
                    // 非组序号后缀的参数，忽略
                }
            }
        }

        // 2. 校验 mapKey：非空、不含英文逗号/冒号、不重复
        List<String> mapKeys = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String mapKey : idxToMapKey.values()) {
            if (mapKey.isEmpty()) {
                throw new IllegalArgumentException(description + "：组名称不能为空");
            }
            if (mapKey.contains(",") || mapKey.contains(":")) {
                throw new IllegalArgumentException(description + "：组名称不能包含英文逗号或冒号：" + mapKey);
            }
            if (!seen.add(mapKey)) {
                throw new IllegalArgumentException(description + "：组名称重复：" + mapKey);
            }
            mapKeys.add(mapKey);
        }

        // 3. 主体校验（required 等作用于 mapKey 列表整体，仅校验不存储）
        String joinedList = String.join(",", mapKeys);
        ValidationResult listResult = item.validate(joinedList);
        if (!listResult.isValid()) {
            throw new IllegalArgumentException(listResult.getMessage());
        }

        // 4. 收集并校验各组子配置值，组装为有序 map（单条 JSON 存储）
        Map<String, Map<String, String>> groupMap = new LinkedHashMap<>();
        for (Map.Entry<Integer, String> entry : idxToMapKey.entrySet()) {
            int idx = entry.getKey();
            String mapKey = entry.getValue();
            Map<String, String> valueMap = new LinkedHashMap<>();
            for (ToolConfigItem valueItem : mapItem.getValues()) {
                String value = collectMapValue(item, valueItem, idx, params);
                ValidationResult result = valueItem.validate(value);
                if (!result.isValid()) {
                    throw new IllegalArgumentException(description + " [" + mapKey + "] " + result.getMessage());
                }
                valueMap.put(valueItem.getKey(), value);
            }
            groupMap.put(mapKey, valueMap);
        }

        // 5. 整体序列化为 JSON，主体键单条存储（组内任一子配置敏感则整条加密）
        String json;
        try {
            json = objectMapper.writeValueAsString(groupMap);
        } catch (Exception e) {
            throw new IllegalArgumentException(description + "：配置序列化失败：" + e.getMessage());
        }
        boolean mapEncrypt = mapItem.getValues().stream().anyMatch(ToolConfigItem::isSensitive);
        saveOrUpdateConfig(listKey, json, description, mapEncrypt);

        // 6. 清理旧散键格式的遗留数据（主体键:mapKey:子配置key）
        for (SysConfig config : sysConfigService.getAll()) {
            if (config.getConfigKey().startsWith(listKey + ":")) {
                sysConfigService.deleteByKey(config.getConfigKey());
            }
        }
    }

    /**
     * 收集 MAP 结构中某个子配置项在某组的值。
     * 复选/多选/多行类型按基础类型的表单命名规则收集后逗号拼接，其余类型直接取值。
     */
    private String collectMapValue(ToolConfigItem item, ToolConfigItem valueItem, int groupIdx,
                                   Map<String, String> params) {
        String valueKeyPrefix = "config_" + item.getKey() + "_" + groupIdx + "_" + valueItem.getKey();
        if (valueItem.getType() == ToolConfigItem.ConfigType.CHECKBOX
                || valueItem.getType() == ToolConfigItem.ConfigType.TEXT_MULTI
                || valueItem.getType() == ToolConfigItem.ConfigType.TEXT_PASSWORD_MULTI) {
            List<String> values = new ArrayList<>();
            for (Map.Entry<String, String> entry : params.entrySet()) {
                if (entry.getKey().startsWith(valueKeyPrefix + "_")) {
                    String v = entry.getValue();
                    if (v != null && !v.trim().isEmpty()) {
                        values.add(v);
                    }
                }
            }
            return String.join(",", values);
        }
        if (valueItem.getType() == ToolConfigItem.ConfigType.SELECT_MULTI) {
            List<String> values = new ArrayList<>();
            for (Map.Entry<String, String> entry : params.entrySet()) {
                if (entry.getKey().startsWith(valueKeyPrefix + "[")) {
                    values.add(entry.getValue());
                }
            }
            return String.join(",", values);
        }
        String value = params.get(valueKeyPrefix);
        return value != null ? value : "";
    }

    private void validateAndSave(ToolConfigItem item, String value, String key, String description) {
        ValidationResult result = item.validate(value);
        if (!result.isValid()) {
            throw new IllegalArgumentException(result.getMessage());
        }
        saveOrUpdateConfig(key, value, description, item.isSensitive());
    }

    private void saveOrUpdateConfig(String key, String value, String description, boolean encrypt) {
        SysConfig config = sysConfigService.getByKey(key);
        if (config == null) {
            config = new SysConfig(key, value, description);
            sysConfigService.insert(config, encrypt);
        } else {
            config.setConfigValue(value);
            config.setDescription(description);
            sysConfigService.update(config, encrypt);
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
