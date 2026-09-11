package com.agent.hopaw.websocket;

import com.agent.hopaw.infra.event.GlobalNoticeEvent;
import com.agent.hopaw.infra.model.dto.GlobalNoticeMessage;
import com.agent.hopaw.infra.websocket.dto.WebSocketBridgeMessage;
import com.agent.hopaw.infra.websocket.service.WebSocketBridgeService;
import com.alibaba.fastjson2.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import javax.jms.JMSException;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 全局通知 WebSocket（/ws/notice）：面向所有页面的公共消息推送通道
 * 连接即订阅；通过 Artemis 桥接消费推送
 */
@Component
public class NoticeWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(NoticeWebSocketHandler.class);

    private final ConcurrentHashMap<String, WebSocketSession> sessionMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, java.util.Set<String>> userSessions = new ConcurrentHashMap<>();
    private final WebSocketBridgeService bridgeService;

    public NoticeWebSocketHandler(WebSocketBridgeService bridgeService) {
        this.bridgeService = bridgeService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessionMap.put(session.getId(), session);
        String userId = getUserId(session);
        if (userId != null) {
            userSessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(session.getId());
        }
        logger.info("Notice WS opened: {} userId={}", session.getId(), userId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionMap.remove(session.getId());
        String userId = getUserId(session);
        if (userId != null) {
            java.util.Set<String> ids = userSessions.get(userId);
            if (ids != null) {
                ids.remove(session.getId());
                if (ids.isEmpty()) {
                    userSessions.remove(userId);
                }
            }
        }
        logger.info("Notice WS closed: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
    }

    // ==================== Spring Event → Artemis ====================

    @EventListener
    public void onGlobalNotice(GlobalNoticeEvent event) {
        GlobalNoticeMessage message = event.getMessage();
        String userId = event.getUserId();
        bridgeService.sendGlobalNotice(userId, JSON.toJSONString(message));
    }

    // ==================== Artemis → WebSocket 推送 ====================

    @JmsListener(destination = WebSocketBridgeService.QUEUE_GLOBAL_NOTICE)
    public void consumeGlobalNotice(javax.jms.TextMessage message) throws JMSException {
        WebSocketBridgeMessage bridge = JSON.parseObject(message.getText(), WebSocketBridgeMessage.class);
        String payload = bridge.getPayload();
        String userId = bridge.getUserId();

        if (userId == null) {
            broadcast(payload);
        } else {
            pushToUser(userId, payload);
        }
    }

    private void pushToUser(String userId, String json) {
        java.util.Set<String> ids = userSessions.get(userId);
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (String id : ids) {
            WebSocketSession ws = sessionMap.get(id);
            if (ws != null && ws.isOpen()) {
                try {
                    synchronized (ws) {
                        ws.sendMessage(new TextMessage(json));
                    }
                } catch (IOException e) {
                    logger.error("推送通知失败 session {}: {}", id, e.getMessage());
                }
            }
        }
    }

    private void broadcast(String json) {
        for (WebSocketSession ws : sessionMap.values()) {
            if (ws != null && ws.isOpen()) {
                try {
                    synchronized (ws) {
                        ws.sendMessage(new TextMessage(json));
                    }
                } catch (IOException e) {
                    logger.error("广播通知失败 session {}: {}", ws.getId(), e.getMessage());
                }
            }
        }
    }

    private String getUserId(WebSocketSession session) {
        Map<String, Object> attributes = session.getAttributes();
        Object userId = attributes.get("userId");
        return userId != null ? userId.toString() : null;
    }
}
