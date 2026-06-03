package com.agent.hopaw.avatar.tool;

import com.agent.hopaw.avatar.model.AvatarEvent;
import com.agent.hopaw.infra.tool.AgentTool;
import com.agent.hopaw.infra.tool.ToolSecurityLevel;
import com.agent.hopaw.infra.util.InvocationParametersWrapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.invocation.InvocationParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class AvatarProactiveTool {

    private static final Logger logger = LoggerFactory.getLogger(AvatarProactiveTool.class);

    private final ApplicationEventPublisher eventPublisher;

    public AvatarProactiveTool(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(value = "向用户发送虚拟人主动消息",
            searchBehavior = dev.langchain4j.agent.tool.SearchBehavior.ALWAYS_VISIBLE)
    public String sendMessageToUser(@P("需要推送给用户的消息内容") String message,
                                   InvocationParameters invocationParameters) {
        InvocationParametersWrapper wrapper = InvocationParametersWrapper.create(invocationParameters);
        String targetUserId = wrapper.getUserId();
        if (targetUserId == null || targetUserId.isBlank()) {
            return "发送失败：未指定用户Id";
        }
        if (message == null || message.isBlank()) {
            return "发送失败：消息内容不能为空";
        }
        try {
            eventPublisher.publishEvent(AvatarEvent.proactiveMessage(targetUserId, message.trim()));
        } catch (Exception e) {
            logger.error("发布虚拟人主动消息事件失败 userId={} err={}", targetUserId, e.getMessage(), e);
            return "发送失败";
        }
        return "已向用户发送虚拟人主动消息";
    }
}
