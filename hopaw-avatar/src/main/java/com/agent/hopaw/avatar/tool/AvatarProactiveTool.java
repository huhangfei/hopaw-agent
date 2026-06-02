package com.agent.hopaw.avatar.tool;

import com.agent.hopaw.avatar.service.AvatarProactiveMessageService;
import com.agent.hopaw.infra.tool.AgentTool;
import com.agent.hopaw.infra.tool.ToolSecurityLevel;
import com.agent.hopaw.infra.util.InvocationParametersWrapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.invocation.InvocationParameters;
import org.springframework.stereotype.Component;

@Component
public class AvatarProactiveTool {

    private final AvatarProactiveMessageService proactiveMessageService;

    public AvatarProactiveTool(AvatarProactiveMessageService proactiveMessageService) {
        this.proactiveMessageService = proactiveMessageService;
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
        boolean sent = proactiveMessageService.sendProactiveMessage(targetUserId, message.trim());
        return sent ? "已向用户发送虚拟人主动消息" : "发送失败";
    }
}
