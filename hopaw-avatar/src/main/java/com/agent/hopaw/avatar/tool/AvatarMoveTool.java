package com.agent.hopaw.avatar.tool;

import com.agent.hopaw.avatar.model.AvatarEvent;
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

@Component
public class AvatarMoveTool {

    private static final Logger logger = LoggerFactory.getLogger(AvatarMoveTool.class);

    private final ApplicationEventPublisher eventPublisher;

    public AvatarMoveTool(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(value = "控制虚拟人在用户屏幕上的相对移动（以当前所在位置为原点(0,0)，传入目标坐标和移动时长）",
            searchBehavior = SearchBehavior.ALWAYS_VISIBLE)
    public String moveAvatar(@P("相对当前 X 方向的位移像素（可正可负，范围建议 -1000 ~ 1000）") int targetX,
                             @P("相对当前 Y 方向的位移像素（可正可负，范围建议 -1000 ~ 1000）") int targetY,
                             @P("完成移动所需的毫秒数（范围 100 ~ 10000）") int durationMs,
                             InvocationParameters invocationParameters) {
        InvocationParametersWrapper wrapper = InvocationParametersWrapper.create(invocationParameters);
        String targetUserId = wrapper.getUserId();
        if (targetUserId == null || targetUserId.isBlank()) {
            return "移动失败：未指定用户Id";
        }
        if (durationMs <= 0) {
            return "移动失败：时长必须大于 0 毫秒";
        }
        try {
            eventPublisher.publishEvent(AvatarEvent.move(targetUserId, targetX, targetY, durationMs));
        } catch (Exception e) {
            logger.error("发布虚拟人移动事件失败 userId={} err={}", targetUserId, e.getMessage(), e);
            return "移动失败";
        }
        return "已向用户发送虚拟人移动事件";
    }
}
