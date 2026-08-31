package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.constant.AgentExecutorBizTypeEnum;
import com.agent.hopaw.infra.constant.NotifyEventEnum;
import com.agent.hopaw.infra.constant.ProjectStatusEnum;
import com.agent.hopaw.infra.constant.TaskStatusEnum;
import com.agent.hopaw.infra.executor.IAgentExecutor;
import com.agent.hopaw.infra.mapper.ProjectMapper;
import com.agent.hopaw.infra.mapper.WorkflowTaskMapper;
import com.agent.hopaw.infra.model.dto.AgentExecutorParams;
import com.agent.hopaw.infra.model.dto.ProjectIterateResult;
import com.agent.hopaw.infra.model.dto.ToolSetInfo;
import com.agent.hopaw.infra.model.dto.UserChatRequest;
import com.agent.hopaw.infra.model.entity.Agent;
import com.agent.hopaw.infra.model.entity.Project;
import com.agent.hopaw.infra.model.entity.ProjectLog;
import com.agent.hopaw.infra.model.entity.WorkflowTask;
import com.agent.hopaw.infra.tool.IAgentToolService;
import com.agent.hopaw.infra.util.UuidUtil;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.TextContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 项目自动迭代服务：参考工作流任务执行器（WorkflowTaskService#createTaskExecutor）的实现方式，
 * 为项目创建"项目管理智能体"执行器，由定时任务周期性驱动：
 * 1. 分析项目目标与当前任务进度；
 * 2. 自动创建缺失的任务（关联本项目）；
 * 3. 自动审核（待启动→待执行）、验收（待验收→已完成）或驳回（待验收→失败）项目下的任务；
 * 4. 全部目标达成后将项目状态更新为已完成。
 */
@Service
public class ProjectIterateService implements IProjectIterateService {
    private static final Logger logger = LoggerFactory.getLogger(ProjectIterateService.class);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 执行超时时间（秒），与定时拉起的工作流任务保持一致 */
    private static final long EXECUTE_TIMEOUT_SECONDS = 1800;
    private static final String EXECUTE_USER_MESSAGE = "【项目自动迭代】请执行本轮项目迭代检查。\n请严格按照本次自动迭代要求执行。\n";
    /** 项目系统提示词注入的项目重点日志条数上限（更早历史通过项目工具查询） */
    private static final int IMPORTANT_LOGS_INJECT_LIMIT = 10;

    private final ProjectMapper projectMapper;
    private final WorkflowTaskMapper workflowTaskMapper;
    private final IAgentService agentService;
    private final IAgentExecutorService agentExecutorService;
    private final IAgentToolService agentToolService;
    private final IMcpServerConfigService mcpServerConfigService;
    private final IProjectService projectService;
    private final IProjectLogService projectLogService;
    private final INotificationService notificationService;
    private final com.agent.hopaw.infra.memory.ProjectMemoryService projectMemoryService;

    public ProjectIterateService(ProjectMapper projectMapper,
                                 WorkflowTaskMapper workflowTaskMapper,
                                 IAgentService agentService,
                                 IAgentExecutorService agentExecutorService,
                                 IAgentToolService agentToolService,
                                 IMcpServerConfigService mcpServerConfigService,
                                 IProjectService projectService,
                                 IProjectLogService projectLogService,
                                 INotificationService notificationService,
                                 com.agent.hopaw.infra.memory.ProjectMemoryService projectMemoryService) {
        this.projectMapper = projectMapper;
        this.workflowTaskMapper = workflowTaskMapper;
        this.agentService = agentService;
        this.agentExecutorService = agentExecutorService;
        this.agentToolService = agentToolService;
        this.mcpServerConfigService = mcpServerConfigService;
        this.projectService = projectService;
        this.projectLogService = projectLogService;
        this.notificationService = notificationService;
        this.projectMemoryService = projectMemoryService;
    }

