package com.agent.hopaw.avatar.tool;

import com.agent.hopaw.avatar.mapper.AvatarConfigMapper;
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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class AvatarProactiveTool {

    private static final Logger logger = LoggerFactory.getLogger(AvatarProactiveTool.class);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ApplicationEventPublisher eventPublisher;
    private final AvatarConfigMapper avatarConfigMapper;

    public AvatarProactiveTool(ApplicationEventPublisher eventPublisher,
                               AvatarConfigMapper avatarConfigMapper) {
        this.eventPublisher = eventPublisher;
        this.avatarConfigMapper = avatarConfigMapper;
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(value = {"向用户发送消息", "向用户发送虚拟人主动消息"},
            searchBehavior = dev.langchain4j.agent.tool.SearchBehavior.ALWAYS_VISIBLE)
    public String sendMessageToUser(@P("需要推送给用户的消息内容") String message,
                                   InvocationParameters invocationParameters) {
        InvocationParametersWrapper wrapper = InvocationParametersWrapper.create(invocationParameters);
        String targetUserId = wrapper.getUserId();
        Long targetAgentId = wrapper.getAgentId();
        if (targetUserId == null || targetUserId.isBlank()) {
            return "发送失败：未指定用户Id";
        }
        if (targetAgentId == null) {
            return "发送失败：未指定智能体Id";
        }
        if (message == null || message.isBlank()) {
            return "发送失败：消息内容不能为空";
        }
        try {
            eventPublisher.publishEvent(AvatarEvent.proactiveMessage(targetUserId, targetAgentId, message.trim()));
            // 记录最后一次主动问候时间，供定时任务判断是否需要补发 wave
            try {
                String now = LocalDateTime.now().format(TIME_FORMAT);
                avatarConfigMapper.updateLastProactiveGreetingTime(targetUserId, targetAgentId, now);
            } catch (Exception persistError) {
                logger.warn("更新 lastProactiveGreetingTime 失败 userId={} agentId={} err={}",
                        targetUserId, targetAgentId, persistError.getMessage());
            }
        } catch (Exception e) {
            logger.error("发布虚拟人主动消息事件失败 userId={} agentId={} err={}", targetUserId, targetAgentId, e.getMessage(), e);
            return "发送失败";
        }
        return "已向用户发送虚拟人主动消息";
    }
}
