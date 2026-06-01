package com.agent.hopaw.avatar.model;

public enum AvatarAction {
    IDLE("idle", "待机"),
    THINKING("thinking", "思考中"),
    TOOL_EXECUTING("tool_executing", "执行工具中"),
    LEVEL_UP("level_up", "升级了"),
    EXCITED("excited", "兴奋"),
    CONFUSED("confused", "困惑"),
    WAVE("wave", "挥手"),
    SLEEP("sleep", "休眠"),
    TYPING("typing", "打字中"),
    CELEBRATE("celebrate", "庆祝");

    private final String code;
    private final String description;

    AvatarAction(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static AvatarAction fromMessageType(String type) {
        if (type == null) {
            return IDLE;
        }
        return switch (type) {
            case "thinking" -> THINKING;
            case "tool_call" -> TOOL_EXECUTING;
            case "chunk" -> TYPING;
            case "done", "task-done" -> IDLE;
            case "error", "warn" -> CONFUSED;
            default -> IDLE;
        };
    }
}