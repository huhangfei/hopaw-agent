package com.agent.hopaw.infra.constant;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 工作流任务状态枚举
 */
public enum TaskStatusEnum {
    PENDING("pending", "待启动", false),
    PENDING_EXECUTION("pending_execution", "待执行", false),
    PROCESSING("processing", "处理中", true),
    PENDING_ACCEPTANCE("pending_acceptance", "待验收", true),
    COMPLETED("completed", "已完成", false),
    FAILED("failed", "失败", true),
    REJECTED("rejected", "已驳回", false),
    CLOSED("closed", "已关闭", false),
    ;

    private final String code;
    private final String description;
    /** 是否由智能体执行驱动（处理中/待验收/失败由智能体执行产生） */
    private final boolean agentDriven;

    TaskStatusEnum(String code, String description, boolean agentDriven) {
        this.code = code;
        this.description = description;
        this.agentDriven = agentDriven;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public boolean isAgentDriven() {
        return agentDriven;
    }

    /** 按存储值解析枚举，未匹配返回 null */
    public static TaskStatusEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (TaskStatusEnum status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }

    /** 看板展示顺序（不含已驳回/已关闭） */
    public static List<TaskStatusEnum> boardOrder() {
        return Arrays.asList(PENDING, PENDING_EXECUTION, PROCESSING, PENDING_ACCEPTANCE, COMPLETED, FAILED);
    }

    /** 看板展示顺序对应的状态值列表 */
    public static List<String> boardOrderCodes() {
        List<String> codes = new ArrayList<>();
        for (TaskStatusEnum status : boardOrder()) {
            codes.add(status.getCode());
        }
        return codes;
    }
}
