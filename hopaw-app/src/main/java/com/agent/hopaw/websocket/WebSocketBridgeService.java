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
        send(QUEUE_TOKEN_USAGE, "token_usage", userId, null, payload);
    }

    @Override
    public void sendAgentMessage(String userId, String payload) {
        send(QUEUE_AGENT_MESSAGE, "agent_message", userId, null, payload);
    }

    @Override
    public void sendGlobalNotice(String userId, String payload) {
        send(QUEUE_GLOBAL_NOTICE, "global_notice", userId, null, payload);
    }

    @Override
    public void sendAvatarEvent(String userId, String payload) {
        send(QUEUE_AVATAR_EVENT, "avatar_event", userId, null, payload);
    }

    @Override
    public boolean sendPluginCommand(String userId, String sessionId, String payload) {
        return send(QUEUE_PLUGIN, "plugin_command", userId, sessionId, payload);
    }

    /**
     * @return true=已成功投递到 Artemis 队列；false=投递失败
     */
    private boolean send(String queue, String eventType, String userId, String targetSessionId, String payload) {
        try {
            WebSocketBridgeMessage msg = new WebSocketBridgeMessage(eventType, userId, payload);
            msg.setTargetSessionId(targetSessionId);
            // JMSXGroupID 保证同组消息按序消费：优先按会话分组（插件指令与会话强相关），否则按用户
            String groupId = targetSessionId != null && !targetSessionId.isEmpty()
                    ? "session_" + targetSessionId
                    : (userId != null ? "user_" + userId : null);
            String effectiveGroup = groupId;
            jmsTemplate.send(queue, session -> {
                javax.jms.TextMessage textMsg = session.createTextMessage(JSON.toJSONString(msg));
                if (effectiveGroup != null) {
                    textMsg.setStringProperty("JMSXGroupID", effectiveGroup);
                }
                return textMsg;
            });
            return true;
        } catch (Exception e) {
            WebSocketBridgeService.log.error("发送消息到 Artemis 队列失败: queue={}, userId={}, sessionId={}, error={} {}",
                    queue, userId, targetSessionId, e.getMessage(), e);
            return false;
        }
    }
}
