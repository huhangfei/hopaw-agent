package com.agent.hopaw.infra.model.entity;

/**
 * 插件运行时状态（与磁盘上的插件 JAR 解耦，卸载/禁用状态持久化）。
 *
 * <p>只保存「框架侧对插件的决策」，不保存可由插件自身提供的元数据（版本、名称等），
 * 避免出现与 JAR 不一致的陈旧副本。</p>
 */
public class PluginState {

    /** 插件标识（主键） */
    private String pluginId;

    /** 是否启用：1=启用，0=禁用。未记录视为启用。 */
    private Integer enabled;

    private String updateTime;

    public PluginState() {
    }

    public PluginState(String pluginId, Integer enabled) {
        this.pluginId = pluginId;
        this.enabled = enabled;
    }

    public String getPluginId() {
        return pluginId;
    }

    public void setPluginId(String pluginId) {
        this.pluginId = pluginId;
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
