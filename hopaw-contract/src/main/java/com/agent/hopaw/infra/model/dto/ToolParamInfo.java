package com.agent.hopaw.infra.model.dto;

public class ToolParamInfo {
    private String name;
    private String description;
    private boolean required;
    private String type;

    public ToolParamInfo() {
    }

    public ToolParamInfo(String name, String description, boolean required, String type) {
        this.name = name;
        this.description = description;
        this.required = required;
        this.type = type;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isRequired() { return required; }
    public void setRequired(boolean required) { this.required = required; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
}
