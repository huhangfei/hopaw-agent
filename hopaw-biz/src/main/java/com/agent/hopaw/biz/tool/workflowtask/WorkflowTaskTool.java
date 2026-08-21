package com.agent.hopaw.biz.tool.workflowtask;

import com.agent.hopaw.infra.constant.TaskCommenterTypeEnum;
import com.agent.hopaw.infra.constant.TaskCommentTypeEnum;
import com.agent.hopaw.infra.model.entity.WorkflowTaskComment;
import com.agent.hopaw.infra.model.entity.WorkflowTask;
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

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 工作流任务工具集：任务查询、任务评论添加。
 * 智能体在执行任务时，可通过本工具查询当前任务详情与评论，并通过评论记录处理关键细节或向用户提问。
 */
@Component("workflowTaskTool")
public class WorkflowTaskTool implements AgentTool {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final IWorkflowTaskService workflowTaskService;
    private final IWorkflowTaskCommentService taskCommentService;

    public WorkflowTaskTool(IWorkflowTaskService workflowTaskService, IWorkflowTaskCommentService taskCommentService) {
        this.workflowTaskService = workflowTaskService;
        this.taskCommentService = taskCommentService;
    }

    @Override
    public String getName() {
        return "workflowTaskTool";
    }

    @Override
    public String getDescription() {
        return "工作流任务工具：查询当前任务详情、评论历史，以及添加任务评论";
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
        WorkflowTask task = workflowTaskService.getTask(taskId, wrapper.getUserId());
        if (task == null) {
            return "失败：任务不存在或无权访问";
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
     * 智能体添加任务评论。
     * 用于记录任务处理的关键细节，或向用户提出问题等待用户评论回复。
     */
    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(value = {"添加任务评论", "向当前任务添加一条智能体评论，可用于记录处理细节或向用户提问。请通过 commentType 参数指明评论类型"}, searchBehavior = SearchBehavior.ALWAYS_VISIBLE)
    public String addWorkflowTaskComment(@P("评论内容：记录处理细节，或向用户提出的问题") String content,
                                 @P(value = "评论类型：default=普通评论（用于日常记录、提问、进度说明等）；summary=总结评论（用于任务阶段总结、关键结论、最终交付摘要）。请根据评论内容选择对应类型", required = false) String commentType,
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
}
