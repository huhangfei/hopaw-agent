package com.agent.hopaw.websocket;

import com.agent.hopaw.infra.model.dto.WebSocketBridgeMessage;
import com.agent.hopaw.infra.service.IWebSocketBridgeService;
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
public class WebSocketBridgeService implements IWebSocketBridgeService {

    private static final Logger log = LoggerFactory.getLogger(WebSocketBridgeService.class);


    private final JmsTemplate jmsTemplate;

    public WebSocketBridgeService(JmsTemplate jmsTemplate) {
        this.jmsTemplate = jmsTemplate;
    }

    @Override
    public void sendTokenUsage(String userId, String payload) {
        send(QUEUE_TOKEN_USAGE, "token_usage", userId, payload);
    }

    @Override
    public void sendAgentMessage(String userId, String payload) {
        send(QUEUE_AGENT_MESSAGE, "agent_message", userId, payload);
    }

    @Override
    public void sendGlobalNotice(String userId, String payload) {
        send(QUEUE_GLOBAL_NOTICE, "global_notice", userId, payload);
    }

    @Override
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
            WebSocketBridgeService.log.error("发送消息到 Artemis 队列失败: queue={}, userId={}, error={} {}", queue, userId, e.getMessage(), e);
        }
    }
}
