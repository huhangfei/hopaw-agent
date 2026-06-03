package com.agent.hopaw.avatar.tool;

import com.agent.hopaw.avatar.model.AvatarEvent;
import com.agent.hopaw.infra.tool.ToolSecurityLevel;
import com.agent.hopaw.infra.util.InvocationParametersWrapper;
import dev.langchain4j.agent.tool.SearchBehavior;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.invocation.InvocationParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class AvatarChangeModelTool {

    private static final Logger logger = LoggerFactory.getLogger(AvatarChangeModelTool.class);

    private final ApplicationEventPublisher eventPublisher;

    public AvatarChangeModelTool(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(value = "让虚拟人换装：从用户可用的模型池中随机抽取一个不同的模型进行切换",
            searchBehavior = SearchBehavior.ALWAYS_VISIBLE)
    public String changeAvatarModel(InvocationParameters invocationParameters) {
        InvocationParametersWrapper wrapper = InvocationParametersWrapper.create(invocationParameters);
        String targetUserId = wrapper.getUserId();
        Long targetAgentId = wrapper.getAgentId();
        if (targetUserId == null || targetUserId.isBlank()) {
            return "换装失败：未指定用户Id";
        }
        if (targetAgentId == null) {
            return "换装失败：未指定智能体Id";
        }
        try {
            eventPublisher.publishEvent(AvatarEvent.changeModel(targetUserId, targetAgentId));
        } catch (Exception e) {
            logger.error("发布虚拟人换装事件失败 userId={} agentId={} err={}", targetUserId, targetAgentId, e.getMessage(), e);
            return "换装失败";
        }
        return "已向用户发送虚拟人换装事件";
    }
}
