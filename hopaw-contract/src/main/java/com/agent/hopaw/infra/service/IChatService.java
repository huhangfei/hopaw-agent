package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.executor.IAgentExecutor;
import com.agent.hopaw.infra.model.dto.UserChatRequest;

/**
 * 聊天业务服务接口
 */
public interface IChatService {
    /**
     * 创建聊天代理执行器
     * @param userChatRequest
     * @return
     */
    void handle(UserChatRequest userChatRequest);
}
