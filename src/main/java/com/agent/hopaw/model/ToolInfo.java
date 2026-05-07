package com.agent.hopaw.model;

import java.util.List;

public class ToolInfo {
    private String name;
    private String description;
    private List<ToolParamInfo> parameters;

    public ToolInfo(String name, String description, List<ToolParamInfo> parameters) {
        this.name = name;
        this.description = description;
        this.parameters = parameters;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public List<ToolParamInfo> getParameters() { return parameters; }
}
