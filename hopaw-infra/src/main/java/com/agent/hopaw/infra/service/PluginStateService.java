package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.mapper.PluginStateMapper;
import com.agent.hopaw.infra.model.entity.PluginState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 插件启用状态服务：持久化「插件是否启用」，并缓存于内存供高频读取。
 *
 * <p>未记录的插件视为启用；插件卸载时清除其状态记录。</p>
 */
@Service
public class PluginStateService {

    private static final Logger log = LoggerFactory.getLogger(PluginStateService.class);

    private final PluginStateMapper pluginStateMapper;

    /** pluginId -> enabled；null 表示尚未加载成功（表未就绪时每次重试） */
    private volatile Map<String, Boolean> enabledMap = null;

    public PluginStateService(PluginStateMapper pluginStateMapper) {
        this.pluginStateMapper = pluginStateMapper;
    }

    /**
     * 插件是否启用；未记录视为启用。
     */
    public boolean isEnabled(String pluginId) {
        if (pluginId == null) {
            return true;
        }
        Boolean enabled = enabledMap().get(pluginId);
        return enabled == null || enabled;
    }

    /**
     * 设置启用状态并持久化。
     */
    public void setEnabled(String pluginId, boolean enabled) {
        pluginStateMapper.upsert(new PluginState(pluginId, enabled ? 1 : 0));
        reload();
        log.info("Plugin [{}] enabled set to {}", pluginId, enabled);
    }

    /**
     * 清理插件状态（卸载时调用）。
     */
    public void remove(String pluginId) {
        pluginStateMapper.deleteByPluginId(pluginId);
        reload();
    }

    private Map<String, Boolean> enabledMap() {
        Map<String, Boolean> cached = enabledMap;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (enabledMap != null) {
                return enabledMap;
            }
            try {
                enabledMap = queryAll();
            } catch (Exception e) {
                // 启动早期表可能尚未创建：不缓存失败结果，下次调用重试
                log.warn("读取 plugin_state 失败（可能表尚未创建），本次按全部启用处理: {}", e.getMessage());
                return Map.of();
            }
            return enabledMap;
        }
    }

    private void reload() {
        synchronized (this) {
            try {
                enabledMap = queryAll();
            } catch (Exception e) {
                enabledMap = null;
                log.warn("刷新 plugin_state 缓存失败: {}", e.getMessage());
            }
        }
    }

    private Map<String, Boolean> queryAll() {
        Map<String, Boolean> result = new ConcurrentHashMap<>();
        for (PluginState state : pluginStateMapper.findAll()) {
            if (state.getPluginId() == null) {
                continue;
            }
            boolean enabled = state.getEnabled() == null || state.getEnabled() != 0;
            result.put(state.getPluginId(), enabled);
        }
        return new HashMap<>(result);
    }
}
