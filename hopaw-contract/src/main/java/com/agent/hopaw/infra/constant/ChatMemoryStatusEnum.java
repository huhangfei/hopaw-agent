package com.agent.hopaw.infra.constant;

/**
 * @author hhf
 */

public enum ChatMemoryStatusEnum {
    DEFAULT(0, "默认"),
    TASK_DONE(1, "任务结束"),
    AUTO_CLEANUP(2, "自动清理"),
    MANUAL_CLEANUP(3, "手动清理"),
    ;
    private final int code;
    private final String description;

    ChatMemoryStatusEnum(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}
