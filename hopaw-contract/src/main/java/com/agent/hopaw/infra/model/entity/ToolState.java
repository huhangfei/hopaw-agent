package com.agent.hopaw.infra.model.entity;

/**
 * 工具运行态状态：持久化「工具集 / 工具方法」的启用/禁用决策。
 *
 * <p>与插件级状态（{@link PluginState}）互补，构成三级禁用：
 * 插件级 → 工具集级 → 工具方法级。未记录的工具集/方法视为启用。</p>
 *
 * <p>主键为 {@code (tool_set_name, tool_name)}；{@code tool_name} 为空串表示工具集级禁用，
 * 非空表示方法级禁用。</p>
 */
public class ToolState {

    /** 工具集名（全局唯一） */
    private String toolSetName;

    /** 工具方法名（@Tool name）；空串 = 工具集级 */
    private String toolName;

    /** 是否启用：1=启用，0=禁用。未记录视为启用。 */
    private Integer enabled;

    private String updateTime;

    public ToolState() {
    }

    public ToolState(String toolSetName, String toolName, Integer enabled) {
        this.toolSetName = toolSetName;
        this.toolName = toolName == null ? "" : toolName;
        this.enabled = enabled;
    }

    public String getToolSetName() {
        return toolSetName;
    }

    public void setToolSetName(String toolSetName) {
        this.toolSetName = toolSetName;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName == null ? "" : toolName;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }

    public String getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(String updateTime) {
        this.updateTime = updateTime;
    }
}
