package com.agent.hopaw.infra.service;

public interface IWebSocketBridgeService {

     String QUEUE_TOKEN_USAGE = "ws.chat.token_usage";
     String QUEUE_AGENT_MESSAGE = "ws.chat.agent_message";
     String QUEUE_GLOBAL_NOTICE = "ws.notice.global";
     String QUEUE_AVATAR_EVENT = "ws.avatar.event";
     String QUEUE_PLUGIN = "ws.plugin.cmd";
    void sendTokenUsage(String userId, String payload);

    void sendAgentMessage(String userId, String payload);

    void sendGlobalNotice(String userId, String payload);

    void sendAvatarEvent(String userId, String payload);

    /**
     * 下发插件前端指令（后端 @Tool → 前端插件 JS）。
     *
     * @param userId   用户编号，非空时按用户定向下发；null 时结合 sessionId 决定路由
     * @param sessionId 聊天会话编号，非空时优先按会话定向下发（前端 WS 注册了该会话才收到）；
     *                  为 null 时退化为按 userId 定向或广播
     * @return true=已成功投递到消息队列；false=投递失败（桥接异常，前端不会收到）
     */
    boolean sendPluginCommand(String userId, String sessionId, String payload);

}
