package com.agent.hopaw.infra.model.dto;

import com.agent.hopaw.infra.tool.ToolSecurityLevel;
import java.util.List;

public class ToolInfo {
    private String name;
    /**
     * descriptions 拼接后的描述
     */
    private String description;
    private List<String> descriptions;
    private List<ToolParamInfo> parameters;
    private ToolSecurityLevel.Level securityLevel;
    /** 是否启用：false 表示工具方法级禁用（未记录视为 true） */
    private boolean enabled = true;

    public ToolInfo() {
    }

    public ToolInfo(String name, String description, List<ToolParamInfo> parameters) {
        this.name = name;
        this.description = description;
        this.parameters = parameters;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<ToolParamInfo> getParameters() { return parameters; }
    public void setParameters(List<ToolParamInfo> parameters) { this.parameters = parameters; }

    public ToolSecurityLevel.Level getSecurityLevel() { return securityLevel; }
    public void setSecurityLevel(ToolSecurityLevel.Level securityLevel) { this.securityLevel = securityLevel; }

    public List<String> getDescriptions() {
        return descriptions;
    }

    public void setDescriptions(List<String> descriptions) {
        this.descriptions = descriptions;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