    @Override
    public ProjectIterateResult executeProjectIterate(Long projectId){
        return executeProjectIterate(projectId, EXECUTE_USER_MESSAGE);
    }
    @Override
    public ProjectIterateResult executeProjectIterate(Long projectId, String userMessage) {
        // 未填写指令时回退默认迭代指令（与定时任务一致）
        if (userMessage == null || userMessage.trim().isEmpty()) {
            userMessage = EXECUTE_USER_MESSAGE;
        }
        Project project = projectMapper.findById(projectId);
        if (project == null) {
            logger.warn("项目自动迭代：项目不存在 id={}", projectId);
            return ProjectIterateResult.fail("项目不存在");
        }
        // 前置条件校验：进行中 + 已配置智能体 + 启用自动迭代（状态可能在等待期间被修改）
        if (!ProjectStatusEnum.IN_PROGRESS.getCode().equals(project.getStatus())) {
            return ProjectIterateResult.fail("项目当前状态为「" + resolveStatusLabel(project.getStatus()) + "」，仅进行中的项目可执行迭代");
        }
        if (project.getAgentId() == null) {
            return ProjectIterateResult.fail("项目未配置项目管理智能体");
        }
        if (!Boolean.TRUE.equals(project.getAutoIterate())) {
            return ProjectIterateResult.fail("项目未启用自动迭代");
        }
        Agent agent = agentService.getAgentById(project.getAgentId());
        if (agent == null) {
            logger.warn("项目自动迭代：项目管理智能体不存在 projectId={}, agentId={}", projectId, project.getAgentId());
            return ProjectIterateResult.fail("项目管理智能体不存在（编号：" + project.getAgentId() + "）");
        }
        if (agent.getAiModelId() == null) {
            return ProjectIterateResult.fail("项目管理智能体未配置AI模型");
        }

        // 会话：复用项目会话编号保留上下文；首次执行时生成并落库
        String sessionId = project.getSessionId();
        if (sessionId == null || sessionId.trim().isEmpty()) {
            sessionId = UuidUtil.generateSimpleUUID();
            projectService.updateSessionId(projectId, sessionId);
        }

        // 执行器运行中：跳过本轮，等待下一轮调度
        if (agentExecutorService.isAgentExecutorRunning(sessionId)) {
            logger.debug("项目自动迭代：项目管理智能体正在执行，跳过 id={} session={}", projectId, sessionId);
            return ProjectIterateResult.fail("项目管理智能体正在执行中，请等待本轮完成后重试");
        }

        IAgentExecutor executor = createProjectExecutor(project, agent, sessionId);
        List<WorkflowTask> tasks = workflowTaskMapper.findByProjectId(projectId);
        List<Content> contents = new ArrayList<>();
        contents.add(new TextContent(userMessage));
        boolean success = true;
        String failReason = null;
        try {
            logger.info("项目自动迭代开始: projectId={}, session={}", projectId, sessionId);
            executor.execute(contents, EXECUTE_TIMEOUT_SECONDS);
            logger.info("项目自动迭代完成: projectId={}", projectId);
        } catch (Exception e) {
            success = false;
            failReason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            logger.error("项目自动迭代失败: projectId={}", projectId, e);
        } finally {
            // 记录项目操作日志（智能体身份），便于用户在操作日志中追踪迭代节奏
            try {
                projectLogService.log(projectId, project.getUserId(), "智能体「" + agent.getName() + "」",
                        "auto_iterate", success ? "项目自动迭代已执行" : "项目自动迭代执行失败：" + failReason);
            } catch (Exception e) {
                logger.warn("项目自动迭代日志记录失败: projectId={}", projectId, e);
            }
            // 迭代失败：发送外部通知（钉钉群/邮件/飞书/Webhook，按项目通知配置）
            if (!success) {
                try {
                    notificationService.sendForProject(projectId, NotifyEventEnum.PROJECT_ITERATE_FAILED.getCode(),
                            NotifyEventEnum.PROJECT_ITERATE_FAILED.getDescription(),
                            "项目自动迭代执行失败：" + failReason);
                } catch (Exception e) {
                    logger.warn("项目迭代失败外部通知发送失败: projectId={}", projectId, e);
                }
            } else {
                // 迭代成功：发送外部通知（按项目通知配置）
                try {
                    notificationService.sendForProject(projectId, NotifyEventEnum.PROJECT_ITERATE_COMPLETED.getCode(),
                            NotifyEventEnum.PROJECT_ITERATE_COMPLETED.getDescription(),
                            "项目自动迭代执行完成");
                } catch (Exception e) {
                    logger.warn("项目迭代完成外部通知发送失败: projectId={}", projectId, e);
                }
            }
        }
        return success
                ? ProjectIterateResult.ok("项目自动迭代执行完成")
                : ProjectIterateResult.fail("项目自动迭代执行失败：" + failReason);
    }

