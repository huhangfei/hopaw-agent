package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.executor.AgentExecutor;
import com.agent.hopaw.infra.executor.IAgentExecutor;
import com.agent.hopaw.infra.memory.IChatMemoryService;
import com.agent.hopaw.infra.memory.ILongTermMemoryService;
import com.agent.hopaw.infra.model.dto.*;
import com.agent.hopaw.infra.model.entity.Agent;
import com.agent.hopaw.infra.model.entity.TaskComment;
import com.agent.hopaw.infra.model.entity.WorkflowTask;
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
    private final ILongTermMemoryService longTermMemoryService;
    private final ISysConfigService sysConfigService;
    private final Map<String, IAgentExecutor> agentExecutors = new HashMap<>();

    public AgentExecutorService(IAgentService agentService, AiModelService aiModelService, IChatMemoryService chatMemoryService, IAgentToolService agentToolService, EmbeddingModel embeddingModel, ISkillService ISkillService, IChatSessionService chatSessionService, IChatModelListenerProvider chatModelListenerProvider, ApplicationEventPublisher eventPublisher, IAvatarSettingsService avatarSettingsService, IMcpServerConfigService mcpServerConfigService, ILongTermMemoryService longTermMemoryService, ISysConfigService sysConfigService) {
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
        this.longTermMemoryService = longTermMemoryService;
        this.sysConfigService = sysConfigService;
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
    public IAgentExecutor createChatAgentExecutor(UserChatRequest userChatRequest) {
        Agent agent = userChatRequest.getAgentId() != null ? agentService.getAgentById(userChatRequest.getAgentId()) : null;
        if (agent == null) {
            throw new RuntimeException("智能体不存在");
        }
        if (userChatRequest.getAiModelId() == null) {
            throw new RuntimeException("智能体没有设置AI模型");
        }
        AvatarSettings avatarSettings = avatarSettingsService.getSettings(userChatRequest.getUserId(), agent.getId());
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
        agentExecutorParams.setSessionId(userChatRequest.getSessionId());
        agentExecutorParams.setAgentId(agent.getId());
        agentExecutorParams.setUserId(userChatRequest.getUserId());
        agentExecutorParams.setAiModelId(userChatRequest.getAiModelId());
        agentExecutorParams.setMaxMemoryRecords(agent.getMaxMemoryRecords() != null ? agent.getMaxMemoryRecords() : 10);
        agentExecutorParams.setMaxToolInvocations(agent.getMaxToolInvocations() != null ? agent.getMaxToolInvocations() : 3);
        agentExecutorParams.setEnableThinking(userChatRequest.getEnableThinking());
        agentExecutorParams.setVectorToolSearch(agent.getVectorToolSearch() != null ? agent.getVectorToolSearch() : false);
        agentExecutorParams.setVectorToolSearchMaxResults(agent.getVectorToolSearchMaxResults() != null ? agent.getVectorToolSearchMaxResults() : 5);
        agentExecutorParams.setSkillNames(userChatRequest.getSkillNames());
        agentExecutorParams.setToolCallPermission(userChatRequest.getToolCallPermission());
        agentExecutorParams.setToolSets(selectedTools);
        agentExecutorParams.setContents(buildContents(userChatRequest));
        // 加载已启用的 MCP 服务器配置
        agentExecutorParams.setMcpServerConfigs(mcpServerConfigService.findEnabled());


        Function<Long, String> systemMessageProvider = aId -> {
            return getChatSystemMessage(userChatRequest.getSessionId(), agent, userChatRequest.getUserId(), selectedTools, userChatRequest.getSkillNames(), avatarSettings);
        };
        AgentExecutor agentExecutor = new AgentExecutor(agentExecutorParams, chatMemoryService, embeddingModel, systemMessageProvider, aiModelService, chatModelListenerProvider, eventPublisher, chatSessionService);
        agentExecutors.put(userChatRequest.getSessionId(), agentExecutor);
        return agentExecutor;
    }

    @Override
    public IAgentExecutor createTaskExecutor(WorkflowTask task, Agent agent, List<TaskComment> comments, String existingSessionId) {
        // 1. 确定会话编号：打回重做时复用已关联会话，否则新建
        String sessionId;
        if (existingSessionId != null && !existingSessionId.isEmpty()) {
            // 复用前先清理可能残留的旧执行器，避免覆盖正在运行的实例
            stopAndRemoveAgentExecutor(existingSessionId);
            sessionId = existingSessionId;
        } else {
            sessionId = UUID.randomUUID().toString();
        }

        // 2. 构建任务专用系统提示词
        String systemMessage = "你是一个任务执行智能体。\n" +
                "智能体名称：" + agent.getName() + "\n" +
                "智能体描述：" + agent.getDescription() + "\n" +
                "任务编号：" + task.getId() + "\n" +
                "任务名称：" + task.getTitle() + "\n" +
                "\n请根据任务内容执行，完成后给出执行结果摘要。\n" +
                "记忆工具是你的核心工具，需要回忆什么信息时，先去调用记忆工具看看有没相关可用信息。\n" +
                "在判断有需要调用工具就去调用，遇到危险操作，立刻停止操作。\n" +
                "你只能使用用户提供的工具，绝对不能调用不存在的工具。\n" +
                "\n--- 任务评论工具使用指引 ---\n" +
                "你可以通过 workflowTaskTool 工具来操作当前任务：\n" +
                "1. 添加任务评论（addTaskComment）：用于记录任务处理的关键细节、阶段性进展、重要决策，便于用户追踪处理过程；当你需要向用户确认信息或遇到需要用户决策的问题时，也可以通过添加评论的方式提出问题，用户会在任务评论中回复你。\n" +
                "2. 查询当前任务（queryCurrentTask）：当需要回顾任务内容、查看用户是否有新的评论回复时调用。\n" +
                "建议在执行关键步骤后通过评论记录处理细节，遇到不确定的问题时通过评论向用户提问而非自行猜测。\n";

        // 3. 构建工具集（任务执行场景强制注入 workflowTaskTool，确保智能体可记录评论）
        List<String> selectedToolNames = parseToolNames(agent.getTools());
        if (!selectedToolNames.contains("workflowTaskTool")) {
            selectedToolNames.add("workflowTaskTool");
        }
        List<ToolSetInfo> selectedTools;
        if (Boolean.TRUE.equals(agent.getEnableAllTools())) {
            selectedTools = agentToolService.getToolSets();
        } else {
            selectedTools = agentToolService.getToolSets().stream()
                    .filter(t -> selectedToolNames.contains(t.getName()))
                    .collect(Collectors.toList());
        }

        // 4. 构建内容（包含评论历史，区分评论者身份）
        List<Content> contents = new ArrayList<>();
        StringBuilder taskContent = new StringBuilder();
        taskContent.append(task.getContent() != null ? task.getContent() : "");
        if (comments != null && !comments.isEmpty()) {
            taskContent.append("\n\n--- 评论历史 ---\n");
            for (TaskComment comment : comments) {
                // 区分评论者身份：agent=智能体，其他（含 null 旧数据）按用户处理
                String role = "agent".equals(comment.getCommenterType()) ? "智能体" : "用户";
                String commenterId = comment.getCommenterId() != null ? comment.getCommenterId() : "";
                taskContent.append(String.format("[%s][%s:%s] %s\n",
                        comment.getCreateTime() != null ? comment.getCreateTime() : "",
                        role,
                        commenterId,
                        comment.getContent() != null ? comment.getContent() : ""));
            }
        }
        contents.add(new TextContent(taskContent.toString()));

        // 5. 构建 AgentExecutorParams
        AgentExecutorParams params = new AgentExecutorParams();
        params.setSessionId(sessionId);
        params.setAgentId(agent.getId());
        params.setUserId(task.getUserId());
        params.setAiModelId(agent.getAiModelId());
        params.setMaxMemoryRecords(agent.getMaxMemoryRecords() != null ? agent.getMaxMemoryRecords() : 10);
        params.setMaxToolInvocations(agent.getMaxToolInvocations() != null ? agent.getMaxToolInvocations() : 3);
        params.setEnableThinking(agent.getEnableThinking());
        params.setVectorToolSearch(agent.getVectorToolSearch() != null ? agent.getVectorToolSearch() : false);
        params.setVectorToolSearchMaxResults(agent.getVectorToolSearchMaxResults() != null ? agent.getVectorToolSearchMaxResults() : 5);
        params.setToolCallPermission("auto");
        params.setToolSets(selectedTools);
        params.setContents(contents);
        params.setMcpServerConfigs(mcpServerConfigService.findEnabled());

        // 6. systemMessageProvider
        Function<Long, String> systemMessageProvider = aId -> systemMessage;

        // 7. 创建执行器
        AgentExecutor agentExecutor = new AgentExecutor(params, chatMemoryService, embeddingModel, systemMessageProvider, aiModelService, chatModelListenerProvider, eventPublisher, chatSessionService);
        agentExecutors.put(sessionId, agentExecutor);
        return agentExecutor;
    }

    /**
     * 构建发送给大模型的内容列表，将图片文件转为 Base64 的 ImageContent
     */
    private List<Content> buildContents(UserChatRequest userChatRequest) {
        List<Content> contents = new ArrayList<>();
        contents.add(new TextContent(userChatRequest.getMessage()));

        List<AttachmentFile> files = userChatRequest.getFiles();
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

    /**
     * 聊天系统提示词
     * @param sessionId
     * @param agent
     * @param userId
     * @param selectedTools
     * @param skillNames
     * @param avatarSettings
     * @return
     */
    private String getChatSystemMessage(String sessionId, Agent agent, String userId, List<ToolSetInfo> selectedTools, List<String> skillNames, AvatarSettings avatarSettings) {
        String systemMessage = "你是一个智能助手，名字叫" + agent.getName() + "," +
                "主要工作是" + agent.getDescription() + "," +
                "你的agentId是" + agent.getId() + "。\n" +
                "记忆工具是你的核心工具，需要回忆什么信息时，先去调用记忆工具看看有没相关可用信息。用户画像记忆、任务记录记忆、过往的经验或总结都可以通过搜索用户记忆尝试查找。\n" +
                "在遇到需要用户提供信息的时候，不要猜，记忆中没有就问用户。\n" +
                "在判断有需要调用工具就去调用，遇到危险操作，立刻停止操作，询问用户。\n" +
                "你只能使用用户提供的工具，绝对不能调用不存在的工具。更不能编造工具。\n";

        // 根据设置决定是否注入用户画像 / 任务记录作为系统提示词上下文
        if (isPromptIncludeUserProfile() && userId != null && !userId.isEmpty()) {
            String profile = longTermMemoryService.queryUserProfileMemoryContent(userId);
            if (profile != null && !profile.isEmpty()) {
                systemMessage += "\n----用户画像----\n" + profile;
                logger.debug("系统提示词已注入用户画像（userId={}）", userId);
            }
        }
        if (isPromptIncludeTaskRecords() && userId != null && !userId.isEmpty()) {
            String taskRecords = longTermMemoryService.queryUserTaskRecordsMemoryContent(sessionId, userId, false);
            if (taskRecords != null && !taskRecords.isEmpty()) {
                systemMessage += "\n----近期任务记录----\n" + taskRecords;
                logger.debug("系统提示词已注入近期任务记录（userId={}）", userId);
            }
        }

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

    /**
     * 读取"提示词带入用户画像"设置（默认 true）
     */
    private boolean isPromptIncludeUserProfile() {
        return Boolean.parseBoolean(sysConfigService.getValueByKey("promptIncludeUserProfile", "true"));
    }

    /**
     * 读取"提示词带入近期任务记录"设置（默认 true）
     */
    private boolean isPromptIncludeTaskRecords() {
        return Boolean.parseBoolean(sysConfigService.getValueByKey("promptIncludeTaskRecords", "true"));
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
