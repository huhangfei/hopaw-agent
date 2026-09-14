package com.agent.hopaw.infra.service;

public interface IWebSocketBridgeService {

     String QUEUE_TOKEN_USAGE = "ws.chat.token_usage";
     String QUEUE_AGENT_MESSAGE = "ws.chat.agent_message";
     String QUEUE_GLOBAL_NOTICE = "ws.notice.global";
     String QUEUE_AVATAR_EVENT = "ws.avatar.event";
    void sendTokenUsage(String userId, String payload);

    void sendAgentMessage(String userId, String payload);

    void sendGlobalNotice(String userId, String payload);

    void sendAvatarEvent(String userId, String payload);

}
