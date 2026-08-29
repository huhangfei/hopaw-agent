package com.agent.hopaw.infra.constant;

/**
 * 通知事项（事件）枚举：项目勾选后，对应关键变化事件发生时向项目绑定的通知渠道发送通知。
 */
public enum NotifyEventEnum {
    TASK_CREATED("task_created", "任务创建"),
    TASK_PENDING_ACCEPTANCE("task_pending_acceptance", "任务待验收"),
    TASK_COMPLETED("task_completed", "任务完成"),
    TASK_FAILED("task_failed", "任务失败"),
    TASK_REDO("task_redo", "任务重做"),
    PROJECT_STATUS_CHANGED("project_status_changed", "项目状态变更"),
    PROJECT_ITERATE_FAILED("project_iterate_failed", "项目迭代失败");

    private final String code;
    private final String description;

    NotifyEventEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /** 按编码解析，未匹配返回 null */
    public static NotifyEventEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (NotifyEventEnum e : values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        return null;
    }
}
