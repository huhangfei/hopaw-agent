package com.agent.hopaw.biz.tool.workflowtask;

import com.agent.hopaw.infra.constant.TaskCommenterTypeEnum;
import com.agent.hopaw.infra.constant.TaskCommentTypeEnum;
import com.agent.hopaw.infra.constant.TaskStatusEnum;
import com.agent.hopaw.infra.model.entity.WorkflowTaskComment;
import com.agent.hopaw.infra.model.entity.WorkflowTask;
import com.agent.hopaw.infra.model.entity.WorkflowTaskPrecondition;
import com.agent.hopaw.infra.service.IProjectLogService;
import com.agent.hopaw.infra.service.IProjectMemoryService;
import com.agent.hopaw.infra.service.IWorkflowTaskCommentService;
import com.agent.hopaw.infra.service.IWorkflowTaskService;
import com.agent.hopaw.infra.tool.AgentTool;
import com.agent.hopaw.infra.tool.ToolSecurityLevel;
import com.agent.hopaw.infra.util.InvocationParametersWrapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.SearchBehavior;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.invocation.InvocationParameters;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 工作流任务工具集：任务列表查询、任务详情查询、任务添加/更新/删除/关闭/重做，以及当前任务查询与评论添加。
 * 智能体在执行任务时，可通过本工具查询当前任务详情与评论，并通过评论记录处理关键细节或向用户提问；
 * 也可代用户管理任务全生命周期（新增默认待启动，处理中的任务不允许编辑/删除）。
 * 任务数据不做用户归属隔离（跨用户共享协作）；状态变更类操作以智能体身份记录。
 */
@Component("workflowTaskTool")
public class WorkflowTaskTool implements AgentTool {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final IWorkflowTaskService workflowTaskService;
    private final IWorkflowTaskCommentService taskCommentService;
    private final IProjectLogService projectLogService;
    private final IProjectMemoryService projectMemoryService;

    public WorkflowTaskTool(IWorkflowTaskService workflowTaskService,
                            IWorkflowTaskCommentService taskCommentService,
                            IProjectLogService projectLogService,
                            IProjectMemoryService projectMemoryService) {
        this.workflowTaskService = workflowTaskService;
        this.taskCommentService = taskCommentService;
        this.projectLogService = projectLogService;
        this.projectMemoryService = projectMemoryService;
    }

    @Override
    public String getName() {
        return "workflowTaskTool";
    }

    @Override
    public String getDescription() {
        return "工作流任务工具：查询任务列表、查询任务详情、添加任务、更新任务、删除任务、关闭任务、重做任务，以及查询当前任务、查询任务记忆与添加任务评论";
    }

    @Override
    public String getIcon() {
        return "workflow-task-tool.svg";
    }

    @Override
    public String getKeyword() {
        return "工作流任务";
    }

