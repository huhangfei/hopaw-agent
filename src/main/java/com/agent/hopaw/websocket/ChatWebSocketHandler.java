package com.agent.hopaw.websocket;

import com.agent.hopaw.mapper.ChatHistoryMapper;
import com.agent.hopaw.model.ChatHistory;
import com.agent.hopaw.service.AgentService;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private final AgentService agentService;
    private final ObjectMapper objectMapper;
    private final ChatHistoryMapper chatHistoryMapper;
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public ChatWebSocketHandler(AgentService agentService, ObjectMapper objectMapper, ChatHistoryMapper chatHistoryMapper) {
        this.agentService = agentService;
        this.objectMapper = objectMapper;
        this.chatHistoryMapper = chatHistoryMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(), session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            Map<String, String> payload = objectMapper.readValue(message.getPayload(), Map.class);
            String agentIdStr = payload.get("agentId");
            String userMessage = payload.get("message");

            if (agentIdStr == null || userMessage == null) {
                sendError(session, "缺少必要参数");
                return;
            }

            Long agentId = Long.parseLong(agentIdStr);
            AgentService.AgentExecutor executor = agentService.getAgentExecutor(agentId);

            if (executor == null) {
                sendError(session, "Agent 不存在");
                return;
            }

            sendStreamingResponse(session, agentId, userMessage, executor);
        } catch (Exception e) {
            sendError(session, "处理消息失败: " + e.getMessage());
        }
    }

    private void sendStreamingResponse(WebSocketSession session, Long agentId, String userMessage,
                                       AgentService.AgentExecutor executor) throws IOException {
        ChatHistory userChat = new ChatHistory(agentId, "user", userMessage);
        userChat.setCreateTime(LocalDateTime.now());
        chatHistoryMapper.insert(userChat);

        StringBuilder fullResponse = new StringBuilder();

        executor.executeStreaming(userMessage, chunk -> {
            try {
                fullResponse.append(chunk);
                Map<String, Object> data = new HashMap<>();
                data.put("type", "chunk");
                data.put("content", chunk);
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(data)));
            } catch (IOException e) {
                // ignore
            }
        });

        String responseText = fullResponse.toString();
        if (responseText.isEmpty()) {
            responseText = executor.execute(userMessage);
        }

        ChatHistory agentChat = new ChatHistory(agentId, "agent", responseText);
        agentChat.setCreateTime(LocalDateTime.now());
        chatHistoryMapper.insert(agentChat);

        Map<String, Object> doneData = new HashMap<>();
        doneData.put("type", "done");
        doneData.put("message", userMessage);
        doneData.put("response", responseText);
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(doneData)));
    }

    private void sendError(WebSocketSession session, String errorMessage) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("type", "error");
            data.put("message", errorMessage);
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(data)));
        } catch (IOException e) {
            // ignore
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
    }
}
