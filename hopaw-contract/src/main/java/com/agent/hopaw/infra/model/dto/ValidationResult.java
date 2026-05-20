package com.agent.hopaw.infra.model.dto;

public class ValidationResult {
    private String key;
    private String label;
    private boolean valid;
    private String message;

    public ValidationResult() {}

    public ValidationResult(String key, String label, boolean valid, String message) {
        this.key = key;
        this.label = label;
        this.valid = valid;
        this.message = message;
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

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
