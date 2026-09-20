package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.mapper.ToolStateMapper;
import com.agent.hopaw.infra.model.entity.ToolState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具运行态状态服务：持久化「工具集 / 工具方法」的启用/禁用决策，并缓存于内存供高频读取。
 *
 * <p>与 {@link PluginStateService} 共同构成三级禁用：
 * 插件级（{@code plugin_state}）→ 工具集级 → 工具方法级（本服务）。未记录的工具集/方法视为启用。</p>
 *
 * <p>缓存键：工具集级为 {@code "<工具集名>"}，方法级为 {@code "<工具集名>\u0000<工具方法名>"}。</p>
 */
@Service
public class ToolStateService {

    private static final Logger log = LoggerFactory.getLogger(ToolStateService.class);

    /** 复合键分隔符（工具集名与工具方法名之间） */
    private static final String SEP = "\u0000";

    private final ToolStateMapper toolStateMapper;

    /** 复合键 -> enabled；null 表示尚未加载成功（表未就绪时每次重试） */
    private volatile Map<String, Boolean> enabledMap = null;

    public ToolStateService(ToolStateMapper toolStateMapper) {
        this.toolStateMapper = toolStateMapper;
    }

    /**
     * 工具集是否启用；未记录视为启用。
     */
    public boolean isToolSetEnabled(String toolSetName) {
        if (toolSetName == null) {
            return true;
        }
        Boolean enabled = enabledMap().get(toolSetName);
        return enabled == null || enabled;
    }

    /**
     * 工具方法是否启用；未记录视为启用（继承工具集级默认）。
     */
    public boolean isToolEnabled(String toolSetName, String toolName) {
        if (toolSetName == null || toolName == null) {
            return true;
        }
        Boolean enabled = enabledMap().get(methodKey(toolSetName, toolName));
        return enabled == null || enabled;
    }

    /**
     * 设置工具集启用状态并持久化。
     */
    public void setToolSetEnabled(String toolSetName, boolean enabled) {
        toolStateMapper.upsert(new ToolState(toolSetName, "", enabled ? 1 : 0));
        reload();
        log.info("Tool set [{}] enabled set to {}", toolSetName, enabled);
    }

    /**
     * 设置工具方法启用状态并持久化。
     */
    public void setToolEnabled(String toolSetName, String toolName, boolean enabled) {
        toolStateMapper.upsert(new ToolState(toolSetName, toolName, enabled ? 1 : 0));
        reload();
        log.info("Tool [{}.{}] enabled set to {}", toolSetName, toolName, enabled);
    }

    /**
     * 清理某工具集的全部状态（插件卸载时调用）。
     */
    public void removeToolSet(String toolSetName) {
        toolStateMapper.deleteByToolSetName(toolSetName);
        reload();
    }

    private static String methodKey(String toolSetName, String toolName) {
        return toolSetName + SEP + toolName;
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
                log.warn("读取 tool_state 失败（可能表尚未创建），本次按全部启用处理: {}", e.getMessage());
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
                log.warn("刷新 tool_state 缓存失败: {}", e.getMessage());
            }
        }
    }

    private Map<String, Boolean> queryAll() {
        Map<String, Boolean> result = new ConcurrentHashMap<>();
        for (ToolState state : toolStateMapper.findAll()) {
            if (state.getToolSetName() == null) {
                continue;
            }
            boolean enabled = state.getEnabled() == null || state.getEnabled() != 0;
            String key = state.getToolName() == null || state.getToolName().isEmpty()
                    ? state.getToolSetName()
                    : methodKey(state.getToolSetName(), state.getToolName());
            result.put(key, enabled);
        }
        return new HashMap<>(result);
    }
}
