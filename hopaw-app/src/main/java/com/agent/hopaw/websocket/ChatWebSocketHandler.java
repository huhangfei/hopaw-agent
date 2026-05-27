package com.agent.hopaw.websocket;

import com.agent.hopaw.constant.DefaultUser;
import com.agent.hopaw.infra.executor.IAgentExecutor;
import com.agent.hopaw.infra.model.dto.UserRequest;
import com.agent.hopaw.infra.service.IAgentExecutorService;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

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
    /**
     * 每个session绑定一个锁
     */
    private static final ConcurrentHashMap<String, Object> SESSION_LOCK_MAP = new ConcurrentHashMap<>();

    private static final Logger logger = LoggerFactory.getLogger(ChatWebSocketHandler.class);
    private final IAgentExecutorService agentExecutorService;
    private static final ConcurrentMap<String, ConcurrentLinkedQueue<String>> userSessionMap = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, WebSocketSession> sessionMap = new ConcurrentHashMap<>();

    public ChatWebSocketHandler(IAgentExecutorService agentExecutorService) {
        this.agentExecutorService = agentExecutorService;
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

            @SuppressWarnings("unchecked")
            List<String> skillNames = payload.getJSONArray("skills").toJavaList(String.class);

            UserRequest userRequest = new UserRequest();
            userRequest.setAgentId(agentId);
            userRequest.setUserId(getUserIdFromSession(session));
            userRequest.setMessage(userMessage);
            userRequest.setSkillNames(skillNames);
            userRequest.setSessionId(sessionId);
            userRequest.setAiModelId(aiModelId);
            userRequest.setEnableThinking(enableThinking);
            //回复一个已收到消息，开始处理
            sendFirstState(session);
            IAgentExecutor executor = agentExecutorService.createAgentExecutor(userRequest,(sId,aiMessageJson)->{
                try {
                    ConcurrentLinkedQueue<String> sessionIds = userSessionMap.get(sId);
                    if (sessionIds == null || sessionIds.isEmpty()) {
                        return;
                    }
                    for (String id : sessionIds) {
                        WebSocketSession currentSession = sessionMap.get(id);
                        if(currentSession == null){
                            return;
                        }
                        if(currentSession.isOpen()) {
                            Object lock = SESSION_LOCK_MAP.computeIfAbsent(id, k -> new Object());
                            // 同一session串行发送
                            synchronized (lock) {
                                currentSession.sendMessage(new TextMessage(aiMessageJson));
                            }
                        }
                    }
                } catch (IOException e) {
                    logger.error("error", e);
                }
            });
            executor.execute();
        } catch (Exception e) {
            logger.error("handleTextMessage error", e);
            sendError(session, "处理消息失败: " + e.getMessage());
        }
    }



    private void sendFirstState(WebSocketSession session) {
        try {
            Map<String, Object> data = new HashMap<>(2);
            data.put("type", "received");
            data.put("message", "已收到消息，开始处理");
            session.sendMessage(new TextMessage(JSON.toJSONString(data)));
        } catch (IOException e) {
            logger.error("error", e);
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
}
