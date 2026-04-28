package com.agent.hopaw.websocket;

import com.agent.hopaw.mapper.ChatHistoryMapper;
import com.agent.hopaw.model.ChatHistory;
import com.agent.hopaw.model.ToolCallInfo;
import com.agent.hopaw.service.AgentService;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger= LoggerFactory.getLogger(ChatWebSocketHandler.class);
    private final AgentService agentService;
    private final ChatHistoryMapper chatHistoryMapper;
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final AtomicLong conversationSequence = new AtomicLong(0);

    public ChatWebSocketHandler(AgentService agentService, ChatHistoryMapper chatHistoryMapper) {
        this.agentService = agentService;
        this.chatHistoryMapper = chatHistoryMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(), session);
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
                sendError(session, "Agent 不存在");
                return;
            }

            String responseId = UUID.randomUUID().toString();
            sendStreamingResponse(session, agentId, userMessage, executor, responseId);
        } catch (Exception e) {
            sendError(session, "处理消息失败: " + e.getMessage());
        }
    }

    private void sendStreamingResponse(WebSocketSession session, Long agentId, String userMessage,
                                       AgentService.AgentExecutor executor, String responseId) throws IOException {
        ChatHistory userChat = new ChatHistory(agentId, "user", "text", userMessage);
        userChat.setCreateTime(LocalDateTime.now());
        chatHistoryMapper.insert(userChat);

        StreamingState state = new StreamingState();
        state.responseId = responseId;

        executor.executeStreaming(userMessage, chunk -> {
            try {
                state.accumulatedText.append(chunk);
                Map<String, Object> data = new HashMap<>(3);
                data.put("type", "chunk");
                data.put("content", chunk);
                data.put("responseId", responseId);
                session.sendMessage(new TextMessage(JSON.toJSONString(data)));
            } catch (IOException e) {
                logger.error("error",e);
            }
        }, toolCallInfo -> {
            try {
                if (ToolCallInfo.STATUS_STARTING.equals(toolCallInfo.getStatus())) {
                    if (state.accumulatedText.length() > 0) {
                        ChatHistory textChat = new ChatHistory(agentId, "agent", "text", state.accumulatedText.toString());
                        textChat.setCreateTime(LocalDateTime.now());
                        chatHistoryMapper.insert(textChat);
                        state.accumulatedText.setLength(0);
                    }

                    state.currentToolCallId = toolCallInfo.getToolCallId();
                    state.currentToolName = toolCallInfo.getToolName();
                    state.currentToolArguments = (JSONObject) toolCallInfo.getArguments();
                } else if (ToolCallInfo.STATUS_EXECUTED.equals(toolCallInfo.getStatus())) {
                    ChatHistory toolChat = new ChatHistory(
                            agentId, "agent", "tool_call",
                            toolCallInfo.getToolCallId(), toolCallInfo.getToolName(),
                            toolCallInfo.getArguments().toString(), (String) toolCallInfo.getResult()
                    );
                    toolChat.setCreateTime(LocalDateTime.now());
                    chatHistoryMapper.insert(toolChat);

                    state.currentToolCallId = null;
                    state.currentToolName = null;
                    state.currentToolArguments = null;
                }
                toolCallInfo.setResponseId(responseId);
                session.sendMessage(new TextMessage(JSON.toJSONString(toolCallInfo)));
            } catch (IOException e) {
                logger.error("error",e);
            }
        });

        if (state.accumulatedText.length() > 0) {
            ChatHistory textChat = new ChatHistory(agentId, "agent", "text", state.accumulatedText.toString());
            textChat.setCreateTime(LocalDateTime.now());
            chatHistoryMapper.insert(textChat);
        }

        Map<String, Object> doneData = new HashMap<>(4);
        doneData.put("type", "done");
        doneData.put("message", userMessage);
        doneData.put("response", state.accumulatedText.toString());
        doneData.put("responseId", responseId);
        session.sendMessage(new TextMessage(JSON.toJSONString(doneData)));
    }

    private static class StreamingState {
        String responseId;
        StringBuilder accumulatedText = new StringBuilder();
        String currentToolCallId;
        String currentToolName;
        JSONObject currentToolArguments;
    }

    private void sendError(WebSocketSession session, String errorMessage) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("type", "error");
            data.put("message", errorMessage);
            session.sendMessage(new TextMessage(JSON.toJSONString(data)));
        } catch (IOException e) {
            logger.error("error",e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
    }
}
