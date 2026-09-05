package com.agent.hopaw.infra.model.dto;


import com.agent.hopaw.infra.constant.AgentExecutorBizTypeEnum;

public class AiMessageBaseInfo {
    private String type;
    private String sessionId;
    private String requestId;
    private String content;
    /** 消息编号：流式消息（text/thinking）开始时生成并入库，前端按编号定位 DOM 元素追加片段；页面刷新后可凭编号续接 */
    private String messageNo;
    /** 流式状态：partial=增量片段（content 为本次新增内容）；done=消息结束（content 为该消息全量内容，用于补全） */
    private String status;

    public AiMessageBaseInfo(String type) {
        this.type = type;
    }

    public AiMessageBaseInfo type(String type) {
        this.setType(type);
        return this;
    }
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
    public AiMessageBaseInfo sessionId(String sessionId) {
        this.setSessionId(sessionId);
        return this;
    }
    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public AiMessageBaseInfo requestId(String requestId) {
        this.setRequestId(requestId);
        return this;
    }
    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public AiMessageBaseInfo content(String content) {
        this.setContent(content);
        return this;
    }
    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public AiMessageBaseInfo messageNo(String messageNo) {
        this.setMessageNo(messageNo);
        return this;
    }
    public String getMessageNo() {
        return messageNo;
    }

    public void setMessageNo(String messageNo) {
        this.messageNo = messageNo;
    }

    public String getStatus() {
        return status;
    }

    public AiMessageBaseInfo status(String status) {
        this.status = status;
        return this;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    /** 会话业务类型（chat / workflowTaskChat / projectChat），供前端区分会话来源 */
    private AgentExecutorBizTypeEnum bizType;
    public AgentExecutorBizTypeEnum getBizType() {
        return bizType;
    }

    public void setBizType(AgentExecutorBizTypeEnum bizType) {
        this.bizType = bizType;
    }


    public static AiMessageBaseInfo build(String type, String sessionId, String requestId) {
        AiMessageBaseInfo aiMessageBaseInfo = new AiMessageBaseInfo(type)
                .sessionId(sessionId)
                .requestId(requestId);
        return aiMessageBaseInfo;
    }
    public static AiMessageBaseInfo done(String sessionId, String requestId) {
        return AiMessageBaseInfo.build("done", sessionId, requestId);
    }
    public static AiMessageBaseInfo taskDone(String sessionId, String requestId) {
        return AiMessageBaseInfo.build("task-done", sessionId, requestId);
    }
    public static AiMessageBaseInfo error(String sessionId, String requestId,String content) {
        return AiMessageBaseInfo.build("error", sessionId, requestId).content(content);
    }
    public static AiMessageBaseInfo warn(String sessionId, String requestId,String content) {
        return AiMessageBaseInfo.build("warn", sessionId, requestId).content(content);
    }
    public static AiMessageBaseInfo chunk(String sessionId, String requestId,String content) {
        return AiMessageBaseInfo.build("chunk", sessionId, requestId).content(content);
    }
    /** 文本流式增量片段：content 为本次新增内容，前端按 messageNo 追加渲染 */
    public static AiMessageBaseInfo chunkPartial(String sessionId, String requestId, String fragment) {
        return chunk(sessionId, requestId, fragment).status("partial");
    }
    /** 文本流式结束补发：content 为该消息全量内容，前端按 messageNo 覆盖补全（中途进入/刷新场景） */
    public static AiMessageBaseInfo chunkDone(String sessionId, String requestId, String fullContent) {
        return chunk(sessionId, requestId, fullContent).status("done");
    }
    public static AiMessageBaseInfo sessionTitle(String sessionId, String requestId, String content) {
        return AiMessageBaseInfo.build("session-title", sessionId, requestId).content(content);
    }
}