    /** 项目状态码转中文描述（用于结果提示） */
    private String resolveStatusLabel(String statusCode) {
        ProjectStatusEnum statusEnum = ProjectStatusEnum.fromCode(statusCode);
        return statusEnum != null ? statusEnum.getDescription() : (statusCode != null ? statusCode : "未知");
    }

    @Override
    public void executeProjectChat(UserChatRequest userChatRequest) {
        String sessionId = userChatRequest.getSessionId();
        Project project = projectMapper.findBySessionId(sessionId);
        if (project == null) {
            throw new RuntimeException("会话未关联项目");
        }
        // 权限校验：仅项目所有者可在项目会话中发起消息
        if (userChatRequest.getUserId() != null && !userChatRequest.getUserId().equals(project.getUserId())) {
            throw new RuntimeException("无权操作该项目会话");
        }
        Agent agent = agentService.getAgentById(project.getAgentId());
        if (agent == null) {
            throw new RuntimeException("项目管理智能体不存在");
        }
        if (userChatRequest.getAiModelId() == null && agent.getAiModelId() == null) {
            throw new RuntimeException("项目管理智能体未配置AI模型");
        }
        // 用户消息驱动：复用项目会话编号保留上下文，重新唤起历史会话
        IAgentExecutor executor = createProjectExecutor(project, agent, sessionId,
                userChatRequest.getAiModelId(),
                userChatRequest.getEnableThinking(),
                userChatRequest.getSkillNames(),
                userChatRequest.getToolCallPermission());
        List<Content> contents = new ArrayList<>();
        contents.add(new TextContent(userChatRequest.getMessage()));
        logger.info("项目会话唤起开始: projectId={}, session={}", project.getId(), sessionId);
        executor.execute(contents, EXECUTE_TIMEOUT_SECONDS);
        logger.info("项目会话唤起完成: projectId={}", project.getId());
    }

    /**
     * 创建项目管理智能体执行器：参考工作流任务执行器（WorkflowTaskService#createTaskExecutor），
     * 构建项目管理专用系统提示词与工具集（强制注入 projectTool 与 workflowTaskTool）。
     * 自动迭代场景：模型/思考模式等参数取智能体配置。
     */
    private IAgentExecutor createProjectExecutor(Project project, Agent agent, String sessionId) {
        return createProjectExecutor(project, agent, sessionId, null, null, null, null);
    }

