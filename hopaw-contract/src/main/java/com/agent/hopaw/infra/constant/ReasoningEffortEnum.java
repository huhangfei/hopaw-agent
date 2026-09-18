package com.agent.hopaw.infra.constant;

/**
 * 思考努力程度参数枚举。
 * 取值与模型/提供商扩展参数中的 reasoningEffort 保持一致（none/minimal/low/medium/high/xhigh/max）。
 */
public enum ReasoningEffortEnum {
    NONE("none", "无"),
    MINIMAL("minimal", "极轻"),
    LOW("low", "低"),
    MEDIUM("medium", "中"),
    HIGH("high", "高"),
    XHIGH("xhigh", "极高"),
    MAX("max", "最大");

    /**
     * 默认值：高
     */
    public static final String DEFAULT_CODE = HIGH.code;

    private final String code;
    private final String name;

    ReasoningEffortEnum(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public static ReasoningEffortEnum fromCode(String code) {
        for (ReasoningEffortEnum effort : values()) {
            if (effort.code.equals(code)) {
                return effort;
            }
        }
        return null;
    }

    /**
     * 判断取值是否合法（空值视为未设置，返回 true）
     */
    public static boolean isValidCode(String code) {
        return code == null || code.isEmpty() || fromCode(code) != null;
    }
}
