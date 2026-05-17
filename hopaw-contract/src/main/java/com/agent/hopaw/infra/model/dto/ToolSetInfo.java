package com.agent.hopaw.infra.model.dto;

import java.util.List;

public class ToolSetInfo {
    private String name;
    private String description;
    private String icon;
    private List<ToolInfo> tools;

    public ToolSetInfo(String name, String description, String icon, List<ToolInfo> tools) {
        this.name = name;
        this.description = description;
        this.icon = icon;
        this.tools = tools;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getIcon() { return icon; }
    public List<ToolInfo> getTools() { return tools; }
}
