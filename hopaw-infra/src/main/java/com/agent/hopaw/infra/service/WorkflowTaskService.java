package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.executor.IAgentExecutor;
import com.agent.hopaw.infra.mapper.TaskSessionMapper;
import com.agent.hopaw.infra.mapper.WorkflowTaskMapper;
import com.agent.hopaw.infra.model.dto.AgentExecutorParams;
import com.agent.hopaw.infra.model.dto.ToolSetInfo;
import com.agent.hopaw.infra.model.dto.UserChatRequest;
import com.agent.hopaw.infra.model.entity.Agent;
import com.agent.hopaw.infra.model.entity.Project;
import com.agent.hopaw.infra.model.entity.TaskComment;
import com.agent.hopaw.infra.model.entity.TaskSession;
import com.agent.hopaw.infra.model.entity.WorkflowTask;
import com.agent.hopaw.infra.tool.IAgentToolService;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.TextContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class WorkflowTaskService implements IWorkflowTaskService {
    private static final Logger logger = LoggerFactory.getLogger(WorkflowTaskService.class);

    private final WorkflowTaskMapper workflowTaskMapper;
    private final TaskSessionMapper taskSessionMapper;
    private final IAgentExecutorService agentExecutorService;
    private final IChatSessionService chatSessionService;
    private final IAgentService agentService;
    private final ITaskCommentService taskCommentService;
    private final IAgentToolService agentToolService;
    private final IMcpServerConfigService mcpServerConfigService;
    private final IProjectService projectService;

    public WorkflowTaskService(WorkflowTaskMapper workflowTaskMapper,
                                TaskSessionMapper taskSessionMapper,
                                IAgentExecutorService agentExecutorService,
                                IChatSessionService chatSessionService,
                                IAgentService agentService,
                                ITaskCommentService taskCommentService,
                                IAgentToolService agentToolService,
                                IMcpServerConfigService mcpServerConfigService,
                                IProjectService projectService) {
        this.workflowTaskMapper = workflowTaskMapper;
        this.taskSessionMapper = taskSessionMapper;
        this.agentExecutorService = agentExecutorService;
        this.chatSessionService = chatSessionService;
        this.agentService = agentService;
        this.taskCommentService = taskCommentService;
        this.agentToolService = agentToolService;
        this.mcpServerConfigService = mcpServerConfigService;
        this.projectService = projectService;
    }

    @Override
    public WorkflowTask createTask(WorkflowTask task) {
        task.setStatus("pending");
        LocalDateTime now = LocalDateTime.now();
        task.setCreateTime(now);
        task.setUpdateTime(now);
        workflowTaskMapper.insert(task);
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
        return task;
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
        if (!"pending".equals(existing.getStatus())) {
            throw new RuntimeException("当前任务状态不允许审批");
        }
        updateTaskStatus(id, "pending_execution", null);
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
        if (!"pending_acceptance".equals(existing.getStatus())) {
            throw new RuntimeException("当前任务状态不允许验收");
        }
        updateTaskStatus(id, "completed", null);
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
        if (!"pending_acceptance".equals(existing.getStatus())) {
            throw new RuntimeException("当前任务状态不允许驳回");
        }
        // 记录驳回原因
        updateTaskStatus(id, "rejected", reason);
        // 立即重新执行
        updateTaskStatus(id, "processing", null);
        executeTask(id);
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
        updateTaskStatus(id, "closed", null);
    }

    @Override
    public List<WorkflowTask> findPendingExecution() {
        return workflowTaskMapper.findPendingExecution();
    }

    @Override
    public void executeTask(UserChatRequest userChatRequest) {

        Long taskId = taskSessionMapper.findTaskIdBySessionId(userChatRequest.getSessionId());
        if (taskId != null) {
            taskCommentService.addComment(taskId, userChatRequest.getMessage(), userChatRequest.getUserId());
            executeTask(taskId);
        }
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
        // 更新状态为 processing
        updateTaskStatus(taskId, "processing", null);
        // 仅查询待处理评论：避免重复处理已处理过的评论
        List<TaskComment> comments = taskCommentService.getPendingCommentsByTaskId(taskId);
        // 查询已关联会话：打回重做时复用最近一次会话编号，保留上下文记忆
        List<TaskSession> sessions = taskSessionMapper.findByTaskId(taskId);
        String existingSessionId = null;
        if (sessions != null && !sessions.isEmpty()) {
            // findByTaskId 按 id ASC 返回，取最后一条为最近会话
            existingSessionId = sessions.get(sessions.size() - 1).getSessionId();
        }
        // 创建任务执行器（复用或新建会话）
        IAgentExecutor executor = createTaskExecutor(task, agent, existingSessionId);
        String sessionId = executor.getSessionId();
        // 仅新建会话时记录 task_sessions 关联；bizType 由 saveChatSession 在 INSERT 时写入
        if (existingSessionId == null) {
            taskSessionMapper.insert(taskId, sessionId);
        }
        // 4. 构建内容（包含评论历史，区分评论者身份）
        List<Content> contents = new ArrayList<>();
        StringBuilder taskContent = new StringBuilder();
        taskContent.append(task.getContent() != null ? task.getContent() : "");
        if (comments != null && !comments.isEmpty()) {
            taskContent.append("\n\n--- 评论历史 ---\n");
            for (TaskComment comment : comments) {
                if ("agent".equals(comment.getCommenterType())) {
                    continue;
                }
                taskContent.append(String.format("[%s] %s\n",
                        comment.getCreateTime() != null ? comment.getCreateTime() : "",
                        comment.getContent() != null ? comment.getContent() : ""));
            }
        }
        contents.add(new TextContent(taskContent.toString()));
        // 执行
        executor.execute(contents);
        // 执行完成后将本次预取的待处理评论标记为已处理（执行期间新增的评论不受影响，将在下次执行时处理）
        List<Long> processedCommentIds = comments.stream().map(TaskComment::getId).collect(Collectors.toList());
        taskCommentService.markCommentsAsProcessed(processedCommentIds);
    }

    /**
     * 创建任务执行器：生成任务场景的执行器参数和系统提示词，调用公共创建方法
     * @param task 工作流任务
     * @param agent 关联智能体
     * @param existingSessionId 已关联的会话编号（打回重做时复用，传 null 表示新建会话）
     * @return
     */
    private IAgentExecutor createTaskExecutor(WorkflowTask task, Agent agent,String existingSessionId) {
        // 1. 确定会话编号：打回重做时复用已关联会话，否则新建
        String sessionId;
        if (existingSessionId != null && !existingSessionId.isEmpty()) {
            sessionId = existingSessionId;
        } else {
            sessionId = UUID.randomUUID().toString();
        }

        // 2. 构建任务专用系统提示词
        String systemMessage = buildTaskSystemMessage(task, agent);

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
        params.setMcpServerConfigs(mcpServerConfigService.findEnabled());
        params.setBizType("task");

        // 6. systemMessageProvider
        Function<Long, String> systemMessageProvider = aId -> systemMessage;

        // 7. 调用公共创建方法（已内置旧执行器清理逻辑）
        IAgentExecutor agentExecutor = agentExecutorService.createAgentExecutor(params, systemMessageProvider);
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

    private List<String> parseToolNames(String toolsStr) {
        if (toolsStr == null || toolsStr.isEmpty()) {
            return new ArrayList<>();
        }
        return Arrays.stream(toolsStr.split(",")).collect(Collectors.toList());
    }

    @Override
    public void updateTaskStatus(Long taskId, String status, String rejectReason) {
        workflowTaskMapper.updateStatus(taskId, status, rejectReason);
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
