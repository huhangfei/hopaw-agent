package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.constant.AgentExecutorBizTypeEnum;
import com.agent.hopaw.infra.model.dto.AttachmentFile;
import com.agent.hopaw.infra.model.dto.UserChatRequest;

import java.util.List;

public interface IChatUserMessageService {
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
    void sendMessage(AgentExecutorBizTypeEnum sessionBizType, String userId, String sessionId, String requestId, Long agentId, String message, List<AttachmentFile> files);

    /**
     * 发送消息
     * @param userChatRequest
     */
    void sendMessage(UserChatRequest userChatRequest);
}