    /**
     * 创建项目管理智能体执行器（完整参数版）：
     * 自动迭代与用户会话唤起共用，用户会话唤起时优先使用请求中的模型/思考模式/技能/工具权限。
     */
    private IAgentExecutor createProjectExecutor(Project project, Agent agent, String sessionId,
                                                 Long aiModelId, Boolean enableThinking,
                                                 List<String> skillNames, String toolCallPermission) {
        String systemMessage = buildProjectSystemMessage(project, agent);

        // 构建工具集：智能体已配置工具 + 项目管理必需工具（projectTool / workflowTaskTool）
        List<String> selectedToolNames = parseToolNames(agent.getTools());
        if (!selectedToolNames.contains("projectTool")) {
            selectedToolNames.add("projectTool");
        }
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

        AgentExecutorParams params = new AgentExecutorParams();
        params.setSessionId(sessionId);
        params.setUserId(project.getUserId());
        // 用户会话唤起时优先使用请求参数，自动迭代时回退智能体配置
        params.setAiModelId(aiModelId != null ? aiModelId : agent.getAiModelId());
        params.setEnableThinking(enableThinking != null ? enableThinking : agent.getEnableThinking());
        params.setSkillNames(skillNames != null ? skillNames : new ArrayList<>());
        params.setToolCallPermission(toolCallPermission != null ? toolCallPermission : "auto");
        params.setAgentId(agent.getId());
        params.setMaxMemoryRecords(agent.getMaxMemoryRecords() != null ? agent.getMaxMemoryRecords() : 10);
        params.setMaxToolInvocations(agent.getMaxToolInvocations() != null ? agent.getMaxToolInvocations() : 3);
        params.setVectorToolSearch(agent.getVectorToolSearch() != null ? agent.getVectorToolSearch() : false);
        params.setVectorToolSearchMaxResults(agent.getVectorToolSearchMaxResults() != null ? agent.getVectorToolSearchMaxResults() : 5);
        params.setToolSets(selectedTools);
        params.setBizType(AgentExecutorBizTypeEnum.ProjectChat);
        params.setMcpServerConfigs(mcpServerConfigService.findEnabled());
        // 会话标题直接使用项目名称
        params.setSessionTitle(project.getName());
        params.setExtParams(new HashMap<>() {{
            put("projectId", project.getId());
        }});

        Function<Long, String> systemMessageProvider = aId -> systemMessage;
        // 调用公共创建方法（已内置旧执行器清理逻辑）
        return agentExecutorService.createAgentExecutor(params, systemMessageProvider);
    }

    /**
     * 构建项目管理智能体系统提示词：项目详情 + 重要项目日志 + 项目空间限制 + 工作流程指引
     */
    private String buildProjectSystemMessage(Project project, Agent agent) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个项目管理智能体，负责项目的整体规划与推进。\n");
        sb.append("智能体名称：").append(agent.getName()).append("\n");
        sb.append("智能体描述：").append(agent.getDescription()).append("\n");
        sb.append("\n--- 项目信息 ---\n");
        sb.append("项目编号：").append(project.getId()).append("\n");
        sb.append("项目名称：").append(project.getName()).append("\n");
        sb.append("项目状态：").append(statusText(project.getStatus())).append("\n");
        sb.append("项目描述：").append(project.getDescription() != null ? project.getDescription() : "无").append("\n");

        // 注入项目空间记忆：项目维度沉淀的整体记忆（目标、进展、关键决策、经验教训）
        try {
            String projectMemory = projectMemoryService.getProjectMemoryContent(project.getId());
            if (projectMemory != null && !projectMemory.isBlank()) {
                sb.append("\n--- 项目记忆 ---\n");
                sb.append("以下是项目沉淀的整体记忆，规划任务与推进项目时请充分参考：\n");
                sb.append(projectMemory).append("\n");
            }
        } catch (Exception e) {
            logger.warn("注入项目记忆失败，项目[{}]: {}", project.getId(), e.getMessage());
        }

        // 注入项目重点日志，提供历史关键结论（含各任务总结评论）——仅最近10条，避免提示词过长
        List<ProjectLog> importantLogs = projectLogService.getImportantLogsByProjectId(project.getId());
        if (importantLogs != null && !importantLogs.isEmpty()) {
            int logTotal = importantLogs.size();
            int logFrom = Math.max(logTotal - IMPORTANT_LOGS_INJECT_LIMIT, 0);
            List<ProjectLog> recentLogs = importantLogs.subList(logFrom, logTotal);
            sb.append("\n--- 项目重点日志（最近").append(recentLogs.size()).append("条） ---\n");
            for (ProjectLog log : recentLogs) {
                sb.append("[").append(log.getCreateTime() != null ? log.getCreateTime().format(TIME_FMT) : "").append("] ")
                        .append(log.getDetail() != null ? log.getDetail() : "")
                        .append("\n");
            }
            if (logTotal > recentLogs.size()) {
                sb.append("（以上仅展示最近").append(recentLogs.size())
                        .append("条，项目共有").append(logTotal)
                        .append("条重点日志，如需了解更早的历史关键结论，请使用项目工具查询项目日志。）\n");
            }
        }