    /**
     * 分页查询工作流任务列表，支持标题关键字与状态过滤（不做用户归属隔离）。
     */
    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(value = {"查询任务列表", "分页查询工作流任务列表，可按标题关键字和状态过滤"}, searchBehavior = SearchBehavior.ALWAYS_VISIBLE)
    public String findWorkflowTasks(@P(value = "标题关键字，空表示不过滤", required = false) String keyword,
                                    @P(value = "任务状态：pending=待启动/pending_execution=待执行/processing=处理中/pending_acceptance=待验收/completed=已完成/failed=失败/rejected=已驳回/closed=已关闭，空表示不过滤", required = false) String status,
                                    @P(value = "页码，从1开始，默认1", required = false) Integer page,
                                    @P(value = "每页数量，默认20，最大100", required = false) Integer size,
                                    InvocationParameters invocationParameters) {
        InvocationParametersWrapper wrapper = InvocationParametersWrapper.create(invocationParameters);
        int pageNo = (page == null || page < 1) ? 1 : page;
        int pageSize = (size == null || size < 1) ? 20 : Math.min(size, 100);
        String kw = keyword == null ? "" : keyword.trim();
        String st = status == null ? "" : status.trim();

        // userId 传空：任务数据不做用户归属过滤（跨用户共享协作）
        List<WorkflowTask> tasks = workflowTaskService.getTasksPage(null, kw, st, null, null, pageNo, pageSize);
        if (tasks == null || tasks.isEmpty()) {
            return "成功：当前条件下没有任务";
        }
        int total = workflowTaskService.countTasks(null, kw, st, null, null);
        StringBuilder sb = new StringBuilder();
        sb.append("共 ").append(total).append(" 个任务，当前第 ").append(pageNo).append(" 页（每页 ").append(pageSize).append(" 条）：\n");
        for (WorkflowTask t : tasks) {
            sb.append("任务ID：").append(t.getId())
                    .append("，标题：").append(t.getTitle())
                    .append("，状态：").append(statusText(t.getStatus()))
                    .append("，执行智能体：").append(t.getAgentName() != null ? t.getAgentName() : ("#" + t.getAgentId()))
                    .append(t.getProjectName() != null ? "，所属项目：" + t.getProjectName() : "")
                    .append("，开始时间：").append(t.getStartTime() != null ? t.getStartTime().format(TIME_FMT) : "未设置")
                    .append("\n");
        }
        return "成功：\n" + sb;
    }

