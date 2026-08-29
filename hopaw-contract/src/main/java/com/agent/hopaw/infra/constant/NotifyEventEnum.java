package com.agent.hopaw.infra.constant;

/**
 * 通知事项（事件）枚举：项目勾选后，对应关键变化事件发生时向项目绑定的通知渠道发送通知。
 */
public enum NotifyEventEnum {
    // ===== 任务事件 =====
    TASK_CREATED("task_created", "任务创建"),
    TASK_APPROVED("task_approved", "任务审核通过"),
    TASK_STARTED("task_started", "任务开始处理"),
    TASK_PENDING_ACCEPTANCE("task_pending_acceptance", "任务待验收"),
    TASK_COMPLETED("task_completed", "任务完成"),
    TASK_FAILED("task_failed", "任务失败"),
    TASK_REDO("task_redo", "任务重做"),
    TASK_COMMENTED("task_commented", "任务普通评论"),
    TASK_SUMMARY_COMMENTED("task_summary_commented", "任务总结评论"),
    TASK_REJECTED("task_rejected", "任务驳回"),
    TASK_CLOSED("task_closed", "任务关闭"),
    TASK_DELETED("task_deleted", "任务删除"),
    // ===== 项目事件 =====
    PROJECT_CREATED("project_created", "项目创建"),
    PROJECT_STATUS_CHANGED("project_status_changed", "项目状态变更"),
    PROJECT_DELETED("project_deleted", "项目删除"),
    PROJECT_ITERATE_FAILED("project_iterate_failed", "项目迭代失败"),
    PROJECT_ITERATE_COMPLETED("project_iterate_completed", "项目迭代完成");

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