        // 项目空间目录限制（与任务执行智能体一致）
        try {
            String absSpacePath = projectService.getProjectSpaceAbsolutePath(project.getId(), project.getUserId());
            if (absSpacePath != null && !absSpacePath.isEmpty()) {
                sb.append("\n--- 项目空间限制 ---\n");
                sb.append("项目空间目录为：").append(absSpacePath).append("\n");
                sb.append("重要约束：所有文件操作（创建、读取、修改、删除等）仅限于在上述项目空间目录及其子目录内进行。\n");
            }
        } catch (Exception e) {
            logger.warn("注入项目空间限制失败，项目[{}]: {}", project.getId(), e.getMessage());
        }

        sb.append("\n--- 工具使用指引 ---\n");
        sb.append("1. 查询当前项目 / 查询项目详情：回顾项目信息与历史关键日志。\n");
        sb.append("2. 查询任务列表 / 查询任务详情：了解项目下任务的状态与执行情况（可按项目过滤）。\n");
        sb.append("3. 添加工作流任务：为项目创建缺失的任务；创建的任务必须关联本项目（项目编号 ").append(project.getId()).append("），并指定合适的执行智能体。\n");
        sb.append("4. 审核工作流任务：将待启动的任务审核为待执行，由系统自动调度执行智能体处理。\n");
        sb.append("5. 验收工作流任务：检查待验收任务的执行结果，结果符合要求则验收为已完成；不符合要求则驳回（注明驳回原因），由系统安排重做。\n");
        sb.append("6. 保存项目：更新项目信息或项目状态（需传入项目名称与项目编号 ").append(project.getId()).append("）。\n");
        sb.append("7. 记忆工具是你的核心工具，需要回忆什么信息时，先去调用记忆工具看看有没相关可用信息。\n");
        sb.append("你只能使用用户提供的工具，绝对不能调用不存在的工具，遇到危险操作立刻停止。\n");

        sb.append("\n--- 常规项目迭代工作流程 ---\n");
        sb.append("1. 分析项目目标与当前任务进度，判断项目目标是否需要拆解为更多任务；\n");
        sb.append("2. 对待启动的任务进行审核：确认任务内容合理后审核通过，使其进入执行；\n");
        sb.append("3. 对待验收的任务进行检查：验收合格则通过，不合格则驳回并说明原因；\n");
        sb.append("4. 对失败的任务视情况驳回重做或调整任务内容；\n");
        sb.append("5. 如果项目所有目标都已达成（任务全部完成且无待处理事项），调用保存项目工具将项目状态更新为 completed（已完成）；\n");
        sb.append("6. 每轮处理后给出简明的本轮迭代总结。\n");

        // 迭代要求提示词：用户配置的额外迭代要求（自动迭代与手动下发指令均生效）
        if (project.getIteratePrompt() != null && !project.getIteratePrompt().trim().isEmpty()) {
            sb.append("\n--- 本次迭代要求 ---\n");
            sb.append(project.getIteratePrompt().trim()).append("\n");
        }

        return sb.toString();
    }


    private List<String> parseToolNames(String toolsStr) {
        if (toolsStr == null || toolsStr.isEmpty()) {
            return new ArrayList<>();
        }
        return Arrays.stream(toolsStr.split(",")).collect(Collectors.toList());
    }

    /** 项目状态码转中文描述 */
    private String statusText(String code) {
        ProjectStatusEnum e = ProjectStatusEnum.fromCode(code);
        return e != null ? e.getDescription() : (code != null ? code : "未知");
    }

    /** 任务状态码转中文描述 */
    private String taskStatusText(String code) {
        TaskStatusEnum e = TaskStatusEnum.fromCode(code);
        return e != null ? e.getDescription() : (code != null ? code : "未知");
    }
}