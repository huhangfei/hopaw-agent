package com.agent.hopaw.infra.model.dto;

import com.agent.hopaw.infra.tool.ToolSecurityLevel;
import java.util.List;

public class ToolInfo {
    private String name;
    private String description;
    private List<ToolParamInfo> parameters;
    private ToolSecurityLevel.Level securityLevel;

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
}
