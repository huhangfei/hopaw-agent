package com.agent.hopaw.infra.model.dto;

public class ValidationRule {
    private boolean required;
    private Integer minLength;
    private Integer maxLength;
    private Long minValue;
    private Long maxValue;
    private String regex;
    private String regexMessage;

    public ValidationRule() {}

    public ValidationRule required() {
        this.required = true;
        return this;
    }

    public ValidationRule length(int min, int max) {
        this.minLength = min;
        this.maxLength = max;
        return this;
    }

    public ValidationRule value(long min, long max) {
        this.minValue = min;
        this.maxValue = max;
        return this;
    }

    public ValidationRule regex(String pattern, String message) {
        this.regex = pattern;
        this.regexMessage = message;
        return this;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public Integer getMinLength() {
        return minLength;
    }

    public void setMinLength(Integer minLength) {
        this.minLength = minLength;
    }

    public Integer getMaxLength() {
        return maxLength;
    }

    public void setMaxLength(Integer maxLength) {
        this.maxLength = maxLength;
    }

    public Long getMinValue() {
        return minValue;
    }

    public void setMinValue(Long minValue) {
        this.minValue = minValue;
    }

    public Long getMaxValue() {
        return maxValue;
    }

    public void setMaxValue(Long maxValue) {
        this.maxValue = maxValue;
    }

    public String getRegex() {
        return regex;
    }

    public void setRegex(String regex) {
        this.regex = regex;
    }

    public String getRegexMessage() {
        return regexMessage;
    }

    public void setRegexMessage(String regexMessage) {
        this.regexMessage = regexMessage;
    }
}
