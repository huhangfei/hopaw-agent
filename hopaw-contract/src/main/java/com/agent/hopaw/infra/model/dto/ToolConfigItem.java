package com.agent.hopaw.infra.model.dto;

import java.util.List;

public class ToolConfigItem {
    private String key;
    private String label;
    private String description;
    private ConfigType type;
    private List<String> options;
    private String defaultValue;

    public enum ConfigType {
        TEXT_SINGLE("单文本"),
        TEXT_MULTI("多文本"),
        SELECT("下拉"),
        RADIO("单选"),
        CHECKBOX("多选");

        private final String description;

        ConfigType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    public ToolConfigItem() {}

    public ToolConfigItem(String key, String label, String description, ConfigType type) {
        this.key = key;
        this.label = label;
        this.description = description;
        this.type = type;
    }

    public ToolConfigItem(String key, String label, String description, ConfigType type, List<String> options) {
        this.key = key;
        this.label = label;
        this.description = description;
        this.type = type;
        this.options = options;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ConfigType getType() {
        return type;
    }

    public void setType(ConfigType type) {
        this.type = type;
    }

    public List<String> getOptions() {
        return options;
    }

    public void setOptions(List<String> options) {
        this.options = options;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }
}
