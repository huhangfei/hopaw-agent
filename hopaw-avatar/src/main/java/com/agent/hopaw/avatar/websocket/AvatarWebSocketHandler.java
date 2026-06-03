package com.agent.hopaw.avatar.websocket;

import com.agent.hopaw.avatar.model.AvatarEvent;
import com.agent.hopaw.avatar.service.AvatarSettingsService;
import com.alibaba.fastjson2.JSON;
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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

@Component
public class AvatarWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(AvatarWebSocketHandler.class);
    private static final ConcurrentHashMap<String, Object> SESSION_LOCK_MAP = new ConcurrentHashMap<>();

    private static final ConcurrentMap<String, ConcurrentLinkedQueue<String>> userSessionMap = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, WebSocketSession> sessionMap = new ConcurrentHashMap<>();

    private final AvatarSettingsService avatarSettingsService;

    public AvatarWebSocketHandler(AvatarSettingsService avatarSettingsService) {
        this.avatarSettingsService = avatarSettingsService;
    }

    @EventListener
    public void onAvatarEvent(AvatarEvent event) {
        if (event == null) {
            return;
        }
        String userId = event.getUserId();
        if (userId == null) {
            return;
        }
        if (!avatarSettingsService.isSoundEnabled(userId) && event.getSoundFile() != null) {
            event.setSoundFile(null);
        }
        ConcurrentLinkedQueue<String> sessionIds = userSessionMap.get(userId);
        if (sessionIds == null || sessionIds.isEmpty()) {
            return;
        }
        String messageJson = JSON.toJSONString(event);
        for (String id : sessionIds) {
            WebSocketSession wsSession = sessionMap.get(id);
            if (wsSession != null && wsSession.isOpen()) {
                try {
                    Object lock = SESSION_LOCK_MAP.computeIfAbsent(id, k -> new Object());
                    synchronized (lock) {
                        wsSession.sendMessage(new TextMessage(messageJson));
                    }
                } catch (IOException e) {
                    logger.error("Failed to send avatar event to session {}: {}", id, e.getMessage());
                }
            }
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        logger.info("Avatar WS session opened: {}", session.getId());
        sessionMap.put(session.getId(), session);
        String userId = getUserIdFromSession(session);
        if (userId != null) {
            ConcurrentLinkedQueue<String> sessionIds = userSessionMap.getOrDefault(userId, new ConcurrentLinkedQueue<>());
            sessionIds.add(session.getId());
            userSessionMap.putIfAbsent(userId, sessionIds);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String userId = getUserIdFromSession(session);
        if (userId != null) {
            ConcurrentLinkedQueue<String> sessionIds = userSessionMap.get(userId);
            if (sessionIds != null) {
                sessionIds.remove(session.getId());
                if (sessionIds.isEmpty()) {
                    userSessionMap.remove(userId);
                }
            }
        }
        sessionMap.remove(session.getId());
        SESSION_LOCK_MAP.remove(session.getId());
        logger.info("Avatar WS session closed: {}", session.getId());
    }

    private String getUserIdFromSession(WebSocketSession session) {
        Map<String, Object> attributes = session.getAttributes();
        Object userIdObj = attributes.get("userId");
        if (userIdObj != null) {
            return userIdObj.toString();
        }
        return null;
    }
}
