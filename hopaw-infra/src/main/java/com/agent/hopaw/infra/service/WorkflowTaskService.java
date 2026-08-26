package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.constant.AgentExecutorBizTypeEnum;
import com.agent.hopaw.infra.constant.TaskCommenterTypeEnum;
import com.agent.hopaw.infra.constant.TaskCommentTypeEnum;
import com.agent.hopaw.infra.constant.TaskStatusEnum;
import com.agent.hopaw.infra.executor.IAgentExecutor;
import com.agent.hopaw.infra.mapper.TaskSessionMapper;
import com.agent.hopaw.infra.mapper.WorkflowTaskMapper;
import com.agent.hopaw.infra.mapper.WorkflowTaskPreconditionMapper;
import com.agent.hopaw.infra.model.dto.AgentExecutorParams;
import com.agent.hopaw.infra.model.dto.ToolSetInfo;
import com.agent.hopaw.infra.model.dto.UserChatRequest;
import com.agent.hopaw.infra.model.entity.Agent;
import com.agent.hopaw.infra.model.entity.Project;
import com.agent.hopaw.infra.model.entity.ProjectLog;
import com.agent.hopaw.infra.model.entity.WorkflowTaskComment;
import com.agent.hopaw.infra.model.entity.WorkflowTaskPrecondition;
import com.agent.hopaw.infra.model.entity.TaskSession;
import com.agent.hopaw.infra.model.entity.WorkflowTask;
import com.agent.hopaw.infra.tool.IAgentToolService;
import com.agent.hopaw.infra.util.UuidUtil;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.TextContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class WorkflowTaskService implements IWorkflowTaskService {
    private static final Logger logger = LoggerFactory.getLogger(WorkflowTaskService.class);

    private final WorkflowTaskMapper workflowTaskMapper;
    private final WorkflowTaskPreconditionMapper preconditionMapper;
    private final TaskSessionMapper taskSessionMapper;
    private final IAgentExecutorService agentExecutorService;
    private final IChatSessionService chatSessionService;
    private final IAgentService agentService;
    private final IWorkflowTaskCommentService taskCommentService;
    private final IAgentToolService agentToolService;
    private final IMcpServerConfigService mcpServerConfigService;
    private final IProjectService projectService;
    private final IProjectLogService projectLogService;

    public WorkflowTaskService(WorkflowTaskMapper workflowTaskMapper,
                               WorkflowTaskPreconditionMapper preconditionMapper,
                               TaskSessionMapper taskSessionMapper,
                               IAgentExecutorService agentExecutorService,
                               IChatSessionService chatSessionService,
                               IAgentService agentService,
                               IWorkflowTaskCommentService taskCommentService,
                               IAgentToolService agentToolService,
                               IMcpServerConfigService mcpServerConfigService,
                               IProjectService projectService,
                               IProjectLogService projectLogService) {
        this.workflowTaskMapper = workflowTaskMapper;
        this.preconditionMapper = preconditionMapper;
        this.taskSessionMapper = taskSessionMapper;
        this.agentExecutorService = agentExecutorService;
        this.chatSessionService = chatSessionService;
        this.agentService = agentService;
        this.taskCommentService = taskCommentService;
        this.agentToolService = agentToolService;
        this.mcpServerConfigService = mcpServerConfigService;
        this.projectService = projectService;
        this.projectLogService = projectLogService;
    }

    @Override
    public WorkflowTask createTask(WorkflowTask task) {
        task.setStatus(TaskStatusEnum.PENDING.getCode());
        LocalDateTime now = LocalDateTime.now();
        task.setCreateTime(now);
        task.setUpdateTime(now);
        workflowTaskMapper.insert(task);
        savePreconditions(task.getId(), task.getPreconditions());
        return task;
    }

    @Override
    public WorkflowTask updateTask(WorkflowTask task, String userId) {
        WorkflowTask existing = workflowTaskMapper.findById(task.getId());
        if (existing == null) {
            throw new RuntimeException("任务不存在");
        }
        if (!userId.equals(existing.getUserId())) {
            throw new RuntimeException("无权修改该任务");
        }
        // 处理中的任务不允许编辑，避免执行中的指令被篡改
        if (TaskStatusEnum.PROCESSING.getCode().equals(existing.getStatus())) {
            throw new RuntimeException("处理中的任务不允许编辑");
        }
        existing.setTitle(task.getTitle());
        existing.setContent(task.getContent());
        if (task.getProjectId() != null) {
            existing.setProjectId(task.getProjectId());
        }
        if (task.getAgentId() != null) {
            existing.setAgentId(task.getAgentId());
        }
        if (task.getStartTime() != null) {
            existing.setStartTime(task.getStartTime());
        }
        if (task.getExecutionPeriod() != null) {
            existing.setExecutionPeriod(task.getExecutionPeriod());
        }
        existing.setUpdateTime(LocalDateTime.now());
        workflowTaskMapper.update(existing);
        savePreconditions(existing.getId(), task.getPreconditions());
        return existing;
    }

    @Override
    @Transactional
    public void deleteTask(Long id, String userId) {
        WorkflowTask existing = workflowTaskMapper.findById(id);
        if (existing == null) {
            throw new RuntimeException("任务不存在");
        }
        if (!userId.equals(existing.getUserId())) {
            throw new RuntimeException("无权删除该任务");
        }
        taskSessionMapper.deleteByTaskId(id);
        // 前置条件双向清理：该任务自己的配置 + 以该任务为前置的其他任务关联（避免删除任务导致其他任务永久阻塞）
        preconditionMapper.deleteByTaskId(id);
        preconditionMapper.deleteByPreTaskId(id);
        workflowTaskMapper.deleteById(id);
    }

    @Override
    public WorkflowTask getTask(Long id, String userId) {
        WorkflowTask task = workflowTaskMapper.findById(id);
        if (task == null) {
            return null;
        }
        if (!userId.equals(task.getUserId())) {
            return null;
        }
        task.setPreconditions(getPreconditions(id));
        return task;
    }

    @Override
    public WorkflowTask getTaskById(Long id) {
        return workflowTaskMapper.findById(id);
    }

    @Override
    public List<WorkflowTask> getTasksByStatus(String userId, String status) {
        return workflowTaskMapper.findByUserIdAndStatus(userId, status);
    }

    @Override
    public List<WorkflowTask> getTasksPage(String userId, String keyword, String status, Long projectId, Long agentId, int page, int size) {
        int offset = (page - 1) * size;
        return workflowTaskMapper.findByUserIdWithFilters(userId, keyword, status, projectId, agentId, offset, size);
    }

    @Override
    public int countTasks(String userId, String keyword, String status, Long projectId, Long agentId) {
        return workflowTaskMapper.countByUserIdWithFilters(userId, keyword, status, projectId, agentId);
    }

    @Override
    public void approveTask(Long id, String userId) {
        WorkflowTask existing = workflowTaskMapper.findById(id);
        if (existing == null) {
            throw new RuntimeException("任务不存在");
        }
        if (!userId.equals(existing.getUserId())) {
            throw new RuntimeException("无权操作该任务");
        }
        if (!TaskStatusEnum.PENDING.getCode().equals(existing.getStatus())) {
            throw new RuntimeException("当前任务状态不允许审批");
        }
        updateTaskStatus(id, TaskStatusEnum.PENDING_EXECUTION.getCode(), null);
    }

    @Override
    public void acceptTask(Long id, String userId) {
        WorkflowTask existing = workflowTaskMapper.findById(id);
        if (existing == null) {
            throw new RuntimeException("任务不存在");
        }
        if (!userId.equals(existing.getUserId())) {
            throw new RuntimeException("无权操作该任务");
        }
        if (!TaskStatusEnum.PENDING_ACCEPTANCE.getCode().equals(existing.getStatus())) {
            throw new RuntimeException("当前任务状态不允许验收");
        }
        updateTaskStatus(id, TaskStatusEnum.COMPLETED.getCode(), null);
    }

    @Override
    public void rejectTask(Long id, String userId, String reason) {
        WorkflowTask existing = workflowTaskMapper.findById(id);
        if (existing == null) {
            throw new RuntimeException("任务不存在");
        }
        if (!userId.equals(existing.getUserId())) {
            throw new RuntimeException("无权操作该任务");
        }
        if (!TaskStatusEnum.PENDING_ACCEPTANCE.getCode().equals(existing.getStatus())) {
            throw new RuntimeException("当前任务状态不允许驳回");
        }
        // 记录驳回原因，任务置为已驳回状态，由后台调度器扫描拉起重做（不在此处同步执行，避免接口长时间阻塞）
        updateTaskStatus(id, TaskStatusEnum.REJECTED.getCode(), reason);
        // 打回原因同时写入任务评论（用户身份），重做时智能体可通过评论历史感知驳回理由
        try {
            taskCommentService.addComment(id, reason, userId);
        } catch (Exception e) {
            logger.warn("打回原因写入任务评论失败: taskId={}", id, e);
        }
    }

    @Override
    public void closeTask(Long id, String userId) {
        WorkflowTask existing = workflowTaskMapper.findById(id);
        if (existing == null) {
            throw new RuntimeException("任务不存在");
        }
        if (!userId.equals(existing.getUserId())) {
            throw new RuntimeException("无权操作该任务");
        }
        updateTaskStatus(id, TaskStatusEnum.CLOSED.getCode(), null);
    }

    @Override
    public void redoTask(Long id, String userId) {
        WorkflowTask existing = workflowTaskMapper.findById(id);
        if (existing == null) {
            throw new RuntimeException("任务不存在");
        }
        if (!userId.equals(existing.getUserId())) {
            throw new RuntimeException("无权操作该任务");
        }
        TaskStatusEnum current = TaskStatusEnum.fromCode(existing.getStatus());
        if (current == null || !current.canTransitionTo(TaskStatusEnum.PENDING_EXECUTION)) {
            throw new RuntimeException("当前任务状态不允许重做");
        }
        // 重置为待执行，由后台调度器扫描拉起重跑（同时清空历史驳回/失败原因）
        updateTaskStatus(id, TaskStatusEnum.PENDING_EXECUTION.getCode(), null);
    }

    @Override
    public List<WorkflowTask> findPendingExecution() {
        return workflowTaskMapper.findPendingExecution();
    }

    @Override
    public void executeTask(UserChatRequest userChatRequest) {

        Long taskId = taskSessionMapper.findTaskIdBySessionId(userChatRequest.getSessionId());
        if (taskId == null) {
            throw new RuntimeException("会话未关联任务");
        }

        WorkflowTask task = workflowTaskMapper.findById(taskId);
        if (task == null) {
            throw new RuntimeException("任务不存在");
        }
        Agent agent = agentService.getAgentById(userChatRequest.getAgentId());
        if (agent == null) {
            throw new RuntimeException("智能体不存在");
        }
        taskCommentService.addComment(taskId, userChatRequest.getMessage(), userChatRequest.getUserId());

        // 创建任务执行器（复用或新建会话）
        IAgentExecutor executor = createTaskExecutor(task, agent, userChatRequest);
        executeTask(task,executor,600);
    }
    private void executeTask(WorkflowTask task,IAgentExecutor executor,long timeout){
        Long taskId=task.getId();
        // 更新状态为 processing
        updateTaskStatus(taskId, TaskStatusEnum.PROCESSING.getCode(), null);
        // 仅查询待处理评论：避免重复处理已处理过的评论
        List<WorkflowTaskComment> comments = taskCommentService.getPendingCommentsByTaskId(taskId);
        // 4. 构建内容（包含评论历史，区分评论者身份）
        List<Content> contents = new ArrayList<>();
        StringBuilder taskContent = new StringBuilder();
        taskContent.append(task.getContent() != null ? task.getContent() : "");
        if (comments != null && !comments.isEmpty()) {
            taskContent.append("\n\n--- 评论历史 ---\n");
            for (WorkflowTaskComment comment : comments) {
                if (TaskCommenterTypeEnum.isAgent(comment.getCommenterType())) {
                    continue;
                }
                // 总结评论追加类型标记，便于智能体识别重要节点
                String typeMark = TaskCommentTypeEnum.fromCode(comment.getCommentType()).isSummary() ? "[总结]" : "";
                taskContent.append(String.format("[%s]%s %s\n",
                        comment.getCreateTime() != null ? comment.getCreateTime() : "",
                        typeMark,
                        comment.getContent() != null ? comment.getContent() : ""));
            }
        }
        contents.add(new TextContent(taskContent.toString()));
        // 执行
        executor.execute(contents,timeout);
        // 执行完成后将本次预取的待处理评论标记为已处理（执行期间新增的评论不受影响，将在下次执行时处理）
        List<Long> processedCommentIds = comments.stream().map(WorkflowTaskComment::getId).collect(Collectors.toList());
        taskCommentService.markCommentsAsProcessed(processedCommentIds);

    }

    @Override
    public void executeTask(Long taskId) {
        WorkflowTask task = workflowTaskMapper.findById(taskId);
        if (task == null) {
            throw new RuntimeException("任务不存在");
        }
        Agent agent = agentService.getAgentById(task.getAgentId());
        if (agent == null) {
            throw new RuntimeException("智能体不存在");
        }
        // 查询已关联会话：打回重做时复用最近一次会话编号，保留上下文记忆
        List<TaskSession> sessions = taskSessionMapper.findByTaskId(taskId);
        String existingSessionId = null;
        if (sessions != null && !sessions.isEmpty()) {
            // findByTaskId 按 id ASC 返回，取最后一条为最近会话
            existingSessionId = sessions.get(sessions.size() - 1).getSessionId();
        }else {
            existingSessionId = UuidUtil.generateSimpleUUID();
            taskSessionMapper.insert(taskId, existingSessionId);
        }
        UserChatRequest userChatRequest = new UserChatRequest();
        userChatRequest.setSessionId(existingSessionId);
        userChatRequest.setUserId(task.getUserId());
        userChatRequest.setAiModelId(agent.getAiModelId());
        userChatRequest.setEnableThinking(agent.getEnableThinking());
        userChatRequest.setToolCallPermission("auto");
        // 创建任务执行器（复用或新建会话）
        IAgentExecutor executor = createTaskExecutor(task, agent, userChatRequest);
        executeTask(task,executor,1800);
    }

    /**
     * 创建任务执行器：生成任务场景的执行器参数和系统提示词，调用公共创建方法
     *
     * @param task              工作流任务
     * @param agent             关联智能体
     * @param userChatRequest 请求
     * @return
     */
    private IAgentExecutor createTaskExecutor(WorkflowTask task, Agent agent, UserChatRequest userChatRequest) {

        // 构建任务专用系统提示词
        String systemMessage = buildTaskSystemMessage(task, agent);

        // 构建工具集（任务执行场景强制注入 workflowTaskTool，确保智能体可记录评论）
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

        // 构建 AgentExecutorParams
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
        agentExecutorParams.setBizType(AgentExecutorBizTypeEnum.WorkflowTaskChat);
        agentExecutorParams.setMcpServerConfigs(mcpServerConfigService.findEnabled());




        // systemMessageProvider
        Function<Long, String> systemMessageProvider = aId -> systemMessage;

        // 调用公共创建方法（已内置旧执行器清理逻辑）
        IAgentExecutor agentExecutor = agentExecutorService.createAgentExecutor(agentExecutorParams, systemMessageProvider);
        return agentExecutor;
    }

    /**
     * 构建任务专用系统提示词，并注入项目空间限制
     */
    private String buildTaskSystemMessage(WorkflowTask task, Agent agent) {
        StringBuilder systemMsgBuilder = new StringBuilder();
        systemMsgBuilder.append("你是一个任务执行智能体。\n");
        systemMsgBuilder.append("智能体名称：").append(agent.getName()).append("\n");
        systemMsgBuilder.append("智能体描述：").append(agent.getDescription()).append("\n");
        systemMsgBuilder.append("\n请根据任务内容执行，完成后给出执行结果摘要，如有产出结果，请放到文件系统中。\n");
        systemMsgBuilder.append("记忆工具是你的核心工具，需要回忆什么信息时，先去调用记忆工具看看有没相关可用信息。\n");
        systemMsgBuilder.append("在判断有需要调用工具就去调用，遇到危险操作，立刻停止操作。\n");
        systemMsgBuilder.append("你只能使用用户提供的工具，绝对不能调用不存在的工具。\n");
        systemMsgBuilder.append("\n--- 任务评论工具使用指引 ---\n");
        systemMsgBuilder.append("你可以通过工具来操作当前任务：\n");
        systemMsgBuilder.append("1. 添加任务评论：用于记录任务处理的关键细节、阶段性进展、重要决策，便于用户追踪处理过程；当你需要向用户确认信息或遇到需要用户决策的问题时，也可以通过添加评论的方式提出问题，用户会在任务评论中回复你。\n");
        systemMsgBuilder.append("2. 查询当前任务：当需要回顾任务内容、查看用户是否有新的评论回复时调用。\n");
        systemMsgBuilder.append("建议在执行关键步骤后通过评论记录处理细节，遇到不确定的问题时通过评论向用户提问而非自行猜测。\n");
        systemMsgBuilder.append("每次处理完成后调用任务工具写入最终执行的结果总结。\n");
        systemMsgBuilder.append("任务编号：").append(task.getId()).append("\n");
        systemMsgBuilder.append("任务名称：").append(task.getTitle()).append("\n");
        systemMsgBuilder.append("任务内容：").append(task.getContent()).append("\n");
        // 若任务关联了项目，注入项目空间目录限制
        if (task.getProjectId() != null) {
            try {
                Project project = projectService.getProject(task.getProjectId(), task.getUserId());
                if (project != null) {
                    systemMsgBuilder.append("\n--- 项目细节 ---\n");
                    systemMsgBuilder.append("本任务关联项目「").append(project.getName()).append("」，项目描述为：\n");
                    systemMsgBuilder.append(project.getDescription()).append("\n");
                    // 注入项目重点日志，为智能体提供项目历史关键结论（含各任务总结评论）
                    appendImportantProjectLogs(systemMsgBuilder, task.getProjectId());
                    // 存储层只保留相对路径，任务提示词需要注入真实绝对路径
                    String absSpacePath = projectService.getProjectSpaceAbsolutePath(task.getProjectId(), task.getUserId());
                    if (absSpacePath != null && !absSpacePath.isEmpty()) {
                        systemMsgBuilder.append("\n--- 项目空间限制 ---\n");
                        systemMsgBuilder.append("本任务关联项目「").append(project.getName()).append("」，项目空间目录为：\n");
                        systemMsgBuilder.append(absSpacePath).append("\n");
                        systemMsgBuilder.append("重要约束：任务执行过程中所有文件操作（创建、读取、修改、删除等）仅限于在上述项目空间目录及其子目录内进行。\n");
                        systemMsgBuilder.append("严禁在该目录之外的任何位置进行任何文件操作，违反此约束将被视为越权操作并终止任务。\n");
                    }
                }
            } catch (Exception e) {
                logger.warn("注入项目空间限制失败，任务[{}]项目[{}]: {}", task.getId(), task.getProjectId(), e.getMessage());
            }
        }
        return systemMsgBuilder.toString();
    }

    /** 将项目重点日志（important 类型）追加到任务系统提示词，为智能体提供项目历史关键结论 */
    private void appendImportantProjectLogs(StringBuilder systemMsgBuilder, Long projectId) {
        try {
            List<ProjectLog> importantLogs = projectLogService.getImportantLogsByProjectId(projectId);
            if (importantLogs == null || importantLogs.isEmpty()) {
                return;
            }
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            systemMsgBuilder.append("\n--- 项目重点日志 ---\n");
            systemMsgBuilder.append("以下是该项目历史沉淀的重点信息（含各任务的关键结论与总结），执行任务时请充分参考：\n");
            for (ProjectLog logItem : importantLogs) {
                systemMsgBuilder.append('[')
                        .append(logItem.getCreateTime() != null ? logItem.getCreateTime().format(fmt) : "时间未知")
                        .append("][")
                        .append(logItem.getOperatorName() != null ? logItem.getOperatorName() : "未知")
                        .append("] ")
                        .append(logItem.getDetail() != null ? logItem.getDetail() : "")
                        .append('\n');
            }
        } catch (Exception e) {
            // 重点日志注入失败不阻断任务执行
            logger.warn("注入项目重点日志失败，项目[{}]: {}", projectId, e.getMessage());
        }
    }

    private List<String> parseToolNames(String toolsStr) {
        if (toolsStr == null || toolsStr.isEmpty()) {
            return new ArrayList<>();
        }
        return Arrays.stream(toolsStr.split(",")).collect(Collectors.toList());
    }

    /* ========== 前置任务条件 ========== */

    /**
     * 保存任务前置条件：先删除旧配置再重建。
     * 逐条校验：前置任务必须存在、不能是任务自身、要求状态不能为空且必须为合法状态值。
     * 前端未传 preconditions 字段（null）时视为清空配置。
     */
    private void savePreconditions(Long taskId, List<WorkflowTaskPrecondition> preconditions) {
        preconditionMapper.deleteByTaskId(taskId);
        if (preconditions == null || preconditions.isEmpty()) {
            return;
        }
        for (WorkflowTaskPrecondition pc : preconditions) {
            if (pc == null || pc.getPreTaskId() == null) {
                continue;
            }
            if (pc.getPreTaskId().equals(taskId)) {
                throw new RuntimeException("前置任务不能是任务自身");
            }
            WorkflowTask preTask = workflowTaskMapper.findById(pc.getPreTaskId());
            if (preTask == null) {
                throw new RuntimeException("前置任务不存在: #" + pc.getPreTaskId());
            }
            String requiredStatus = normalizeRequiredStatus(pc.getRequiredStatus());
            if (requiredStatus == null) {
                throw new RuntimeException("前置任务「" + (preTask.getTitle() != null ? preTask.getTitle() : "#" + preTask.getId()) + "」未勾选要求状态");
            }
            WorkflowTaskPrecondition item = new WorkflowTaskPrecondition();
            item.setTaskId(taskId);
            item.setPreTaskId(pc.getPreTaskId());
            item.setRequiredStatus(requiredStatus);
            preconditionMapper.insert(item);
        }
    }

    /** 规范化要求状态：按逗号拆分过滤非法值，返回合法状态的逗号分隔串；无合法状态返回 null */
    private String normalizeRequiredStatus(String requiredStatus) {
        if (requiredStatus == null || requiredStatus.trim().isEmpty()) {
            return null;
        }
        List<String> valid = new ArrayList<>();
        for (String code : requiredStatus.split(",")) {
            String trimmed = code.trim();
            if (!trimmed.isEmpty() && TaskStatusEnum.fromCode(trimmed) != null && !valid.contains(trimmed)) {
                valid.add(trimmed);
            }
        }
        return valid.isEmpty() ? null : String.join(",", valid);
    }

    @Override
    public List<WorkflowTaskPrecondition> getPreconditions(Long taskId) {
        List<WorkflowTaskPrecondition> list = preconditionMapper.findByTaskId(taskId);
        return list != null ? list : new ArrayList<>();
    }

    @Override
    public boolean isPreconditionsSatisfied(Long taskId) {
        List<WorkflowTaskPrecondition> preconditions = getPreconditions(taskId);
        if (preconditions.isEmpty()) {
            return true;
        }
        for (WorkflowTaskPrecondition pc : preconditions) {
            WorkflowTask preTask = workflowTaskMapper.findById(pc.getPreTaskId());
            // 前置任务已被删除（关联未清理的容错场景）视为满足，避免任务永久卡住
            if (preTask == null) {
                continue;
            }
            // 要求状态为多选：前置任务当前状态命中任意一个勾选状态即满足
            List<String> required = parseToolNames(pc.getRequiredStatus());
            if (!required.contains(preTask.getStatus())) {
                logger.info("任务前置条件未满足: taskId={}, preTaskId={}, preTaskStatus={}, required={}",
                        taskId, pc.getPreTaskId(), preTask.getStatus(), pc.getRequiredStatus());
                return false;
            }
        }
        return true;
    }


    @Override
    public void updateTaskStatus(Long taskId, String status, String rejectReason) {
        WorkflowTask task = workflowTaskMapper.findById(taskId);
        workflowTaskMapper.updateStatus(taskId, status, rejectReason);
        // 任务关联了项目且状态实际发生变化时，写入项目操作日志
        if (task == null || task.getProjectId() == null || status == null || status.equals(task.getStatus())) {
            return;
        }
        logTaskStatusToProject(task, status);
    }

    /** 任务状态变更写入关联项目的操作日志；智能体驱动的状态以智能体身份记录 */
    private void logTaskStatusToProject(WorkflowTask task, String newStatus) {
        try {
            TaskStatusEnum statusEnum = TaskStatusEnum.fromCode(newStatus);
            String label = statusEnum != null ? statusEnum.getDescription() : newStatus;
            String detail = "任务「" + (task.getTitle() != null ? task.getTitle() : "#" + task.getId()) + "」(#" + task.getId() + ") 状态变更为「" + label + "」";
            if (statusEnum != null && statusEnum.isAgentDriven()) {
                String operatorName = "智能体";
                if (task.getAgentId() != null) {
                    Agent agent = agentService.getAgentById(task.getAgentId());
                    if (agent != null && agent.getName() != null) {
                        operatorName = "智能体「" + agent.getName() + "」";
                    }
                }
                projectLogService.log(task.getProjectId(), task.getUserId(), operatorName, "task_status", detail);
            } else {
                projectLogService.log(task.getProjectId(), task.getUserId(), "task_status", detail);
            }
        } catch (Exception e) {
            logger.warn("任务状态变更写入项目日志失败: taskId={}, status={}", task.getId(), newStatus, e);
        }
    }

    @Override
    public List<TaskSession> getTaskSessions(Long taskId) {
        List<TaskSession> list = taskSessionMapper.findByTaskId(taskId);
        return list != null ? list : new ArrayList<>();
    }

    @Override
    public Long findTaskIdBySessionId(String sessionId) {
        return taskSessionMapper.findTaskIdBySessionId(sessionId);
    }
}
