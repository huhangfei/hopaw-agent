package com.agent.hopaw.model;

import java.util.List;

public class ToolSetInfo {
    private String name;
    private String description;
    private List<ToolInfo> tools;

    public ToolSetInfo(String name, String description, List<ToolInfo> tools) {
        this.name = name;
        this.description = description;
        this.tools = tools;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public List<ToolInfo> getTools() { return tools; }
}