    /**
     * 按任务编号查询任务详细信息（含前置条件及满足情况）。
     */
    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(value = {"查询任务详情", "按任务编号查询工作流任务详细信息（内容、状态、智能体、前置条件等）"}, searchBehavior = SearchBehavior.ALWAYS_VISIBLE)
    public String getWorkflowTaskDetail(@P("任务编号") Long taskId,
                                        InvocationParameters invocationParameters) {
        InvocationParametersWrapper wrapper = InvocationParametersWrapper.create(invocationParameters);
        // userId 传空：不做用户归属校验
        WorkflowTask task = workflowTaskService.getTask(taskId, null);
        if (task == null) {
            return "失败：任务不存在";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("任务ID：").append(task.getId()).append("\n");
        sb.append("任务标题：").append(task.getTitle()).append("\n");
        sb.append("任务状态：").append(statusText(task.getStatus())).append("\n");
        sb.append("任务内容：").append(task.getContent() != null ? task.getContent() : "").append("\n");
        sb.append("执行智能体：").append(task.getAgentName() != null ? task.getAgentName() + "（#" + task.getAgentId() + "）" : "#" + task.getAgentId()).append("\n");
        if (task.getProjectId() != null) {
            sb.append("所属项目：").append(task.getProjectName() != null ? task.getProjectName() : "未知").append("（#").append(task.getProjectId()).append("）\n");
        }
        if (task.getStartTime() != null) {
            sb.append("开始时间：").append(task.getStartTime().format(TIME_FMT)).append("\n");
        }
        if (task.getExecutionPeriod() != null) {
            sb.append("执行时段（分钟）：").append(task.getExecutionPeriod()).append("\n");
        }
        if (task.getRejectReason() != null && !task.getRejectReason().isEmpty()) {
            sb.append("驳回/失败原因：").append(task.getRejectReason()).append("\n");
        }
        sb.append("创建时间：").append(task.getCreateTime() != null ? task.getCreateTime().format(TIME_FMT) : "").append("\n");

        // 前置条件
        List<WorkflowTaskPrecondition> preconditions = workflowTaskService.getPreconditions(taskId);
        if (preconditions != null && !preconditions.isEmpty()) {
            boolean allSatisfied = workflowTaskService.isPreconditionsSatisfied(taskId);
            sb.append("前置条件（").append(allSatisfied ? "已全部满足" : "未全部满足").append("）：\n");
            for (WorkflowTaskPrecondition pc : preconditions) {
                String required = pc.getRequiredStatus() != null ? pc.getRequiredStatus() : "";
                boolean hit = pc.getPreTaskStatus() != null && required.contains(pc.getPreTaskStatus());
                sb.append("  - 前置任务#").append(pc.getPreTaskId())
                        .append("「").append(pc.getPreTaskTitle() != null ? pc.getPreTaskTitle() : "").append("」")
                        .append(" 当前状态：").append(statusText(pc.getPreTaskStatus()))
                        .append("，要求状态：").append(requiredStatusText(required))
                        .append(hit ? "（已满足）" : "（未满足）")
                        .append("\n");
            }
        }
        return "成功：\n" + sb;
    }

    /**
     * 添加工作流任务：创建后默认为待启动状态，由用户审核后进入执行。
     */
    @ToolSecurityLevel(ToolSecurityLevel.Level.PARAM_REQUIRE_APPROVAL)
    @Tool(value = {"添加工作流任务", "创建工作流任务（默认待启动状态，需用户审核后才会执行）"}, searchBehavior = SearchBehavior.ALWAYS_VISIBLE)
    public String addWorkflowTask(@P("任务标题") String title,
                                  @P("任务内容：下发给执行智能体的具体指令") String content,
                                  @P("执行智能体编号") Long agentId,
                                  @P(value = "关联项目编号，可不关联", required = false) Long projectId,
                                  @P(value = "开始时间，格式 yyyy-MM-dd HH:mm，不填则创建后可立即审核执行", required = false) String startTime,
                                  @P(value = "执行时段，格式 HH:mm-HH:mm（如 09:00-18:00），限制调度拉起任务的每日时间窗口，可不填表示不限制", required = false) String executionPeriod,
                                  @P(value = "前置任务配置，格式：任务编号:要求状态[|任务编号:要求状态...]，如 \"3:completed,failed|5:pending_acceptance\"；要求状态为状态码（多个状态逗号分隔），可选值 pending/pending_execution/processing/pending_acceptance/completed/failed/rejected/closed；不填表示无前置任务", required = false) String preconditions,
                                  InvocationParameters invocationParameters) {
        InvocationParametersWrapper wrapper = InvocationParametersWrapper.create(invocationParameters);
        if (title == null || title.trim().isEmpty()) {
            return "失败：任务标题不能为空";
        }
        if (content == null || content.trim().isEmpty()) {
            return "失败：任务内容不能为空";
        }
        if (agentId == null) {
            return "失败：必须指定执行智能体";
        }
        LocalDateTime start = parseTime(startTime);
        if (start == null && startTime != null && !startTime.trim().isEmpty()) {
            return "失败：开始时间格式不正确，应为 yyyy-MM-dd HH:mm";
        }

        try {
            WorkflowTask task = new WorkflowTask();
            task.setTitle(title.trim());
            task.setContent(content.trim());
            task.setAgentId(agentId);
            task.setProjectId(projectId);
            task.setStartTime(start);
            task.setExecutionPeriod(executionPeriod);
            task.setUserId(wrapper.getUserId());
            // 智能体创建的任务：记录创建者类型与创建者智能体（前端按创建者类型展示不同图标与名称）
            if (wrapper.getAgentId() != null) {
                task.setCreatorType(TaskCommenterTypeEnum.AGENT.getCode());
                task.setCreatorAgentId(wrapper.getAgentId());
            }
            WorkflowTask created = workflowTaskService.createTask(task);
            // 关联了项目则记录项目操作日志（与页面创建任务行为一致）
            if (created.getProjectId() != null) {
                try {
                    projectLogService.log(created.getProjectId(), wrapper.getUserId(), "task_bind",
                            "关联任务「" + created.getTitle() + "」(#" + created.getId() + ")");
                } catch (Exception ignored) {
                    // 日志失败不影响任务创建
                }
            }
            return "成功：任务已创建，任务ID：" + created.getId() + "，状态：" + statusText(created.getStatus());
        } catch (RuntimeException e) {
            return "失败：" + e.getMessage();
        }
    }

    /**
     * 更新工作流任务：仅覆盖传入的非空字段，未传字段保持原值。处理中的任务不允许编辑。
     */
    @ToolSecurityLevel(ToolSecurityLevel.Level.PARAM_REQUIRE_APPROVAL)
    @Tool(value = {"更新工作流任务", "更新工作流任务信息（仅覆盖传入的非空字段，未传字段保持原值；处理中的任务不允许编辑）"}, searchBehavior = SearchBehavior.ALWAYS_VISIBLE)
    public String updateWorkflowTask(@P("任务编号") Long taskId,
                                     @P(value = "任务标题，空表示保持原值", required = false) String title,
                                     @P(value = "任务内容：下发给执行智能体的具体指令，空表示保持原值", required = false) String content,
                                     @P(value = "执行智能体编号，空表示保持原值", required = false) Long agentId,
                                     @P(value = "关联项目编号，空表示保持原值", required = false) Long projectId,
                                     @P(value = "开始时间，格式 yyyy-MM-dd HH:mm，空表示保持原值", required = false) String startTime,
                                     @P(value = "执行时段，格式 HH:mm-HH:mm（如 09:00-18:00），空表示保持原值", required = false) String executionPeriod,
                                     @P(value = "前置任务配置，格式：任务编号:要求状态[|任务编号:要求状态...]，如 \"3:completed,failed|5:pending_acceptance\"；要求状态为状态码（多个状态逗号分隔），可选值 pending/pending_execution/processing/pending_acceptance/completed/failed/rejected/closed；空表示保持原值；传 \"none\" 清空全部前置任务", required = false) String preconditions,
                                     InvocationParameters invocationParameters) {
        InvocationParametersWrapper wrapper = InvocationParametersWrapper.create(invocationParameters);
        // userId 传空：不做用户归属校验
        WorkflowTask existing = workflowTaskService.getTask(taskId, null);
        if (existing == null) {
            return "失败：任务不存在";
        }

        LocalDateTime start = null;
        if (startTime != null && !startTime.trim().isEmpty()) {
            start = parseTime(startTime);
            if (start == null) {
                return "失败：开始时间格式不正确，应为 yyyy-MM-dd HH:mm";
            }
        }

        try {
            // updateTask 会整体覆盖标题/内容，这里先与原任务合并，保证未传字段保持原值
            WorkflowTask task = new WorkflowTask();
            task.setId(taskId);
            task.setTitle(title != null && !title.trim().isEmpty() ? title.trim() : existing.getTitle());
            task.setContent(content != null && !content.trim().isEmpty() ? content.trim() : existing.getContent());
            task.setAgentId(agentId != null ? agentId : existing.getAgentId());
            task.setProjectId(projectId != null ? projectId : existing.getProjectId());
            task.setStartTime(start != null ? start : existing.getStartTime());
            task.setExecutionPeriod(executionPeriod != null ? executionPeriod : existing.getExecutionPeriod());
            WorkflowTask updated = workflowTaskService.updateTask(task, null);

            // 关联项目变化时记录项目操作日志（与页面更新任务行为一致）
            if (!java.util.Objects.equals(existing.getProjectId(), updated.getProjectId())) {
                try {
                    if (existing.getProjectId() != null) {
                        projectLogService.log(existing.getProjectId(), wrapper.getUserId(), "task_unbind",
                                "取消关联任务「" + existing.getTitle() + "」(#" + taskId + ")");
                    }
                    if (updated.getProjectId() != null) {
                        projectLogService.log(updated.getProjectId(), wrapper.getUserId(), "task_bind",
                                "关联任务「" + updated.getTitle() + "」(#" + taskId + ")");
                    }
                } catch (Exception ignored) {
                    // 日志失败不影响任务更新
                }
            }
            return "成功：任务已更新，任务ID：" + updated.getId() + "，状态：" + statusText(updated.getStatus());
        } catch (RuntimeException e) {
            return "失败：" + e.getMessage();
        }
    }

    /**
     * 按任务编号删除工作流任务（危险操作，需用户确认；处理中的任务不允许删除）。
     * 请勿随便删除任务，不再需要执行的任务应使用关闭工作流任务代替删除。
     */
    @ToolSecurityLevel(ToolSecurityLevel.Level.ALL_REQUIRE_APPROVAL)
    @Tool(value = {"删除工作流任务", "按任务编号删除工作流任务，删除后不可恢复。请勿随便删除任务：不再需要执行的任务应优先使用「关闭工作流任务」"}, searchBehavior = SearchBehavior.ALWAYS_VISIBLE)
    public String deleteWorkflowTask(@P("任务编号") Long taskId,
                                     InvocationParameters invocationParameters) {
        InvocationParametersWrapper wrapper = InvocationParametersWrapper.create(invocationParameters);
        // userId 传空：不做用户归属校验
        WorkflowTask existing = workflowTaskService.getTask(taskId, null);
        if (existing == null) {
            return "失败：任务不存在";
        }
        try {
            workflowTaskService.deleteTask(taskId, null);
            // 关联了项目则记录取消关联日志（与页面删除任务行为一致）
            if (existing.getProjectId() != null) {
                try {
                    projectLogService.log(existing.getProjectId(), wrapper.getUserId(), "task_unbind",
                            "取消关联任务「" + existing.getTitle() + "」(#" + taskId + ")");
                } catch (Exception ignored) {
                    // 日志失败不影响任务删除
                }
            }
            return "成功：任务已删除，任务ID：" + taskId;
        } catch (RuntimeException e) {
            return "失败：" + e.getMessage();
        }
    }

    /**
     * 查询当前任务详情（含评论历史）。
     * 通过当前会话编号反查关联的任务。
     */
    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(value = {"查询当前任务", "查询当前正在执行的工作流任务详情及评论历史"}, searchBehavior = SearchBehavior.ALWAYS_VISIBLE)
    public String queryCurrentWorkflowTask(InvocationParameters invocationParameters) {
        InvocationParametersWrapper wrapper = InvocationParametersWrapper.create(invocationParameters);
        Long taskId = workflowTaskService.findTaskIdBySessionId(wrapper.getSessionId());
        if (taskId == null) {
            return "失败：当前会话未关联任何工作流任务";
        }
        // userId 传空：不做用户归属校验
        WorkflowTask task = workflowTaskService.getTask(taskId, null);
        if (task == null) {
            return "失败：任务不存在";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("任务ID：").append(task.getId()).append("\n");
        sb.append("任务名称：").append(task.getTitle()).append("\n");
        sb.append("任务状态：").append(task.getStatus()).append("\n");
        sb.append("任务内容：").append(task.getContent() != null ? task.getContent() : "").append("\n");
        if (task.getRejectReason() != null && !task.getRejectReason().isEmpty()) {
            sb.append("驳回原因：").append(task.getRejectReason()).append("\n");
        }

        // 评论历史
        List<WorkflowTaskComment> comments = taskCommentService.getCommentsByTaskId(taskId);
        if (comments != null && !comments.isEmpty()) {
            sb.append("\n--- 评论历史 ---\n");
            for (WorkflowTaskComment comment : comments) {
                // 区分评论者身份：agent=智能体，其他（含 null 旧数据）按用户处理
                String role = TaskCommenterTypeEnum.isAgent(comment.getCommenterType()) ? TaskCommenterTypeEnum.AGENT.getDescription() : TaskCommenterTypeEnum.USER.getDescription();
                String commenterId = comment.getCommenterId() != null ? comment.getCommenterId() : "";
                String time = comment.getCreateTime() != null ? comment.getCreateTime().format(TIME_FMT) : "";
                // 总结评论追加类型标记，便于智能体识别重要节点
                String typeMark = TaskCommentTypeEnum.fromCode(comment.getCommentType()).isSummary() ? "[总结]" : "";
                sb.append("[").append(time).append("][").append(role).append(":").append(commenterId).append("]").append(typeMark).append(" ")
                        .append(comment.getContent() != null ? comment.getContent() : "")
                        .append("\n");
            }
        } else {
            sb.append("\n（暂无评论）\n");
        }
        return "成功：\n" + sb.toString();
    }

    /**
     * 查询任务记忆：读取任务关联项目空间 memory/task-{taskId}-memory.md 的记忆内容
     * （任务历次执行/交互的增量总结，由系统自动沉淀），帮助智能体快速恢复任务上下文。
     * 任务编号为空时默认查询当前会话关联的任务。
     */
    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(value = {"查询任务记忆", "按任务编号查询任务记忆文件内容（任务历次执行的进展、决策与经验总结）。任务编号为空时查询当前任务"}, searchBehavior = SearchBehavior.ALWAYS_VISIBLE)
    public String getWorkflowTaskMemory(@P(value = "任务编号，空表示查询当前会话关联的任务", required = false) Long taskId,
                                        InvocationParameters invocationParameters) {
        InvocationParametersWrapper wrapper = InvocationParametersWrapper.create(invocationParameters);
        // 任务编号为空：从当前会话反查关联任务
        if (taskId == null) {
            taskId = workflowTaskService.findTaskIdBySessionId(wrapper.getSessionId());
            if (taskId == null) {
                return "失败：当前会话未关联任何工作流任务，请指定任务编号";
            }
        }
        // userId 传空：不做用户归属校验（任务数据跨用户共享）
        WorkflowTask task = workflowTaskService.getTask(taskId, null);
        if (task == null) {
            return "失败：任务不存在";
        }
        if (task.getProjectId() == null) {
            return "失败：任务【" + task.getTitle() + "】未关联项目，无任务记忆";
        }
        String memory = projectMemoryService.getTaskMemoryContent(task.getProjectId(), taskId);
        if (memory == null || memory.isBlank()) {
            return "成功：任务【" + task.getTitle() + "】暂无记忆";
        }
        return "成功：任务【" + task.getTitle() + "】记忆内容：\n" + memory;
    }

    /**
     * 智能体添加任务评论。
     * 用于记录任务处理的关键细节，或向用户提出问题等待用户评论回复。
     */
    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(value = {"添加任务评论", "向当前任务添加一条智能体评论，可用于记录处理细节或向用户提问。请通过 commentType 参数指明评论类型"}, searchBehavior = SearchBehavior.ALWAYS_VISIBLE)
    public String addWorkflowTaskComment(@P("评论内容：记录处理细节，或向用户提出的问题") String content,
                                 @P(value = "评论类型：summary=总结评论（用于任务阶段总结、关键结论、最终交付摘要）；default=普通评论（用于日常记录、提问、进度说明等）。请根据评论内容认真选择对应类型") String commentType,
                                 InvocationParameters invocationParameters) {
        InvocationParametersWrapper wrapper = InvocationParametersWrapper.create(invocationParameters);
        Long taskId = workflowTaskService.findTaskIdBySessionId(wrapper.getSessionId());
        if (taskId == null) {
            return "失败：当前会话未关联任何工作流任务";
        }
        if (content == null || content.trim().isEmpty()) {
            return "失败：评论内容不能为空";
        }
        // 智能体评论：commenterType=agent，commenterId=智能体ID
        taskCommentService.addComment(taskId, content.trim(), wrapper.getUserId(),
                TaskCommenterTypeEnum.AGENT.getCode(), String.valueOf(wrapper.getAgentId()), commentType);
        return "成功：任务评论已添加";
    }

    /**
     * 审核工作流任务：待启动任务审核通过后进入待执行，由系统调度执行。
     * 状态变更评论以智能体身份记录（commenterType=agent，commenterId=智能体编号）。
     */
    @ToolSecurityLevel(ToolSecurityLevel.Level.PARAM_REQUIRE_APPROVAL)
    @Tool(value = {"审核工作流任务", "审核待启动的工作流任务，审核通过后进入待执行状态，由系统自动调度执行智能体处理"}, searchBehavior = SearchBehavior.ALWAYS_VISIBLE)
    public String approveWorkflowTask(@P("任务编号") Long taskId,
                                      InvocationParameters invocationParameters) {
        InvocationParametersWrapper wrapper = InvocationParametersWrapper.create(invocationParameters);
        // userId 传空：不做用户归属校验
        WorkflowTask task = workflowTaskService.getTask(taskId, null);
        if (task == null) {
            return "失败：任务不存在";
        }
        try {
            workflowTaskService.approveTask(taskId, null, agentCommenterType(wrapper), agentCommenterId(wrapper));
            return "成功：任务已审核通过，进入待执行状态，任务ID：" + taskId;
        } catch (RuntimeException e) {
            return "失败：" + e.getMessage();
        }
    }

    /**
     * 验收工作流任务：待验收任务验收通过后标记为已完成。
     * 状态变更评论以智能体身份记录。
     */
    @ToolSecurityLevel(ToolSecurityLevel.Level.PARAM_REQUIRE_APPROVAL)
    @Tool(value = {"验收工作流任务", "验收待验收的工作流任务，确认执行结果符合要求后任务标记为已完成"}, searchBehavior = SearchBehavior.ALWAYS_VISIBLE)
    public String acceptWorkflowTask(@P("任务编号") Long taskId,
                                     InvocationParameters invocationParameters) {
        InvocationParametersWrapper wrapper = InvocationParametersWrapper.create(invocationParameters);
        // userId 传空：不做用户归属校验
        WorkflowTask task = workflowTaskService.getTask(taskId, null);
        if (task == null) {
            return "失败：任务不存在";
        }
        try {
            workflowTaskService.acceptTask(taskId, null, agentCommenterType(wrapper), agentCommenterId(wrapper));
            return "成功：任务已验收通过，任务ID：" + taskId;
        } catch (RuntimeException e) {
            return "失败：" + e.getMessage();
        }
    }

    /**
     * 驳回工作流任务：待验收任务验收不通过时驳回（注明原因），由系统安排重做。
     * 状态变更评论以智能体身份记录。
     */
    @ToolSecurityLevel(ToolSecurityLevel.Level.PARAM_REQUIRE_APPROVAL)
    @Tool(value = {"驳回工作流任务", "驳回待验收的工作流任务并注明驳回原因，驳回后任务转为失败状态等待处理"}, searchBehavior = SearchBehavior.ALWAYS_VISIBLE)
    public String rejectWorkflowTask(@P("任务编号") Long taskId,
                                     @P("驳回原因：说明执行结果不符合要求的具体原因") String reason,
                                     InvocationParameters invocationParameters) {
        InvocationParametersWrapper wrapper = InvocationParametersWrapper.create(invocationParameters);
        // userId 传空：不做用户归属校验
        WorkflowTask task = workflowTaskService.getTask(taskId, null);
        if (task == null) {
            return "失败：任务不存在";
        }
        if (reason == null || reason.trim().isEmpty()) {
            return "失败：驳回原因不能为空";
        }
        try {
            workflowTaskService.rejectTask(taskId, null, reason.trim(), agentCommenterType(wrapper), agentCommenterId(wrapper));
            return "成功：任务已驳回，任务ID：" + taskId;
        } catch (RuntimeException e) {
            return "失败：" + e.getMessage();
        }
    }

    /**
     * 关闭工作流任务：不再需要执行的任务可关闭，关闭前系统会先停止该任务的会话执行器。
     * 状态变更评论以智能体身份记录。
     */
    @ToolSecurityLevel(ToolSecurityLevel.Level.PARAM_REQUIRE_APPROVAL)
    @Tool(value = {"关闭工作流任务", "按任务编号关闭工作流任务，不需要再执行的任务可关闭（关闭前系统自动停止该任务的会话执行器），关闭后任务不再被调度执行"}, searchBehavior = SearchBehavior.ALWAYS_VISIBLE)
    public String closeWorkflowTask(@P("任务编号") Long taskId,
                                    InvocationParameters invocationParameters) {
        InvocationParametersWrapper wrapper = InvocationParametersWrapper.create(invocationParameters);
        // userId 传空：不做用户归属校验
        WorkflowTask task = workflowTaskService.getTask(taskId, null);
        if (task == null) {
            return "失败：任务不存在";
        }
        try {
            workflowTaskService.closeTask(taskId, null, agentCommenterType(wrapper), agentCommenterId(wrapper));
            return "成功：任务已关闭，任务ID：" + taskId;
        } catch (RuntimeException e) {
            return "失败：" + e.getMessage();
        }
    }

    /**
     * 重做工作流任务：失败/已完成的任务重新移回待执行，由系统调度重新处理。
     * 状态变更评论以智能体身份记录。
     */
    @ToolSecurityLevel(ToolSecurityLevel.Level.PARAM_REQUIRE_APPROVAL)
    @Tool(value = {"重做工作流任务", "按任务编号重做工作流任务，将失败或已完成的任务重新移回待执行状态等待系统调度处理（同时清空历史驳回/失败原因）"}, searchBehavior = SearchBehavior.ALWAYS_VISIBLE)
    public String redoWorkflowTask(@P("任务编号") Long taskId,
                                   InvocationParameters invocationParameters) {
        InvocationParametersWrapper wrapper = InvocationParametersWrapper.create(invocationParameters);
        // userId 传空：不做用户归属校验
        WorkflowTask task = workflowTaskService.getTask(taskId, null);
        if (task == null) {
            return "失败：任务不存在";
        }
        try {
            workflowTaskService.redoTask(taskId, null, agentCommenterType(wrapper), agentCommenterId(wrapper));
            return "成功：任务已重做，已移回待执行状态，任务ID：" + taskId;
        } catch (RuntimeException e) {
            return "失败：" + e.getMessage();
        }
    }

    /** 状态变更评论者类型：智能体调用时为 agent，否则按用户 */
    private String agentCommenterType(InvocationParametersWrapper wrapper) {
        return wrapper.getAgentId() != null ? TaskCommenterTypeEnum.AGENT.getCode() : TaskCommenterTypeEnum.USER.getCode();
    }

    /** 状态变更评论者编号：智能体调用时为智能体编号，否则为当前用户 */
    private String agentCommenterId(InvocationParametersWrapper wrapper) {
        return wrapper.getAgentId() != null ? String.valueOf(wrapper.getAgentId()) : wrapper.getUserId();
    }

    /** 状态码转中文描述，未知状态原样返回 */
    private String statusText(String code) {
        TaskStatusEnum e = TaskStatusEnum.fromCode(code);
        return e != null ? e.getDescription() : (code != null ? code : "未知");
    }

    /** 要求状态（逗号分隔的状态码）转中文描述 */
    private String requiredStatusText(String required) {
        if (required == null || required.trim().isEmpty()) {
            return "未设置";
        }
        StringBuilder sb = new StringBuilder();
        for (String code : required.split(",")) {
            String c = code.trim();
            if (c.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("/");
            }
            sb.append(statusText(c));
        }
        return sb.toString();
    }

    /** 解析 yyyy-MM-dd HH:mm（或 yyyy-MM-dd HH:mm:ss）格式时间，失败返回 null */
    private LocalDateTime parseTime(String time) {
        if (time == null || time.trim().isEmpty()) {
            return null;
        }
        String t = time.trim();
        try {
            if (t.length() == 16) {
                return LocalDateTime.parse(t, TIME_FMT);
            }
            return LocalDateTime.parse(t.replace(' ', 'T'));
        } catch (Exception e) {
            return null;
        }
    }
}
