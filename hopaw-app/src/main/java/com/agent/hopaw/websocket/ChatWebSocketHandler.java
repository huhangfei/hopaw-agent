package com.agent.hopaw.websocket;

import com.agent.hopaw.infra.constant.AgentExecutorBizTypeEnum;
import com.agent.hopaw.infra.event.AgentMessageEvent;
import com.agent.hopaw.infra.event.TokenUsageEvent;
import com.agent.hopaw.infra.model.dto.AiMessageBaseInfo;
import com.agent.hopaw.infra.model.dto.AttachmentFile;
import com.agent.hopaw.infra.model.dto.UserChatRequest;
import com.agent.hopaw.infra.service.IChatService;
import com.agent.hopaw.infra.websocket.dto.WebSocketBridgeMessage;
import com.agent.hopaw.infra.websocket.service.WebSocketBridgeService;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

/**
 * @author hhf
 */
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final ConcurrentHashMap<String, Object> SESSION_LOCK_MAP = new ConcurrentHashMap<>();

    private static final Logger logger = LoggerFactory.getLogger(ChatWebSocketHandler.class);
    private final IChatService chatService;
    private final WebSocketBridgeService bridgeService;
    private static final ConcurrentMap<String, ConcurrentLinkedQueue<String>> userSessionMap = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, WebSocketSession> sessionMap = new ConcurrentHashMap<>();

    public ChatWebSocketHandler(IChatService chatService, WebSocketBridgeService bridgeService) {
        this.chatService = chatService;
        this.bridgeService = bridgeService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        logger.info("Session opened: {}", session.getId());
        sessionMap.put(session.getId(), session);
        String userId = getUserIdFromSession(session);
        if (userId != null) {
            ConcurrentLinkedQueue<String> sessionIds = userSessionMap.getOrDefault(userId, new ConcurrentLinkedQueue<>());
            sessionIds.add(session.getId());
            userSessionMap.putIfAbsent(userId, sessionIds);
            logger.info("Session {} initialized with agentId from URL: {}", session.getId(), userId);
        }
    }
    private String getUserIdFromSession(WebSocketSession session) {
        Map<String, Object> attributes = session.getAttributes();
        Object userIdObj = attributes.get("userId");
        if (userIdObj != null) {
            return userIdObj.toString();
        }
        return null;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            JSONObject payload = JSON.parseObject(message.getPayload());
            Long agentId = payload.getLong("agentId");
            String userMessage = payload.getString("message");
            if (agentId == null || userMessage == null) {
                sendError(session, "缺少必要参数");
                return;
            }
            String sessionId = payload.getString("sessionId");
            Long aiModelId = payload.getLong("aiModelId");
            Boolean enableThinking = payload.getBoolean("enableThinking");
            String reasoningEffort = payload.getString("reasoningEffort");
            String toolCallPermission = payload.getString("toolCallPermission");
            @SuppressWarnings("unchecked")
            List<String> skillNames = payload.getJSONArray("skills").toJavaList(String.class);

            List<AttachmentFile> files = new ArrayList<>();
            if (payload.containsKey("files") && payload.get("files") != null) {
                try {
                    files = payload.getJSONArray("files").toJavaList(AttachmentFile.class);
                } catch (Exception e) {
                    logger.warn("解析附件文件失败: {}", e.getMessage());
                }
            }

            UserChatRequest userChatRequest = new UserChatRequest();
            userChatRequest.setAgentId(agentId);
            userChatRequest.setUserId(getUserIdFromSession(session));
            userChatRequest.setMessage(userMessage);
            userChatRequest.setSkillNames(skillNames);
            userChatRequest.setSessionId(sessionId);
            userChatRequest.setAiModelId(aiModelId);
            userChatRequest.setEnableThinking(enableThinking);
            userChatRequest.setReasoningEffort(reasoningEffort);
            userChatRequest.setToolCallPermission(toolCallPermission);
            userChatRequest.setFiles(files);
            chatService.handle(userChatRequest);
        } catch (Exception e) {
            logger.error("handleTextMessage error", e);
            sendError(session, "处理消息失败: " + e.getMessage());
        }
    }
    private void sendError(WebSocketSession session, String errorMessage) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("type", "error");
            data.put("message", errorMessage);
            session.sendMessage(new TextMessage(JSON.toJSONString(data)));
        } catch (IOException e) {
            logger.error("error", e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String userId = getUserIdFromSession(session);
        if (userId != null) {
            userSessionMap.getOrDefault(userId, new ConcurrentLinkedQueue<>()).remove(session.getId());
            if(userSessionMap.get(userId).isEmpty()){
                userSessionMap.remove(userId);
            }
            logger.info("Session {} closed, removed userId: {}", session.getId(), userId);
        }
        sessionMap.remove(session.getId());
        SESSION_LOCK_MAP.remove(session.getId());
        logger.info("Session closed: {}", session.getId());
    }

    // ==================== Spring Event → Artemis ====================

    @EventListener
    public void onTokenUsageMessage(TokenUsageEvent message) {
        String source = message.getSource();
        boolean broadcast = "workflow-task-chat".equals(source) || "project-chat".equals(source);
        String userId = message.getUserId();
        if (userId == null && !broadcast) {
            return;
        }
        Map<String, Object> data = new HashMap<>();
        data.put("type", "token_usage");
        data.put("id", null);
        data.put("agentId", message.getAgentId());
        data.put("modelName", message.getModelName());
        data.put("inputTokens", message.getInputTokens());
        data.put("outputTokens", message.getOutputTokens());
        data.put("totalTokens", message.getTotalTokens());
        data.put("sessionId", message.getSessionId());
        data.put("source", message.getSource());
        data.put("createTime", message.getCreateTime() != null ? message.getCreateTime().toString() : null);
        data.put("broadcast", broadcast);
        bridgeService.sendTokenUsage(userId, JSON.toJSONString(data));
    }

    @EventListener
    public void onAgentMessageEvent(AgentMessageEvent event) {
        AiMessageBaseInfo message = event.getMessage();
        AgentExecutorBizTypeEnum bizType = message != null ? message.getBizType() : AgentExecutorBizTypeEnum.Chat;
        boolean broadcast = AgentExecutorBizTypeEnum.WorkflowTaskChat.equals(bizType)
                || AgentExecutorBizTypeEnum.ProjectChat.equals(bizType);
        String userId = event.getUserId();
        if (userId == null && !broadcast) {
            return;
        }
        JSONObject data = (JSONObject) JSON.toJSON(message);
        data.put("broadcast", broadcast);
        bridgeService.sendAgentMessage(userId, data.toJSONString());
    }

    // ==================== Artemis → WebSocket 推送 ====================

    @JmsListener(destination = WebSocketBridgeService.QUEUE_TOKEN_USAGE)
    public void consumeTokenUsage(javax.jms.TextMessage message) throws JMSException {
        WebSocketBridgeMessage bridge = JSON.parseObject(message.getText(), WebSocketBridgeMessage.class);
        JSONObject data = JSON.parseObject(bridge.getPayload());
        boolean broadcast = data.getBooleanValue("broadcast");
        String messageJson = data.toJSONString();

        if (broadcast) {
            sendToAllOnlineUsers(messageJson);
            return;
        }
        String userId = bridge.getUserId();
        if (userId == null) return;
        ConcurrentLinkedQueue<String> sessionIds = userSessionMap.get(userId);
        if (sessionIds == null || sessionIds.isEmpty()) return;
        for (String id : sessionIds) {
            WebSocketSession wsSession = sessionMap.get(id);
            if (wsSession != null && wsSession.isOpen()) {
                try {
                    Object lock = SESSION_LOCK_MAP.computeIfAbsent(id, k -> new Object());
                    synchronized (lock) {
                        wsSession.sendMessage(new TextMessage(messageJson));
                    }
                } catch (IOException e) {
                    logger.error("Failed to send token_usage to session {}: {}", id, e.getMessage());
                }
            }
        }
    }

    @JmsListener(destination = WebSocketBridgeService.QUEUE_AGENT_MESSAGE)
    public void consumeAgentMessage(javax.jms.TextMessage message) throws JMSException {
        WebSocketBridgeMessage bridge = JSON.parseObject(message.getText(), WebSocketBridgeMessage.class);
        JSONObject data = JSON.parseObject(bridge.getPayload());
        boolean broadcast = data.getBooleanValue("broadcast");
        // 移除 broadcast 字段，不推送给前端
        data.remove("broadcast");
        String messageJson = data.toJSONString();

        if (broadcast) {
            sendToAllOnlineUsers(messageJson);
            return;
        }
        String userId = bridge.getUserId();
        if (userId == null) return;
        ConcurrentLinkedQueue<String> sessionIds = userSessionMap.get(userId);
        if (sessionIds == null || sessionIds.isEmpty()) return;
        for (String id : sessionIds) {
            WebSocketSession wsSession = sessionMap.get(id);
            if (wsSession != null && wsSession.isOpen()) {
                try {
                    Object lock = SESSION_LOCK_MAP.computeIfAbsent(id, k -> new Object());
                    synchronized (lock) {
                        wsSession.sendMessage(new TextMessage(messageJson));
                    }
                } catch (IOException e) {
                    logger.error("Failed to send agent_message to session {}: {}", id, e.getMessage());
                }
            }
        }
    }

    /** 广播消息给所有在线用户的 WebSocket 连接 */
    private void sendToAllOnlineUsers(String messageJson) {
        for (Map.Entry<String, WebSocketSession> entry : sessionMap.entrySet()) {
            WebSocketSession wsSession = entry.getValue();
            if (wsSession == null || !wsSession.isOpen()) {
                continue;
            }
            try {
                Object lock = SESSION_LOCK_MAP.computeIfAbsent(entry.getKey(), k -> new Object());
                synchronized (lock) {
                    wsSession.sendMessage(new TextMessage(messageJson));
                }
            } catch (IOException e) {
                logger.error("Failed to broadcast message to session {}: {}", entry.getKey(), e.getMessage());
            }
        }
    }
}
