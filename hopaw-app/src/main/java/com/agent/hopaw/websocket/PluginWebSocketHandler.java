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
 *
 * <p><b>会话隔离</b>：前端连接后需上行注册消息 {@code {"type":"register","sessionId":"..." }}
 * 声明自己所属的聊天会话；后端指令携带 targetSessionId 时只推给注册了该会话的连接，
 * 避免 A 会话的画布/五子棋指令串台到 B 会话的浏览器。会话路由未命中时回退
 * 按 userId 定向（userId 也为空则广播），保证旧版前端（不发送注册消息）仍可用。</p>
 */
@Component
public class PluginWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(PluginWebSocketHandler.class);

    private final ConcurrentHashMap<String, WebSocketSession> sessionMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> userSessions = new ConcurrentHashMap<>();
    /** wsId → 该连接注册的聊天会话编号（前端 register 消息声明，可重新注册覆盖） */
    private final ConcurrentHashMap<String, String> wsSessionIdMap = new ConcurrentHashMap<>();
    /** 聊天会话编号 → 注册到该会话的 wsId 集合 */
    private final ConcurrentHashMap<String, Set<String>> sessionWsMap = new ConcurrentHashMap<>();
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
        unregisterSessionOf(session.getId());
        logger.info("Plugin WS closed: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 目前仅处理上行注册消息：声明本连接所属的聊天会话（会话切换由前端重连/重新注册）
        String payload = message.getPayload();
        try {
            RegisterMsg reg = JSON.parseObject(payload, RegisterMsg.class);
            if (reg == null || !"register".equals(reg.type)) {
                return;
            }
            registerSessionOf(session.getId(), reg.sessionId);
            logger.info("Plugin WS registered: ws={} sessionId={}", session.getId(), reg.sessionId);
        } catch (Exception e) {
            logger.debug("Plugin WS upstream ignored: {}", e.getMessage());
        }
    }

    /** 记录（或覆盖）ws 连接的会话归属 */
    private void registerSessionOf(String wsId, String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return;
        }
        String old = wsSessionIdMap.put(wsId, sessionId);
        if (sessionId.equals(old)) {
            return;
        }
        if (old != null) {
            removeSessionWs(old, wsId);
        }
        sessionWsMap.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet()).add(wsId);
    }

    /** 清除 ws 连接的会话归属 */
    private void unregisterSessionOf(String wsId) {
        String sessionId = wsSessionIdMap.remove(wsId);
        if (sessionId != null) {
            removeSessionWs(sessionId, wsId);
        }
    }

    private void removeSessionWs(String sessionId, String wsId) {
        Set<String> ids = sessionWsMap.get(sessionId);
        if (ids != null) {
            ids.remove(wsId);
            if (ids.isEmpty()) {
                sessionWsMap.remove(sessionId);
            }
        }
    }

    @JmsListener(destination = WebSocketBridgeService.QUEUE_PLUGIN)
    public void consumePluginCommand(javax.jms.TextMessage message) throws JMSException {
        WebSocketBridgeMessage bridge = JSON.parseObject(message.getText(), WebSocketBridgeMessage.class);
        String payload = bridge.getPayload();
        String userId = bridge.getUserId();
        String targetSessionId = bridge.getTargetSessionId();

        if (targetSessionId != null && !targetSessionId.isEmpty()) {
            if (pushToSession(targetSessionId, payload)) {
                return;
            }
            // 该会话没有已注册的连接（旧版前端不发送注册消息）：回退按用户定向，避免指令丢失
            logger.warn("Plugin WS: no connection registered for sessionId={}, fallback to userId={}", targetSessionId, userId);
            if (userId != null && !userId.isEmpty()) {
                pushToUser(userId, payload);
            } else {
                broadcast(payload);
            }
        } else if (userId != null && !userId.isEmpty()) {
            pushToUser(userId, payload);
        } else {
            broadcast(payload);
        }
    }

    /** 按会话定向推送。@return true=至少推给一个连接 */
    private boolean pushToSession(String targetSessionId, String json) {
        Set<String> ids = sessionWsMap.get(targetSessionId);
        if (ids == null || ids.isEmpty()) {
            return false;
        }
        boolean delivered = false;
        for (String id : ids) {
            WebSocketSession ws = sessionMap.get(id);
            if (ws != null && ws.isOpen() && sendSafe(ws, json, "会话推送")) {
                delivered = true;
            }
        }
        return delivered;
    }

    private void pushToUser(String userId, String json) {
        Set<String> ids = userSessions.get(userId);
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (String id : ids) {
            WebSocketSession ws = sessionMap.get(id);
            if (ws != null) {
                sendSafe(ws, json, "用户推送");
            }
        }
    }

    private void broadcast(String json) {
        for (WebSocketSession ws : sessionMap.values()) {
            if (ws != null && ws.isOpen()) {
                sendSafe(ws, json, "广播");
            }
        }
    }

    private boolean sendSafe(WebSocketSession ws, String json, String scene) {
        try {
            synchronized (ws) {
                ws.sendMessage(new TextMessage(json));
            }
            return true;
        } catch (IOException e) {
            logger.error("推送插件指令失败({}) session {}: {}", scene, ws.getId(), e.getMessage());
            return false;
        }
    }

    private String getUserId(WebSocketSession session) {
        Map<String, Object> attributes = session.getAttributes();
        Object userId = attributes.get("userId");
        return userId != null ? userId.toString() : null;
    }

    /** 上行注册消息 DTO */
    private static final class RegisterMsg {
        public String type;
        public String sessionId;
    }
}
