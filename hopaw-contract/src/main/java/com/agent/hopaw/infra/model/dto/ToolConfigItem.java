package com.agent.hopaw.infra.model.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ToolConfigItem {
    private String key;
    private String label;
    private String description;
    private ConfigType type;
    private List<OptionItem> options;
    private String defaultValue;
    private ValidationRule validation;
    /** 配置结构类型，默认单值结构 */
    private ConfigStructure structure = ConfigStructure.SINGLE;
    /** 是否加密存储：默认 true（新建配置项默认加密），非敏感配置项请显式设置为 false */
    private boolean sensitive = true;

    /**
     * 配置结构类型：单值结构（一条配置一个值）或映射组结构（多个 mapKey，每个对应一组子配置）
     */
    public enum ConfigStructure {
        SINGLE("单值结构"),
        MAP("映射组结构");

        private final String description;

        ConfigStructure(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    public enum ConfigType {
        TEXT_SINGLE("单文本"),
        TEXT_AREA("大文本"),
        TEXT_MULTI("多文本"),
        TEXT_PASSWORD("单密码"),
        TEXT_PASSWORD_MULTI("多密码"),
        SELECT("下拉"),
        SELECT_MULTI("多选下拉"),
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
        this.options = options.stream().map(OptionItem::of).collect(Collectors.toList());
    }

    public ToolConfigItem(String key, String label, String description, ConfigType type, OptionItem... options) {
        this.key = key;
        this.label = label;
        this.description = description;
        this.type = type;
        this.options = new ArrayList<>();
        for (OptionItem option : options) {
            this.options.add(option);
        }
    }

    public ToolConfigItem addOption(String value, String label) {
        if (this.options == null) {
            this.options = new ArrayList<>();
        }
        this.options.add(new OptionItem(value, label));
        return this;
    }

    public ToolConfigItem validation(ValidationRule rule) {
        this.validation = rule;
        return this;
    }

    /**
     * 链式设置是否加密存储
     */
    public ToolConfigItem sensitive(boolean sensitive) {
        this.sensitive = sensitive;
        return this;
    }

    public ValidationResult validate(String value) {
        ValidationResult result = new ValidationResult();
        result.setKey(this.key);
        result.setLabel(this.label);

        if (validation == null) {
            result.setValid(true);
            return result;
        }

        boolean isEmpty = value == null || value.trim().isEmpty();

        if (validation.isRequired() && isEmpty) {
            result.setValid(false);
            result.setMessage(this.label + "不能为空");
            return result;
        }

        if (!isEmpty) {
            if (validation.getMinLength() != null && value.length() < validation.getMinLength()) {
                result.setValid(false);
                result.setMessage(this.label + "长度不能小于" + validation.getMinLength());
                return result;
            }

            if (validation.getMaxLength() != null && value.length() > validation.getMaxLength()) {
                result.setValid(false);
                result.setMessage(this.label + "长度不能大于" + validation.getMaxLength());
                return result;
            }

            if (validation.getMinValue() != null || validation.getMaxValue() != null) {
                try {
                    long num = Long.parseLong(value);
                    if (validation.getMinValue() != null && num < validation.getMinValue()) {
                        result.setValid(false);
                        result.setMessage(this.label + "不能小于" + validation.getMinValue());
                        return result;
                    }
                    if (validation.getMaxValue() != null && num > validation.getMaxValue()) {
                        result.setValid(false);
                        result.setMessage(this.label + "不能大于" + validation.getMaxValue());
                        return result;
                    }
                } catch (NumberFormatException e) {
                    result.setValid(false);
                    result.setMessage(this.label + "必须是数字");
                    return result;
                }
            }

            if (validation.getRegex() != null && !Pattern.matches(validation.getRegex(), value)) {
                result.setValid(false);
                result.setMessage(validation.getRegexMessage() != null ? validation.getRegexMessage() : this.label + "格式不正确");
                return result;
            }
        }

        result.setValid(true);
        return result;
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

    public List<OptionItem> getOptions() {
        return options;
    }

    public void setOptions(List<OptionItem> options) {
        this.options = options;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public ValidationRule getValidation() {
        return validation;
    }

    public void setValidation(ValidationRule validation) {
        this.validation = validation;
    }

    public ConfigStructure getStructure() {
        return structure;
    }

    public void setStructure(ConfigStructure structure) {
        this.structure = structure;
    }

    public boolean isSensitive() {
        return sensitive;
    }

    public void setSensitive(boolean sensitive) {
        this.sensitive = sensitive;
    }
}
