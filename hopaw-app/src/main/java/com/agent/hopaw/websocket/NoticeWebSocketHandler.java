package com.agent.hopaw.websocket;

import com.agent.hopaw.infra.event.GlobalNoticeEvent;
import com.agent.hopaw.infra.model.dto.GlobalNoticeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 全局通知 WebSocket（/ws/notice）：面向所有页面的公共消息推送通道
 * 连接即订阅；推送服务通过 pushToUser 主动写入
 */
@Component
public class NoticeWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(NoticeWebSocketHandler.class);

    /** ws连接ID -> WebSocketSession */
    private final ConcurrentHashMap<String, WebSocketSession> sessionMap = new ConcurrentHashMap<>();
    /** 用户ID -> 该用户的一组ws连接ID */
    private final ConcurrentHashMap<String, java.util.Set<String>> userSessions = new ConcurrentHashMap<>();

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

    /** 通知通道不接收客户端消息，收到即忽略 */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
    }

    /** 向指定用户的所有通知连接推送消息 */
    public void pushToUser(String userId, GlobalNoticeMessage message) {
        if (userId == null) {
            return;
        }
        java.util.Set<String> ids = userSessions.get(userId);
        if (ids == null || ids.isEmpty()) {
            return;
        }
        String json = com.alibaba.fastjson2.JSON.toJSONString(message);
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

    /** 广播给所有在线用户 */
    public void broadcast(GlobalNoticeMessage message) {
        String json = com.alibaba.fastjson2.JSON.toJSONString(message);
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

    /** 监听全局通知事件：定向推送或广播到 /ws/notice */
    @EventListener
    public void onGlobalNotice(GlobalNoticeEvent event) {
        GlobalNoticeMessage message = event.getMessage();
        if (event.getUserId() == null) {
            broadcast(message);
        } else {
            pushToUser(event.getUserId(), message);
        }
    }

    private String getUserId(WebSocketSession session) {
        Map<String, Object> attributes = session.getAttributes();
        Object userId = attributes.get("userId");
        return userId != null ? userId.toString() : null;
    }
}

