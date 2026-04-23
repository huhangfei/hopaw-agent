package com.agent.hopaw.model;

public class Agent {
    private Long id;
    private String name;
    private String description;
    private String tools;
    private Integer maxMemoryRecords;

    public Agent() {}

    public Agent(String name, String description, String tools) {
        this.name = name;
        this.description = description;
        this.tools = tools;
        this.maxMemoryRecords = 20;
    }

    public Agent(String name, String description, String tools, Integer maxMemoryRecords) {
        this.name = name;
        this.description = description;
        this.tools = tools;
        this.maxMemoryRecords = maxMemoryRecords != null ? maxMemoryRecords : 20;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTools() {
        return tools;
    }

    public void setTools(String tools) {
        this.tools = tools;
    }

    public String[] getToolsArray() {
        if (tools == null || tools.isEmpty()) {
            return new String[0];
        }
        return tools.split(",");
    }

    public Integer getMaxMemoryRecords() {
        return maxMemoryRecords;
    }

    public void setMaxMemoryRecords(Integer maxMemoryRecords) {
        this.maxMemoryRecords = maxMemoryRecords;
    }
}
