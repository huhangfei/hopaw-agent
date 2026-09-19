package com.agent.hopaw.infra.model.dto;

import java.io.Serializable;

/**
 * WebSocket 消息桥接 DTO：Spring Event → Artemis → WebSocket 推送
 */
public class WebSocketBridgeMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 事件类型：token_usage / agent_message / global_notice / avatar_event */
    private String eventType;

    /** 用户编号，作为 JMSXGroupID 实现按用户串行消费 */
    private String userId;

    /** 目标会话编号（聊天会话隔离）：插件指令按 sessionId 定向下发，null 表示按 userId 或广播 */
    private String targetSessionId;

    /** JSON 序列化的原始事件数据 */
    private String payload;

    /** 时间戳 */
    private long timestamp;

    public WebSocketBridgeMessage() {
    }

    public WebSocketBridgeMessage(String eventType, String userId, String payload) {
        this.eventType = eventType;
        this.userId = userId;
        this.payload = payload;
        this.timestamp = System.currentTimeMillis();
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getTargetSessionId() {
        return targetSessionId;
    }

    public void setTargetSessionId(String targetSessionId) {
        this.targetSessionId = targetSessionId;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
