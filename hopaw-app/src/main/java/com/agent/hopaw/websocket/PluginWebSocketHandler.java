package com.agent.hopaw.websocket;

import com.agent.hopaw.infra.model.dto.WebSocketBridgeMessage;
import com.alibaba.fastjson2.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import javax.jms.JMSException;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 插件前端指令 WebSocket（/ws/plugin）：后端 @Tool → 前端插件 JS 的下行指令通道。
 *
 * <p>与聊天主链路解耦：插件指令不影响会话流，用户切换会话不干扰插件状态。
 * 结构同 {@link NoticeWebSocketHandler}，复用 {@link WebSocketBridgeService} 的 Artemis 桥接。</p>
 *
 * <p>结果回传不通过本通道（走 HTTP 上报接口），本通道仅用于下行指令。</p>
 */
@Component
public class PluginWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(PluginWebSocketHandler.class);

    private final ConcurrentHashMap<String, WebSocketSession> sessionMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> userSessions = new ConcurrentHashMap<>();
    private final WebSocketBridgeService bridgeService;

    public PluginWebSocketHandler(WebSocketBridgeService bridgeService) {
        this.bridgeService = bridgeService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessionMap.put(session.getId(), session);
        String userId = getUserId(session);
        if (userId != null) {
            userSessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(session.getId());
        }
        logger.info("Plugin WS opened: {} userId={}", session.getId(), userId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionMap.remove(session.getId());
        String userId = getUserId(session);
        if (userId != null) {
            Set<String> ids = userSessions.get(userId);
            if (ids != null) {
                ids.remove(session.getId());
                if (ids.isEmpty()) {
                    userSessions.remove(userId);
                }
            }
        }
        logger.info("Plugin WS closed: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 下行通道，暂不处理前端上行（结果走 HTTP 上报接口）
    }

    @JmsListener(destination = WebSocketBridgeService.QUEUE_PLUGIN)
    public void consumePluginCommand(javax.jms.TextMessage message) throws JMSException {
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
        Set<String> ids = userSessions.get(userId);
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
                    logger.error("推送插件指令失败 session {}: {}", id, e.getMessage());
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
                    logger.error("广播插件指令失败 session {}: {}", ws.getId(), e.getMessage());
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
