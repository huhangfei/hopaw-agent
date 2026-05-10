package com.agent.hopaw.task;

import com.agent.hopaw.constant.AiModelCallSourceEnum;
import com.agent.hopaw.mapper.AgentMapper;
import com.agent.hopaw.model.Agent;
import com.agent.hopaw.model.ScheduledTask;
import com.agent.hopaw.service.AiModelService;
import com.agent.hopaw.service.LangChain4jMonitor;
import com.agent.hopaw.service.TokenUsageService;
import com.agent.hopaw.tools.AgentTool;
import com.agent.hopaw.util.InvocationParametersWrapper;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.model.chat.ChatModel;
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
import java.util.stream.Collectors;

@Component
public class AgentTaskHandler implements TaskHandler {
    private static final Logger logger = LoggerFactory.getLogger(AgentTaskHandler.class);
    private final AiModelService aiModelService;
    private final AgentMapper agentMapper;
    private final List<AgentTool> allTools;
    private final TokenUsageService tokenUsageService;
    public AgentTaskHandler(AiModelService aiModelService, AgentMapper agentMapper, @Lazy List<AgentTool> allTools, TokenUsageService tokenUsageService) {
        this.aiModelService = aiModelService;
        this.agentMapper = agentMapper;
        this.allTools = allTools;
        this.tokenUsageService = tokenUsageService;
    }


    @Override
    public String getType() {
        return "agentTask";
    }
    public interface AgentTaskAssistant {
        @UserMessage("{{content}}")
        String chat(String content,
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
            Agent agent =agentMapper.findById(agentId);
            LangChain4jMonitor langChain4jMonitor = new LangChain4jMonitor(AiModelCallSourceEnum.AgentTask).setAgentId(agentId).setUserId(task.getUserId()).setTokenUsageService(tokenUsageService);
            ChatModel chatModel = aiModelService.createChatModel(agent.getAiModelId(), agent.getEnableThinking(),langChain4jMonitor);
            List<String> selectTools = parseToolNames(agent.getTools());

            List<AgentTool> selectedTools = allTools.stream()
                    .filter(t -> {
                        return selectTools.contains(t.getName()) && !"agentTaskTool".equals(t.getName());
                    })
                    .collect(Collectors.toList());
            InvocationParametersWrapper invocationParametersWrapper = InvocationParametersWrapper.create();
            invocationParametersWrapper.setUserId(task.getUserId());
            invocationParametersWrapper.setAgentId(Long.parseLong(agentIdStr));
            invocationParametersWrapper.setRequestId(UUID.randomUUID().toString());

            AgentTaskAssistant assistant = AiServices.builder(AgentTaskAssistant.class)
                    .chatModel(chatModel)
                    .systemMessageProvider(chatMemoryId -> "这是一个定时执行的任务，你根据任务描述认真执行任务")
                    .tools(selectedTools.toArray())
                    .maxSequentialToolsInvocations(agent.getMaxToolInvocations())
                    .build();
            ChatRequestParameters chatRequestParameters=ChatRequestParameters.builder()
                    .temperature(0.1)
                    .build();
            String result = assistant.chat(task.getDescription(), chatRequestParameters, invocationParametersWrapper.getParameters());
            logger.info("定时任务执行结果 [{}] - {}: {}", task.getId(), task.getTaskName(), result);
            }
        } catch (Exception e) {
            logger.error("定时任务执行失败 [{}] - {}: {}", task.getId(), task.getTaskName(), task.getDescription(), e);
        }
    }
}
