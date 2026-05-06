package com.agent.hopaw.task;

import com.agent.hopaw.mapper.AgentMapper;
import com.agent.hopaw.model.Agent;
import com.agent.hopaw.model.ScheduledTask;
import com.agent.hopaw.service.AiModelService;
import com.agent.hopaw.tools.AgentTool;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.UserMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class AgentTaskHandler implements TaskHandler {
    private static final Logger logger = LoggerFactory.getLogger(AgentTaskHandler.class);
    private final AiModelService aiModelService;
    private final AgentMapper agentMapper;
    private final List<AgentTool> allTools;
    public AgentTaskHandler(AiModelService aiModelService, AgentMapper agentMapper,@Lazy List<AgentTool> allTools) {
        this.aiModelService = aiModelService;
        this.agentMapper = agentMapper;
        this.allTools = allTools;
    }


    @Override
    public String getType() {
        return "agentTask";
    }
    public interface AgentTaskAssistant {
        @UserMessage("{{content}}")
        String chat(String content);
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
        String identity = task.getIdentity();
        if(identity != null && !identity.isEmpty() && task.getDescription() != null && !task.getDescription().isEmpty()){
            Long agentId = Long.parseLong(identity);
            Agent agent =    agentMapper.findById(agentId);
            ChatModel chatModel = aiModelService.createChatModel(agent.getAiModelId(), agent.getEnableThinking());
            List<String> selectTools = parseToolNames(agent.getTools());

            List<AgentTool> selectedTools = allTools.stream()
                    .filter(t -> {
                        return selectTools.contains(t.getName()) && !"agentTaskTool".equals(t.getName());
                    })
                    .collect(Collectors.toList());

            AgentTaskAssistant assistant = AiServices.builder(AgentTaskAssistant.class)
                    .chatModel(chatModel)
                    .systemMessageProvider(chatMemoryId -> "这是一个定时执行的任务，你根据任务描述认真执行任务")
                    .tools(selectedTools.toArray())
                    .maxSequentialToolsInvocations(agent.getMaxToolInvocations())
                    .build();
            String result = assistant.chat(task.getDescription());
            logger.info("定时任务执行结果 [{}] - {}: {}", task.getId(), task.getTaskName(), result);
            }
        } catch (Exception e) {
            logger.error("定时任务执行失败 [{}] - {}: {}", task.getId(), task.getTaskName(), task.getDescription(), e);
        }
    }
}
