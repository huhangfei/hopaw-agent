package com.agent.hopaw.avatar.websocket;

import com.agent.hopaw.avatar.model.AvatarEvent;
import com.agent.hopaw.avatar.service.AvatarSettingsService;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
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

    /** key = userId + "::" + agentId */
    private static final ConcurrentMap<String, ConcurrentLinkedQueue<String>> sessionKeyMap = new ConcurrentHashMap<>();
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
        Long agentId = event.getAgentId();
        if (userId == null || agentId == null) {
            logger.warn("忽略虚拟人事件，userId/agentId 为空 userId={} agentId={}", userId, agentId);
            return;
        }
        if (!avatarSettingsService.isSoundEnabled(userId, agentId) && event.getSoundFile() != null) {
            event.setSoundFile(null);
        }
        String key = buildKey(userId, agentId);
        ConcurrentLinkedQueue<String> sessionIds = sessionKeyMap.get(key);
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
        Long agentId = getAgentIdFromSession(session);
        if (userId != null && agentId != null) {
            String key = buildKey(userId, agentId);
            ConcurrentLinkedQueue<String> sessionIds = sessionKeyMap.getOrDefault(key, new ConcurrentLinkedQueue<>());
            sessionIds.add(session.getId());
            sessionKeyMap.putIfAbsent(key, sessionIds);
        } else {
            logger.warn("Avatar WS session {} 缺少 userId/agentId", session.getId());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String userId = getUserIdFromSession(session);
        Long agentId = getAgentIdFromSession(session);
        if (userId != null && agentId != null) {
            String key = buildKey(userId, agentId);
            ConcurrentLinkedQueue<String> sessionIds = sessionKeyMap.get(key);
            if (sessionIds != null) {
                sessionIds.remove(session.getId());
                if (sessionIds.isEmpty()) {
                    sessionKeyMap.remove(key);
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

    private Long getAgentIdFromSession(WebSocketSession session) {
        Map<String, Object> attributes = session.getAttributes();
        Object agentIdObj = attributes.get("agentId");
        if (agentIdObj == null) {
            return null;
        }
        try {
            if (agentIdObj instanceof Number) {
                return ((Number) agentIdObj).longValue();
            }
            return Long.parseLong(agentIdObj.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String buildKey(String userId, Long agentId) {
        return userId + "::" + agentId;
    }

    /**
     * 判断指定 (userId, agentId) 当前是否存在活跃的虚拟人 WebSocket 会话。
     * 用于定时任务避免无客户端时仍调用大模型做主动关怀。
     */
    public boolean hasActiveSession(String userId, Long agentId) {
        if (userId == null || userId.isEmpty() || agentId == null) {
            return false;
        }
        ConcurrentLinkedQueue<String> sessionIds = sessionKeyMap.get(buildKey(userId, agentId));
        return sessionIds != null && !sessionIds.isEmpty();
    }

    /**
     * 向指定用户推送 TTS 音频数据。
     * @param userId 用户 ID
     * @param agentId 智能体 ID
     * @param audioBase64 base64 编码的 MP3 音频数据
     * @param messageText 对应的文本内容（用于前端展示）
     */
    public void sendTtsAudio(String userId, Long agentId, String audioBase64, String messageText) {
        if (userId == null || agentId == null || audioBase64 == null) {
            return;
        }
        String key = buildKey(userId, agentId);
        ConcurrentLinkedQueue<String> sessionIds = sessionKeyMap.get(key);
        if (sessionIds == null || sessionIds.isEmpty()) {
            return;
        }
        JSONObject msg = new JSONObject();
        msg.put("type", "avatar_tts_audio");
        msg.put("audio", audioBase64);
        msg.put("text", messageText != null ? messageText : "");
        String messageJson = msg.toJSONString();
        for (String id : sessionIds) {
            WebSocketSession wsSession = sessionMap.get(id);
            if (wsSession != null && wsSession.isOpen()) {
                try {
                    Object lock = SESSION_LOCK_MAP.computeIfAbsent(id, k -> new Object());
                    synchronized (lock) {
                        wsSession.sendMessage(new TextMessage(messageJson));
                    }
                } catch (IOException e) {
                    logger.error("Failed to send TTS audio to session {}: {}", id, e.getMessage());
                }
            }
        }
    }
}
