package com.agent.hopaw.infra.model.dto;

import java.time.LocalDateTime;

/**
 * 会话记忆展示 VO：chat_memory 记录解析后的结构化数据
 */
public class ChatMemoryVO {
    /** 记忆 id（chat_memory.id） */
    private Long id;
    private String sessionId;
    /** 消息类型：system / user / ai / toolResult */
    private String type;
    /** 主文本内容（用户文本 / AI回复 / 系统提示 / 工具执行结果） */
    private String content;
    /** AI 思考内容（仅 ai 类型） */
    private String thinking;
    /** 工具名（AI 工具调用请求 / 工具执行结果） */
    private String toolName;
    /** 工具调用参数（仅 AI 工具调用请求） */
    private String toolArguments;
    /** 工具执行是否出错（仅 toolResult 类型） */
    private Boolean error;
    /** 状态：0 默认 / 1 任务结束 / 2 自动清理 / 3 手动清理 */
    private Integer status;
    private LocalDateTime createTime;

    public ChatMemoryVO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getThinking() { return thinking; }
    public void setThinking(String thinking) { this.thinking = thinking; }

    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }

    public String getToolArguments() { return toolArguments; }
    public void setToolArguments(String toolArguments) { this.toolArguments = toolArguments; }

    public Boolean getError() { return error; }
    public void setError(Boolean error) { this.error = error; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
