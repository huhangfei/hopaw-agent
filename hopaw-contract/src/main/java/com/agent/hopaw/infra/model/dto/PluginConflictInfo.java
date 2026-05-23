package com.agent.hopaw.infra.model.dto;

import java.util.List;

/**
 * 插件冲突信息
 */
public class PluginConflictInfo {
    private List<String> conflictingPlugins;
    private List<String> conflictingTools;
    private String message;

    public PluginConflictInfo() {
    }

    public PluginConflictInfo(List<String> conflictingPlugins, List<String> conflictingTools) {
        this.conflictingPlugins = conflictingPlugins;
        this.conflictingTools = conflictingTools;
        this.message = buildMessage();
    }

    private String buildMessage() {
        StringBuilder sb = new StringBuilder();
        if (conflictingPlugins != null && !conflictingPlugins.isEmpty()) {
            sb.append("插件名称冲突: ").append(String.join(", ", conflictingPlugins));
        }
        if (conflictingTools != null && !conflictingTools.isEmpty()) {
            if (sb.length() > 0) sb.append("; ");
            sb.append("工具名称冲突: ").append(String.join(", ", conflictingTools));
        }
        return sb.toString();
    }

    public List<String> getConflictingPlugins() {
        return conflictingPlugins;
    }

    public void setConflictingPlugins(List<String> conflictingPlugins) {
        this.conflictingPlugins = conflictingPlugins;
    }

    public List<String> getConflictingTools() {
        return conflictingTools;
    }

    public void setConflictingTools(List<String> conflictingTools) {
        this.conflictingTools = conflictingTools;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean hasConflicts() {
        return (conflictingPlugins != null && !conflictingPlugins.isEmpty()) ||
               (conflictingTools != null && !conflictingTools.isEmpty());
    }
}
