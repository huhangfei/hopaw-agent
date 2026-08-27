package com.agent.hopaw.infra.event;

import com.agent.hopaw.infra.model.dto.GlobalNoticeMessage;

/**
 * 全局通知事件：由 IGlobalNoticeService 发布，WebSocket 层监听后推送到 /ws/notice
 */
public class GlobalNoticeEvent {

    private final String userId;
    private final GlobalNoticeMessage message;

    public GlobalNoticeEvent(String userId, GlobalNoticeMessage message) {
        this.userId = userId;
        this.message = message;
    }

    /** 接收用户；null 表示广播 */
    public String getUserId() {
        return userId;
    }

    public GlobalNoticeMessage getMessage() {
        return message;
    }
}
