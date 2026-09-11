package com.agent.hopaw.infra.websocket.service;

import com.agent.hopaw.infra.websocket.dto.WebSocketBridgeMessage;
import com.alibaba.fastjson2.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;

/**
 * WebSocket 消息桥接服务：Spring Event → Artemis Queue
 * 后续由 @JmsListener 消费并推送 WebSocket
 */
@Service
public class WebSocketBridgeService {

    private static final Logger log = LoggerFactory.getLogger(WebSocketBridgeService.class);

    public static final String QUEUE_TOKEN_USAGE = "ws.chat.token_usage";
    public static final String QUEUE_AGENT_MESSAGE = "ws.chat.agent_message";
    public static final String QUEUE_GLOBAL_NOTICE = "ws.notice.global";
    public static final String QUEUE_AVATAR_EVENT = "ws.avatar.event";

    private final JmsTemplate jmsTemplate;

    public WebSocketBridgeService(JmsTemplate jmsTemplate) {
        this.jmsTemplate = jmsTemplate;
    }

    public void sendTokenUsage(String userId, String payload) {
        send(QUEUE_TOKEN_USAGE, "token_usage", userId, payload);
    }

    public void sendAgentMessage(String userId, String payload) {
        send(QUEUE_AGENT_MESSAGE, "agent_message", userId, payload);
    }

    public void sendGlobalNotice(String userId, String payload) {
        send(QUEUE_GLOBAL_NOTICE, "global_notice", userId, payload);
    }

    public void sendAvatarEvent(String userId, String payload) {
        send(QUEUE_AVATAR_EVENT, "avatar_event", userId, payload);
    }

    private void send(String queue, String eventType, String userId, String payload) {
        try {
            WebSocketBridgeMessage msg = new WebSocketBridgeMessage(eventType, userId, payload);
            jmsTemplate.send(queue, session -> {
                javax.jms.TextMessage textMsg = session.createTextMessage(JSON.toJSONString(msg));
                if (userId != null) {
                    textMsg.setStringProperty("JMSXGroupID", "user_" + userId);
                }
                return textMsg;
            });
        } catch (Exception e) {
            log.error("发送消息到 Artemis 队列失败: queue={}, userId={}, error={} {}", queue, userId, e.getMessage(),e);
        }
    }
}
