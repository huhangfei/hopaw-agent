package com.agent.hopaw.infra.constant;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 项目状态枚举（含状态流转规则）
 */
public enum ProjectStatusEnum {
    PLANNING("planning", "规划中"),
    IN_PROGRESS("in_progress", "进行中"),
    PAUSED("paused", "已暂停"),
    COMPLETED("completed", "已完成"),
    ARCHIVED("archived", "已归档"),
    ;

    private final String code;
    private final String description;

    /** 状态流转规则：key 可流转到 value 集合中的任一状态 */
    private static final Map<ProjectStatusEnum, Set<ProjectStatusEnum>> TRANSITIONS = new HashMap<>();

    static {
        TRANSITIONS.put(PLANNING, EnumSet.of(IN_PROGRESS, ARCHIVED));
        TRANSITIONS.put(IN_PROGRESS, EnumSet.of(PAUSED, COMPLETED, ARCHIVED));
        TRANSITIONS.put(PAUSED, EnumSet.of(IN_PROGRESS, ARCHIVED));
        TRANSITIONS.put(COMPLETED, EnumSet.of(IN_PROGRESS, ARCHIVED));
        TRANSITIONS.put(ARCHIVED, EnumSet.of(PLANNING));
    }

    ProjectStatusEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /** 是否允许从当前状态流转到目标状态 */
    public boolean canTransitionTo(ProjectStatusEnum target) {
        Set<ProjectStatusEnum> allowed = TRANSITIONS.get(this);
        return allowed != null && allowed.contains(target);
    }

    /** 按存储值解析枚举，未匹配返回 null */
    public static ProjectStatusEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (ProjectStatusEnum status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }
}
