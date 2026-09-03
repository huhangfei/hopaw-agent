package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.constant.AgentExecutorBizTypeEnum;
import com.agent.hopaw.infra.event.AgentMessageEvent;
import com.agent.hopaw.infra.event.ChatHistoryEvent;
import com.agent.hopaw.infra.model.dto.AiUserMessageInfo;
import com.agent.hopaw.infra.model.dto.AttachmentFile;
import com.agent.hopaw.infra.model.dto.UserChatRequest;
import com.agent.hopaw.infra.model.entity.ChatHistory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * @author hhf
 */
@Service
public class ChatUserMessageService implements IChatUserMessageService {
    private final ApplicationEventPublisher eventPublisher;

    public ChatUserMessageService(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }


    /**
     * 发送消息
     * @param sessionBizType
     * @param userId
     * @param sessionId
     * @param requestId
     * @param agentId
     * @param message
     * @param files
     */
    @Override
    public void sendMessage(AgentExecutorBizTypeEnum sessionBizType,String userId, String sessionId, String requestId, Long agentId, String message, List<AttachmentFile> files){
        AiUserMessageInfo userMessageInfo = AiUserMessageInfo.of(sessionId, requestId, message, files);
        userMessageInfo.setBizType(sessionBizType);
        //通知前端显示
        eventPublisher.publishEvent(new AgentMessageEvent(userId, agentId, userMessageInfo));
        //聊天消息入库通知
        sendChatHistoryMessage(userId, sessionId, agentId, message, files);
    }

    /**
     * 发送消息
     * @param userChatRequest
     */
    @Override
    public void sendMessage(UserChatRequest userChatRequest) {
        String userId = userChatRequest.getUserId();
        String sessionId = userChatRequest.getSessionId();
        String requestId = userChatRequest.getRequestId();
        Long agentId = userChatRequest.getAgentId();
        String message = userChatRequest.getMessage();
        List<AttachmentFile> files = userChatRequest.getFiles();
        sendMessage(userChatRequest.getSessionBizType(), userId, sessionId, requestId, agentId, message, files);
    }

    private void sendChatHistoryMessage(String userId, String sessionId, Long agentId, String message, List<AttachmentFile> files) {
        List<ChatHistory> chatHistoryList = convertToChatHistory(userId, sessionId, agentId, message, files);
        for (ChatHistory chatHistory : chatHistoryList) {
            eventPublisher.publishEvent(new ChatHistoryEvent(chatHistory));
        }
    }

    private List<ChatHistory> convertToChatHistory(String userId, String sessionId, Long agentId, String message, List<AttachmentFile> files) {
        List<ChatHistory> chatHistoryList = new ArrayList<ChatHistory>();
        if(StringUtils.hasLength(message)){
            ChatHistory chatHistory = new ChatHistory(agentId, "user", "text", message);
            chatHistory.setUserId(userId);
            chatHistory.setSessionId(sessionId);
            chatHistoryList.add(chatHistory);
        }
        if(files!=null && files.size()>0){
            //todo:等支持多种消息类型后完善存储
            for (AttachmentFile file : files) {
                ChatHistory chatHistory = new ChatHistory(agentId, "user",  file.getType(), file.getUrl());
                chatHistory.setUserId(userId);
                chatHistory.setSessionId(sessionId);
                chatHistoryList.add(chatHistory);
            }
        }
        return chatHistoryList;
    }

}
