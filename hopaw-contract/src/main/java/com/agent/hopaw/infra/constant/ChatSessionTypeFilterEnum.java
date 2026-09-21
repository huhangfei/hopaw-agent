package com.agent.hopaw.infra.constant;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 会话类型筛选项：把 UI 上的「聊天 / 项目管理 / 工作流任务」分组映射为
 * chat_sessions.biz_type 的取值集合（含历史遗留写法）。
 *
 * <p>聊天会话在库里既可能是空值也可能是字面量 {@code chat}（见
 * ChatSessionMapper.findVisibleSessions 的判定），所以除取值集合外还有
 * {@link #isIncludeBlank()} 标记，由 SQL 侧补上 {@code biz_type IS NULL OR biz_type = ''}。</p>
 */
public enum ChatSessionTypeFilterEnum {
    /** 全部：不做 biz_type 过滤 */
    ALL("all", "全部", Collections.emptyList(), false),
    CHAT("chat", "聊天", Collections.singletonList(AgentExecutorBizTypeEnum.Chat.getValue()), true),
    PROJECT("project", "项目管理",
            Arrays.asList(AgentExecutorBizTypeEnum.ProjectChat.getValue(), "project-chat"), false),
    TASK("task", "工作流任务",
            Arrays.asList(AgentExecutorBizTypeEnum.WorkflowTaskChat.getValue(), "workflow-task-chat"), false),
    ;

    private final String value;
    private final String description;
    private final List<String> bizTypes;
    private final boolean includeBlank;

    ChatSessionTypeFilterEnum(String value, String description, List<String> bizTypes, boolean includeBlank) {
        this.value = value;
        this.description = description;
        this.bizTypes = bizTypes;
        this.includeBlank = includeBlank;
    }

    public String getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }

    /** 该分组对应的 biz_type 取值集合（ALL 为空集合，由调用方视作「不过滤」） */
    public List<String> getBizTypes() {
        return bizTypes;
    }

    /** 是否需要把 biz_type 为 NULL / 空串的会话也算进本分组 */
    public boolean isIncludeBlank() {
        return includeBlank;
    }

    public boolean isAll() {
        return this == ALL;
    }

    /** 按取值反查；空或未知一律回退「全部」，保证筛选参数异常时仍能出数据 */
    public static ChatSessionTypeFilterEnum getByValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return ALL;
        }
        for (ChatSessionTypeFilterEnum item : values()) {
            if (item.value.equalsIgnoreCase(value.trim())) {
                return item;
            }
        }
        return ALL;
    }
}
