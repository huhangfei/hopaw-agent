package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.model.dto.ToolConfigItem;
import com.agent.hopaw.infra.plugin.AgentPlugin;
import com.agent.hopaw.infra.plugin.PluginRegistry;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 插件级配置服务：配置键前缀 {@code plugin.<pluginId>.}，与工具级配置 {@code tool.<工具集名>.} 并列。
 *
 * <p>插件级配置用于多工具共享的公共配置（连接信息、凭证等），与工具级配置共用
 * {@link ConfigItemStore} 的存取/校验/序列化规则。</p>
 */
@Service
public class PluginConfigService implements IPluginConfigService {

    /** 插件级配置键前缀 */
    public static final String PREFIX_ROOT = "plugin.";

    private final PluginRegistry pluginRegistry;
    private final ConfigItemStore configItemStore;
    private final ISysConfigService sysConfigService;

    public PluginConfigService(PluginRegistry pluginRegistry,
                               ConfigItemStore configItemStore,
                               ISysConfigService sysConfigService) {
        this.pluginRegistry = pluginRegistry;
        this.configItemStore = configItemStore;
        this.sysConfigService = sysConfigService;
    }

    /**
     * 插件级配置键前缀：{@code plugin.<pluginId>.}
     */
    public static String prefix(String pluginId) {
        return PREFIX_ROOT + pluginId + ".";
    }

    /**
     * 读取插件配置页数据（元信息 + 配置项定义 + 当前值）。
     */
    public Map<String, Object> getPluginConfig(String pluginId) {
        AgentPlugin plugin = requirePlugin(pluginId);
        List<ToolConfigItem> configItems = plugin.getConfigItems();

        Map<String, Object> result = new HashMap<>();
        result.put("pluginId", plugin.getId());
        result.put("pluginName", plugin.getName());
        result.put("pluginDescription", plugin.getDescription());
        result.put("configItems", configItems);
        ConfigItemStore.Loaded loaded = configItemStore.load(prefix(pluginId), configItems);
        result.put("values", loaded.values());
        result.put("mapValues", loaded.mapValues());
        return result;
    }

    /**
     * 保存插件配置。
     */
    public void savePluginConfig(String pluginId, Map<String, String> params) {
        AgentPlugin plugin = requirePlugin(pluginId);
        configItemStore.save(prefix(pluginId), pluginId, plugin.getConfigItems(), params);
    }

    @Override
    public String get(String pluginId, String key, String defaultValue) {
        return sysConfigService.getValueByKey(prefix(pluginId) + key, defaultValue);
    }

    @Override
    public void put(String pluginId, String key, String value) {
        String configKey = prefix(pluginId) + key;
        com.agent.hopaw.infra.model.entity.SysConfig config = sysConfigService.getByKey(configKey);
        if (config == null) {
            config = new com.agent.hopaw.infra.model.entity.SysConfig(configKey, value, pluginId + " - " + key);
            sysConfigService.insert(config, false);
        } else {
            config.setConfigValue(value);
            sysConfigService.update(config, false);
        }
    }

    @Override
    public String getValue(String configKey, String defaultValue) {
        return sysConfigService.getValueByKey(configKey, defaultValue);
    }

    @Override
    public void delete(String configKey) {
        sysConfigService.deleteByKey(configKey);
    }

    private AgentPlugin requirePlugin(String pluginId) {
        if (pluginId == null || pluginId.isEmpty()) {
            throw new IllegalArgumentException("插件标识不能为空");
        }
        PluginRegistry.PluginEntry entry = pluginRegistry.getPlugin(pluginId);
        if (entry == null) {
            throw new IllegalArgumentException("插件不存在：" + pluginId);
        }
        return entry.getPlugin();
    }
}
