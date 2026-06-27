package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.executor.AgentExecutor;
import com.agent.hopaw.infra.executor.IAgentExecutor;
import com.agent.hopaw.infra.memory.IChatMemoryService;
import com.agent.hopaw.infra.model.dto.*;
import com.agent.hopaw.infra.model.entity.Agent;
import com.agent.hopaw.infra.tool.AgentTool;
import com.agent.hopaw.infra.tool.IAgentToolService;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AgentExecutorService implements IAgentExecutorService {
    private static final Logger logger = LoggerFactory.getLogger(AgentExecutorService.class);
    private final IAgentService agentService;
    private final AiModelService aiModelService;
    private final IChatMemoryService chatMemoryService;
    private final IAgentToolService agentToolService;
    private final EmbeddingModel embeddingModel;
    private final ISkillService ISkillService;
    private final IChatSessionService chatSessionService;
    private final IChatModelListenerProvider chatModelListenerProvider;
    private final ApplicationEventPublisher eventPublisher;
    private final IAvatarSettingsService avatarSettingsService;
    private final IMcpServerConfigService mcpServerConfigService;
    private final Map<String, IAgentExecutor> agentExecutors = new HashMap<>();

    public AgentExecutorService(IAgentService agentService, AiModelService aiModelService, IChatMemoryService chatMemoryService, IAgentToolService agentToolService, EmbeddingModel embeddingModel, ISkillService ISkillService, IChatSessionService chatSessionService, IChatModelListenerProvider chatModelListenerProvider, ApplicationEventPublisher eventPublisher, IAvatarSettingsService avatarSettingsService, IMcpServerConfigService mcpServerConfigService) {
        this.agentService = agentService;
        this.aiModelService = aiModelService;
        this.chatMemoryService = chatMemoryService;
        this.agentToolService = agentToolService;
        this.embeddingModel = embeddingModel;
        this.ISkillService = ISkillService;
        this.chatSessionService = chatSessionService;
        this.chatModelListenerProvider = chatModelListenerProvider;
        this.eventPublisher = eventPublisher;
        this.avatarSettingsService = avatarSettingsService;
        this.mcpServerConfigService = mcpServerConfigService;
    }

    @Override
    public void addToolStopHook(String sessionId, String callId, Consumer<String> hook) {
        IAgentExecutor IAgentExecutor = agentExecutors.get(sessionId);
        if (IAgentExecutor != null) {
            IAgentExecutor.addToolStopHook(callId, hook);
        }
    }

    @Override
    public void sendToolRunningContent(String sessionId, String callId, Object resultPartial) {
        IAgentExecutor IAgentExecutor = agentExecutors.get(sessionId);
        if (IAgentExecutor != null) {
            IAgentExecutor.sendToolRunningContent(callId, resultPartial);
        }
    }

    @Override
    public void toolApprovalComplete(String sessionId, String callId, Boolean allowed) {
        IAgentExecutor IAgentExecutor = agentExecutors.get(sessionId);
        if (IAgentExecutor != null) {
            IAgentExecutor.toolApprovalComplete(callId, allowed);
        }
    }

    @Override
    public void stopTool(String sessionId, String callId) {
        IAgentExecutor IAgentExecutor = agentExecutors.get(sessionId);
        if (IAgentExecutor != null) {
            IAgentExecutor.stopTool(callId);
        }
    }

    @Override
    public boolean toolIsCancelled(String sessionId, String callId) {
        IAgentExecutor IAgentExecutor = agentExecutors.get(sessionId);
        if (IAgentExecutor != null) {
            return IAgentExecutor.toolIsCancelled(callId);
        }
        return false;
    }

    @Override
    public void clearAndStopAgentExecutorByAiModel(Long aiModelId) {
        List<IAgentExecutor> list = agentExecutors.values().stream().collect(Collectors.toList());
        for (IAgentExecutor agentExecutor : list) {
            if (agentExecutor.getAiModelId() != null && agentExecutor.getAiModelId().equals(aiModelId)) {
                stopAndRemoveAgentExecutor(agentExecutor.getSessionId());
            }
        }
    }

    @Override
    public void stopAgentExecutor(String sessionId) {
        IAgentExecutor IAgentExecutor = agentExecutors.get(sessionId);
        if (IAgentExecutor != null) {
            IAgentExecutor.stop();
        }
    }

    @Override
    public void stopAndRemoveAgentExecutor(String sessionId) {
        stopAgentExecutor(sessionId);
        agentExecutors.remove(sessionId);
    }

    @Override
    public boolean isAgentExecutorRunning(String sessionId) {
        IAgentExecutor IAgentExecutor = agentExecutors.get(sessionId);
        return IAgentExecutor != null && IAgentExecutor.running();
    }

    @Override
    public IAgentExecutor getAgentExecutor(String sessionId) {
        return agentExecutors.get(sessionId);
    }

    @Override
    public IAgentExecutor createAgentExecutor(UserRequest userRequest) {
        Agent agent = userRequest.getAgentId() != null ? agentService.getAgentById(userRequest.getAgentId()) : null;
        if (agent == null) {
            throw new RuntimeException("智能体不存在");
        }
        if (userRequest.getAiModelId() == null) {
            throw new RuntimeException("智能体没有设置AI模型");
        }
        AvatarSettings avatarSettings = avatarSettingsService.getSettings(userRequest.getUserId(), agent.getId());
        List<String> selectedToolNames = parseToolNames(agent.getTools());
        List<ToolSetInfo> selectedTools;
        if (Boolean.TRUE.equals(agent.getEnableAllTools())) {
            selectedTools = agentToolService.getToolSets();
        } else {
            if(!avatarSettings.isDisabled() && avatarSettings.getPersonaSetting() != null && !avatarSettings.getPersonaSetting().isEmpty()){
               if(!selectedToolNames.contains(IAvatarSettingsService.TOOL_NAME)){
                   selectedToolNames.add(IAvatarSettingsService.TOOL_NAME);
               }
            }
            selectedTools = agentToolService.getToolSets().stream()
                    .filter(t -> selectedToolNames.contains(t.getName()))
                    .collect(Collectors.toList());

        }
        AgentExecutorParams agentExecutorParams = new AgentExecutorParams();
        agentExecutorParams.setSessionId(userRequest.getSessionId());
        agentExecutorParams.setAgentId(agent.getId());
        agentExecutorParams.setUserId(userRequest.getUserId());
        agentExecutorParams.setAiModelId(userRequest.getAiModelId());
        agentExecutorParams.setMaxMemoryRecords(agent.getMaxMemoryRecords() != null ? agent.getMaxMemoryRecords() : 10);
        agentExecutorParams.setMaxToolInvocations(agent.getMaxToolInvocations() != null ? agent.getMaxToolInvocations() : 3);
        agentExecutorParams.setEnableThinking(userRequest.getEnableThinking());
        agentExecutorParams.setVectorToolSearch(agent.getVectorToolSearch() != null ? agent.getVectorToolSearch() : false);
        agentExecutorParams.setVectorToolSearchMaxResults(agent.getVectorToolSearchMaxResults() != null ? agent.getVectorToolSearchMaxResults() : 5);
        agentExecutorParams.setSkillNames(userRequest.getSkillNames());
        agentExecutorParams.setToolCallPermission(userRequest.getToolCallPermission());
        agentExecutorParams.setToolSets(selectedTools);
        agentExecutorParams.setContents(buildContents(userRequest));
        // 加载已启用的 MCP 服务器配置
        agentExecutorParams.setMcpServerConfigs(mcpServerConfigService.findEnabled());


        Function<Long, String> systemMessageProvider = aId -> {
            return getSystemMessage(userRequest.getSessionId(), agent, userRequest.getUserId(), selectedTools, userRequest.getSkillNames(), avatarSettings);
        };
        AgentExecutor agentExecutor = new AgentExecutor(agentExecutorParams, chatMemoryService, embeddingModel, systemMessageProvider, aiModelService, chatModelListenerProvider, eventPublisher, chatSessionService);
        agentExecutors.put(userRequest.getSessionId(), agentExecutor);
        return agentExecutor;
    }

    /**
     * 构建发送给大模型的内容列表，将图片文件转为 Base64 的 ImageContent
     */
    private List<Content> buildContents(UserRequest userRequest) {
        List<Content> contents = new ArrayList<>();
        contents.add(new TextContent(userRequest.getMessage()));

        List<AttachmentFile> files = userRequest.getFiles();
        if (files != null && !files.isEmpty()) {
            for (AttachmentFile file : files) {
                if (!"image".equals(file.getType())) {
                    continue;
                }
                try {
                    String url = file.getUrl();
                    if (url == null || url.isEmpty()) continue;
                    // url 格式: /uploads/2025-01-01/xxx.png
                    String relativePath = url.startsWith("/") ? url.substring(1) : url;
                    Path filePath = Paths.get(System.getProperty("user.dir"), relativePath);
                    if (!Files.exists(filePath)) {
                        logger.warn("图片文件不存在: {}", filePath);
                        continue;
                    }
                    byte[] bytes = Files.readAllBytes(filePath);
                    String base64 = Base64.getEncoder().encodeToString(bytes);
                    String mimeType = getMimeType(filePath.toString());
                    contents.add(ImageContent.from(base64, mimeType));
                } catch (Exception e) {
                    logger.error("图片转 Base64 失败: {} -> {}", file.getUrl(), e.getMessage());
                }
            }
        }
        return contents;
    }

    private String getMimeType(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".bmp")) return "image/bmp";
        if (lower.endsWith(".webp")) return "image/webp";
        return "image/png";
    }

    private String getSystemMessage(String sessionId, Agent agent, String userId, List<ToolSetInfo> selectedTools, List<String> skillNames,AvatarSettings avatarSettings) {
        String systemMessage = "你是一个智能助手，名字叫" + agent.getName() + "," +
                "主要工作是" + agent.getDescription() + "," +
                "你的agentId是" + agent.getId() + "。\n" +
                "记忆工具是你的核心工具，需要回忆什么信息时，先去调用记忆工具看看有没相关可用信息。与用户相关获取用户画像记忆，与任务相关获取任务记录记忆，如果找不到可以搜索用户记忆试试\n" +
                "在遇到需要用户提供信息的时候，不要猜，记忆中没有就问用户。\n" +
                "在判断有需要调用工具就去调用，遇到危险操作，立刻停止操作，询问用户。\n" +
                "你只能使用用户提供的工具，绝对不能调用不存在的工具。更不能编造工具。\n";
        if(!avatarSettings.isDisabled() && avatarSettings.getPersonaSetting() != null && !avatarSettings.getPersonaSetting().isEmpty()){
            systemMessage += "你可以控制一个虚拟人和用户交互，人物的设定是：" + avatarSettings.getPersonaSetting() + "\n";
        }
        if (agent.getVectorToolSearch() != null && agent.getVectorToolSearch() && selectedTools != null && !selectedTools.isEmpty()) {
            systemMessage += "当需要[" + getToolKeywords(selectedTools) + "]这些能力时，先使用"+ AgentTool.TOOL_SEARCH_TOOL_NAME +"搜一下对应关键词，拿到工具详情再做决定使用。\n";
        }
        if (skillNames != null && !skillNames.isEmpty()) {
            String skillContext = buildSkillContext(skillNames);
            systemMessage += skillContext;
        }
        return systemMessage;
    }

    private String buildSkillContext(List<String> skillNames) {
        StringBuilder sb = new StringBuilder();
        sb.append("你将使用以下技能完成任务，请严格遵循技能中定义的指令：\n\n");
        for (String name : skillNames) {
            SkillInfo skill = ISkillService.getSkill(name);
            if (skill == null || skill.getContent() == null) {
                continue;
            }
            String content = skill.getContent().trim();
            sb.append("--- 技能: ").append(name).append(" ---\n");
            sb.append(content);
            if (!content.endsWith("\n")) {
                sb.append("\n");
            }
            sb.append("--- 结束 ---\n\n");
        }
        return sb.toString();
    }

    private String getToolKeywords(List<ToolSetInfo> selectedTools) {
        return selectedTools.stream().map(ToolSetInfo::getKeyword).collect(Collectors.joining(","));
    }

    private List<String> parseToolNames(String toolsStr) {
        if (toolsStr == null || toolsStr.isEmpty()) {
            return new ArrayList<>();
        }
        return Arrays.stream(toolsStr.split(",")).collect(Collectors.toList());
    }
}
