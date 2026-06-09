package com.agent.hopaw.biz.task;

import com.agent.hopaw.infra.constant.AiModelCallSourceEnum;
import com.agent.hopaw.infra.model.entity.Agent;
import com.agent.hopaw.infra.model.entity.ScheduledTask;
import com.agent.hopaw.infra.service.IAgentService;
import com.agent.hopaw.infra.service.IAiModelService;
import com.agent.hopaw.infra.service.IChatModelListenerProvider;
import com.agent.hopaw.infra.task.TaskHandler;
import com.agent.hopaw.infra.tool.AgentTool;
import com.agent.hopaw.infra.util.InvocationParametersWrapper;
import com.agent.hopaw.infra.util.UuidUtil;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.UserMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class AgentTaskHandler implements TaskHandler {
    private static final Logger logger = LoggerFactory.getLogger(AgentTaskHandler.class);
    private final IAiModelService aiModelService;
    private final IAgentService agentService;
    private final List<AgentTool> allTools;
    private final IChatModelListenerProvider chatModelListenerProvider;
    public AgentTaskHandler(IAiModelService aiModelService, IAgentService agentService, @Lazy List<AgentTool> allTools, IChatModelListenerProvider chatModelListenerProvider) {
        this.aiModelService = aiModelService;
        this.agentService = agentService;
        this.allTools = allTools;
        this.chatModelListenerProvider = chatModelListenerProvider;
    }


    @Override
    public String getType() {
        return "agentTask";
    }
    public interface AgentTaskAssistant {
        String chat(@dev.langchain4j.service.UserMessage List<Content> contents,
                    ChatRequestParameters chatRequestParameters, InvocationParameters invocationParameters);
    }
    private List<String> parseToolNames(String toolsStr) {
        if (toolsStr == null || toolsStr.isEmpty()) {
            return new ArrayList<>();
        }
        return Arrays.asList(toolsStr.split(","));
    }
    @Override
    public void execute(ScheduledTask task) {
        logger.info("定时任务执行 [{}] - {}: {}", task.getId(), task.getTaskName(), task.getDescription());

        try {
        String agentIdStr = task.getAgentId();
        if(agentIdStr != null && !agentIdStr.isEmpty() && task.getDescription() != null && !task.getDescription().isEmpty()){
            Long agentId = Long.parseLong(agentIdStr);
            Agent agent = agentService.getAgentById(agentId);
            ChatModelListener chatModelListener = chatModelListenerProvider.getChatModelListener(AiModelCallSourceEnum.AgentTask, task.getSessionId(), task.getUserId(), agentId);
            ChatModel chatModel = aiModelService.createChatModel(agent.getAiModelId(), agent.getEnableThinking(),chatModelListener);
            List<String> selectTools = parseToolNames(agent.getTools());

            List<AgentTool> selectedTools;
            if (Boolean.TRUE.equals(agent.getEnableAllTools())) {
                selectedTools = allTools.stream()
                        .filter(t -> !"agentTaskTool".equals(t.getName()))
                        .collect(Collectors.toList());
            } else {
                selectedTools = allTools.stream()
                        .filter(t -> {
                            return selectTools.contains(t.getName()) && !"agentTaskTool".equals(t.getName());
                        })
                        .collect(Collectors.toList());
            }
            Function<Object, String> systemMessageProvider = x -> {
                String systemMessage = "现在你要执行一个用户给定的任务，请你根据任务描述认真执行任务，如果任务中出现了指定时间执行或者定时执行的描述请忽略，直接执行要做的事情即可，有疑问可以尝试查询用户记忆，需要调用工具就调用工具。"
                        +"本次任务任务编号："+task.getId()+"，本次任务的表达式："+task.getCronExpression()+"，判断如果是一次性任务处理完后请删除任务。";
                return systemMessage;
            };
            InvocationParametersWrapper invocationParametersWrapper = InvocationParametersWrapper.create()
            .setUserId(task.getUserId())
            .setAgentId(Long.parseLong(agentIdStr))
            .setRequestId(UuidUtil.generateSimpleUUID())
            .setSessionId(task.getSessionId());
            AgentTaskAssistant assistant = AiServices.builder(AgentTaskAssistant.class)
                    .chatModel(chatModel)
                    .systemMessageProvider(systemMessageProvider)
                    .tools(selectedTools.toArray())
                    .maxSequentialToolsInvocations(agent.getMaxToolInvocations())
                    .build();
            ChatRequestParameters chatRequestParameters=ChatRequestParameters.builder()
                    .temperature(0.1)
                    .build();
            String result = assistant.chat(List.of(new TextContent(task.getDescription())), chatRequestParameters, invocationParametersWrapper.getParameters());
            logger.info("定时任务执行结果 [{}] - {}: {}", task.getId(), task.getTaskName(), result);
            }
        } catch (Exception e) {
            logger.error("定时任务执行失败 [{}] - {}: {}", task.getId(), task.getTaskName(), task.getDescription(), e);
        }
    }
}
