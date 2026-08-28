package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.constant.AgentExecutorBizTypeEnum;
import com.agent.hopaw.infra.constant.AiModelCallSourceEnum;
import com.agent.hopaw.infra.executor.IAgentExecutor;
import com.agent.hopaw.infra.memory.ILongTermMemoryService;
import com.agent.hopaw.infra.model.dto.AgentExecutorParams;
import com.agent.hopaw.infra.model.dto.AttachmentFile;
import com.agent.hopaw.infra.model.dto.AvatarSettings;
import com.agent.hopaw.infra.model.dto.SkillInfo;
import com.agent.hopaw.infra.model.dto.ToolSetInfo;
import com.agent.hopaw.infra.model.dto.UserChatRequest;
import com.agent.hopaw.infra.model.entity.Agent;
import com.agent.hopaw.infra.model.entity.ChatSession;
import com.agent.hopaw.infra.tool.AgentTool;
import com.agent.hopaw.infra.tool.IAgentToolService;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 聊天业务服务：负责生成聊天场景的执行器参数和系统提示词，并调用公共创建方法
 */
@Service
public class ChatService implements IChatService {
    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);

    private final IAgentService agentService;
    private final IAgentToolService agentToolService;
    private final IAvatarSettingsService avatarSettingsService;
    private final ISkillService skillService;
    private final ILongTermMemoryService longTermMemoryService;
    private final ISysConfigService sysConfigService;
    private final IMcpServerConfigService mcpServerConfigService;
    private final IAgentExecutorService agentExecutorService;
    private final IWorkflowTaskService workflowTaskService;
    private final IChatSessionService chatSessionService;
    private final IProjectIterateService projectIterateService;

    public ChatService(IAgentService agentService, IAgentToolService agentToolService, IAvatarSettingsService avatarSettingsService, ISkillService skillService, ILongTermMemoryService longTermMemoryService, ISysConfigService sysConfigService, IMcpServerConfigService mcpServerConfigService, IAgentExecutorService agentExecutorService, IWorkflowTaskService workflowTaskService, IChatSessionService chatSessionService, IProjectIterateService projectIterateService) {
        this.agentService = agentService;
        this.agentToolService = agentToolService;
        this.avatarSettingsService = avatarSettingsService;
        this.skillService = skillService;
        this.longTermMemoryService = longTermMemoryService;
        this.sysConfigService = sysConfigService;
        this.mcpServerConfigService = mcpServerConfigService;
        this.agentExecutorService = agentExecutorService;
        this.workflowTaskService = workflowTaskService;
        this.chatSessionService = chatSessionService;
        this.projectIterateService = projectIterateService;
    }

    @Override
    public void handle(UserChatRequest userChatRequest) {
        String sessionBizType = resolveSessionBizType(userChatRequest);
        // 任务会话：走工作流任务执行（复用任务会话上下文）
        if (AiModelCallSourceEnum.WorkflowTaskChat.getValue().equals(sessionBizType)) {
            workflowTaskService.executeTask(userChatRequest);
            return;
        }
        // 项目会话：重新唤起历史项目会话，由项目管理智能体处理用户消息
        if (AiModelCallSourceEnum.ProjectChat.getValue().equals(sessionBizType)) {
            projectIterateService.executeProjectChat(userChatRequest);
            return;
        }
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
        agentExecutorParams.setUserId(userChatRequest.getUserId());
        agentExecutorParams.setAiModelId(userChatRequest.getAiModelId());
        agentExecutorParams.setEnableThinking(userChatRequest.getEnableThinking());
        agentExecutorParams.setSkillNames(userChatRequest.getSkillNames());
        agentExecutorParams.setToolCallPermission(userChatRequest.getToolCallPermission());
        agentExecutorParams.setAgentId(agent.getId());
        agentExecutorParams.setMaxMemoryRecords(agent.getMaxMemoryRecords() != null ? agent.getMaxMemoryRecords() : 10);
        agentExecutorParams.setMaxToolInvocations(agent.getMaxToolInvocations() != null ? agent.getMaxToolInvocations() : 3);
        agentExecutorParams.setVectorToolSearch(agent.getVectorToolSearch() != null ? agent.getVectorToolSearch() : false);
        agentExecutorParams.setVectorToolSearchMaxResults(agent.getVectorToolSearchMaxResults() != null ? agent.getVectorToolSearchMaxResults() : 5);
        agentExecutorParams.setToolSets(selectedTools);
        // 加载已启用的 MCP 服务器配置
        agentExecutorParams.setMcpServerConfigs(mcpServerConfigService.findEnabled());
        agentExecutorParams.setBizType(AgentExecutorBizTypeEnum.Chat);


        Function<Long, String> systemMessageProvider = aId -> {
            return getChatSystemMessage(userChatRequest.getSessionId(), agent, userChatRequest.getUserId(), selectedTools, userChatRequest.getSkillNames(), avatarSettings);
        };
        IAgentExecutor agentExecutor = agentExecutorService.createAgentExecutor(agentExecutorParams, systemMessageProvider);
        agentExecutor.execute(buildContents(userChatRequest));
    }

    /**
     * 解析会话业务类型：优先取请求显式传入的类型，否则按会话编号从会话表读取（来源在首次插入时确定）
     */
    private String resolveSessionBizType(UserChatRequest userChatRequest) {
        if (userChatRequest.getSessionBizType() != null) {
            return userChatRequest.getSessionBizType();
        }
        if (userChatRequest.getSessionId() != null) {
            ChatSession session = chatSessionService.getSessionBySessionId(userChatRequest.getSessionId());
            if (session != null) {
                return session.getBizType();
            }
        }
        return null;
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
            SkillInfo skill = skillService.getSkill(name);
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
