package com.agent.hopaw.websocket;

import com.agent.hopaw.mapper.ChatHistoryMapper;
import com.agent.hopaw.model.ChatHistory;
import com.agent.hopaw.service.AgentService;
import com.alibaba.fastjson2.JSON;
import dev.langchain4j.data.message.UserMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(ChatWebSocketHandler.class);
    private final AgentService agentService;
    private final ChatHistoryMapper chatHistoryMapper;
    private static final Map<Long, String> sessionAgentMap = new ConcurrentHashMap<>();
    private static final Map<String, WebSocketSession> sessionMap = new ConcurrentHashMap<>();

    public ChatWebSocketHandler(AgentService agentService, ChatHistoryMapper chatHistoryMapper) {
        this.agentService = agentService;
        this.chatHistoryMapper = chatHistoryMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        logger.info("Session opened: {}", session.getId());
        sessionMap.put(session.getId(), session);
        Long agentId = getAgentIdFromSession(session);
        if (agentId != null) {
            sessionAgentMap.put(agentId,session.getId());
            logger.info("Session {} initialized with agentId from URL: {}", session.getId(), agentId);
        }
    }
    private Long getAgentIdFromSession(WebSocketSession session) {
        Map<String, Object> attributes = session.getAttributes();
        Object agentIdObj = attributes.get("agentId");
        if (agentIdObj != null) {
            return Long.parseLong(agentIdObj.toString());
        }
        return null;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            Map<String, String> payload = JSON.parseObject(message.getPayload(), Map.class);
            String agentIdStr = payload.get("agentId");
            String userMessage = payload.get("message");

            if (agentIdStr == null || userMessage == null) {
                sendError(session, "缺少必要参数");
                return;
            }

            Long agentId = Long.parseLong(agentIdStr);
            AgentService.AgentExecutor executor = agentService.getAgentExecutor(agentId);

            if (executor == null) {
                sendError(session, "Agent 初始化执行失败，请检查相关配置。");
                return;
            }
            ChatHistory userChat = new ChatHistory(agentId, "user", "text", userMessage);
            userChat.setCreateTime(LocalDateTime.now());
            chatHistoryMapper.insert(userChat);

            executor.executeStreaming(new UserMessage("userId",userMessage), aiMessageJson->{
                try {
                    String sessionId = sessionAgentMap.get(agentId);
                    if(sessionId == null){
                        return;
                    }
                    WebSocketSession currentSession = sessionMap.get(sessionId);
                    if(currentSession == null){
                        return;
                    }
                   if(currentSession.isOpen()){
                       currentSession.sendMessage(new TextMessage(aiMessageJson));
                   }
                } catch (IOException e) {
                    logger.error("error", e);
                }
            }, chatHistory -> {
                if(chatHistory.getMessageType().equals("tool_call")){
                    ChatHistory old = chatHistoryMapper.findByAgentIdAndToolCallId(agentId, chatHistory.getToolCallId());
                    if(old != null){
                        chatHistory.setId(old.getId());
                        chatHistoryMapper.updateToolCallStatusAndContent(chatHistory.getId(), chatHistory.getToolCallStatus(), chatHistory.getContent());
                    }else{
                        chatHistoryMapper.insert(chatHistory);
                    }
                }else{
                    chatHistoryMapper.insert(chatHistory);
                }
            });
        } catch (Exception e) {
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
        Long agentId = getAgentIdFromSession(session);
        if (agentId != null) {
            sessionAgentMap.remove(agentId);
            logger.info("Session {} closed, removed agentId: {}", session.getId(), agentId);
        }
        sessionMap.remove(session.getId());
        logger.info("Session closed: {}", session.getId());
    }
}
