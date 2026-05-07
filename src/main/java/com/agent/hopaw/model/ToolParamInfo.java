package com.agent.hopaw.model;

public class ToolParamInfo {
    private String name;
    private String description;
    private boolean required;
    private String type;

    public ToolParamInfo(String name, String description, boolean required, String type) {
        this.name = name;
        this.description = description;
        this.required = required;
        this.type = type;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public boolean isRequired() { return required; }
    public String getType() { return type; }
}
