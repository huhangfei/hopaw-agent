package com.agent.hopaw.avatar.tool;

import com.agent.hopaw.avatar.mapper.AvatarConfigMapper;
import com.agent.hopaw.avatar.model.AvatarEvent;
import com.agent.hopaw.infra.service.IAvatarSettingsService;
import com.agent.hopaw.infra.tool.AgentTool;
import com.agent.hopaw.infra.tool.ToolSecurityLevel;
import com.agent.hopaw.infra.util.InvocationParametersWrapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.SearchBehavior;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.invocation.InvocationParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * @author hhf
 */
@Component
public class AvatarTool implements AgentTool {

    private static final Logger logger = LoggerFactory.getLogger(AvatarTool.class);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ApplicationEventPublisher eventPublisher;
    private final AvatarConfigMapper avatarConfigMapper;

    public AvatarTool(ApplicationEventPublisher eventPublisher,
                      AvatarConfigMapper avatarConfigMapper) {
        this.eventPublisher = eventPublisher;
        this.avatarConfigMapper = avatarConfigMapper;
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(value = {"换个漂亮衣服", "让虚拟人换装：从用户可用的模型池中随机抽取一个不同的模型进行切换"},
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

    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(value = {"走动一下", "控制虚拟人在用户屏幕上的相对移动（以当前所在位置为原点(0,0)，传入目标坐标和移动时长）"},
            searchBehavior = SearchBehavior.ALWAYS_VISIBLE)
    public String moveAvatar(@P("相对当前 X 方向的位移像素（可正可负，范围建议 -1000 ~ 1000）") int targetX,
                             @P("相对当前 Y 方向的位移像素（可正可负，范围建议 -1000 ~ 1000）") int targetY,
                             @P("完成移动所需的毫秒数（范围 100 ~ 10000）") int durationMs,
                             InvocationParameters invocationParameters) {
        InvocationParametersWrapper wrapper = InvocationParametersWrapper.create(invocationParameters);
        String targetUserId = wrapper.getUserId();
        Long targetAgentId = wrapper.getAgentId();
        if (targetUserId == null || targetUserId.isBlank()) {
            return "移动失败：未指定用户Id";
        }
        if (targetAgentId == null) {
            return "移动失败：未指定智能体Id";
        }
        if (durationMs <= 0) {
            return "移动失败：时长必须大于 0 毫秒";
        }
        try {
            eventPublisher.publishEvent(AvatarEvent.move(targetUserId, targetAgentId, targetX, targetY, durationMs));
        } catch (Exception e) {
            logger.error("发布虚拟人移动事件失败 userId={} agentId={} err={}", targetUserId, targetAgentId, e.getMessage(), e);
            return "移动失败";
        }
        return "已向用户发送虚拟人移动事件";
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(value = {"和用户聊天", "向用户发送虚拟人主动消息"},
            searchBehavior = dev.langchain4j.agent.tool.SearchBehavior.ALWAYS_VISIBLE)
    public String sendAvatarMessageToUser(@P("需要推送给用户的消息内容") String message,
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

    @Override
    public String getName() {
        return IAvatarSettingsService.TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return "虚拟人工具（当启用虚拟人功能时，此工具默认启用）";
    }

    @Override
    public String getIcon() {
        return "avatar-tool.svg";
    }

    @Override
    public String getVersion() {
        return AgentTool.super.getVersion();
    }

    @Override
    public String getAuthor() {
        return AgentTool.super.getAuthor();
    }

    @Override
    public String getUrl() {
        return AgentTool.super.getUrl();
    }

    @Override
    public String getKeyword() {
        return "虚拟人：换装、移动、消息";
    }

    @Override
    public void asyncInit() {
        AgentTool.super.asyncInit();
    }

    @Override
    public void destroy() {
        AgentTool.super.destroy();
    }
}
