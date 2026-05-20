package com.agent.hopaw.infra.model.dto;

public class OptionItem {
    private String value;
    private String label;

    public OptionItem() {}

    public OptionItem(String value, String label) {
        this.value = value;
        this.label = label;
    }

    public static OptionItem of(String value) {
        return new OptionItem(value, value);
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
}