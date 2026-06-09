package com.agent.hopaw.biz.tool.agenttask;


import com.agent.hopaw.infra.model.entity.ScheduledTask;
import com.agent.hopaw.infra.service.IScheduledTaskService;
import com.agent.hopaw.infra.util.InvocationParametersWrapper;
import com.agent.hopaw.infra.tool.ToolSecurityLevel;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.invocation.InvocationParameters;
import com.agent.hopaw.infra.tool.AgentTool;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;
@Component("agentTaskTool")
public class AgentTaskTool implements AgentTool {
    private final IScheduledTaskService scheduledTaskService;
    public AgentTaskTool(IScheduledTaskService scheduledTaskService) {
        this.scheduledTaskService = scheduledTaskService;
    }
    @Override
    public String getName() {
        return "agentTaskTool";
    }

    @Override
    public String getDescription() {
        return "智能体内创建定时任务的工具";
    }

    @Override
    public String getIcon() {
        return "agent-task-tool.svg";
    }

    @Override
    public String getKeyword() {
        return "定时任务";
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.PARAM_REQUIRE_APPROVAL)
    @Tool(value = {"创建代理任务", "创建定时执行的任务"})
    public String createAgentTask(@P("任务的简要名称") String taskName, @P("任务的cron表达式(6位)") String cron, @P("任务具体要做的事情描述") String taskDescription, InvocationParameters invocationParameters) {
        InvocationParametersWrapper invocationParametersWrapper = InvocationParametersWrapper.create(invocationParameters);
        ScheduledTask agentTask = new ScheduledTask(taskName, "agentTask", cron, 1, taskDescription);
        agentTask.setAgentId(String.valueOf(invocationParametersWrapper.getAgentId()));
        agentTask.setUserId(invocationParametersWrapper.getUserId());
        agentTask.setSessionId(invocationParametersWrapper.getSessionId());
        agentTask.setBuiltin(0);
        scheduledTaskService.insert(agentTask);
        return "定时任务创建成功";
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(value = {"查询代理任务", "查询定时执行的任务"})
    public String findAgentTask(InvocationParameters invocationParameters) {
        InvocationParametersWrapper invocationParametersWrapper = InvocationParametersWrapper.create(invocationParameters);
        List<ScheduledTask> tasks = scheduledTaskService.findByUserIdAndAgentId(invocationParametersWrapper.getUserId(), String.valueOf(invocationParametersWrapper.getAgentId()));
        if (tasks != null && !tasks.isEmpty()){
            //循环 tasks 构建 字符串 拼接主要字段
            StringBuilder sb = new StringBuilder();
            for (ScheduledTask task : tasks) {
                sb.append("任务ID：").append(task.getId())
                        .append("，任务名称：").append(task.getTaskName())
                        .append("，任务描述：").append(task.getDescription())
                        .append("，cron表达式：").append(task.getCronExpression())
                        .append("，状态：").append(task.getEnabled() == 1 ? "启用" : "禁用")
                        .append("，创建时间：").append(task.getCreateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                        .append("\n");
            }
            return "成功：\n" + sb.toString();
        }
        return "成功：当前没有定时任务";
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.PARAM_REQUIRE_APPROVAL)
    @Tool(value = {"停止代理任务", "停止启动中的定时执行的任务"})
    public String stopAgentTask(@P("任务ID") Long taskId,InvocationParameters invocationParameters) {
        InvocationParametersWrapper invocationParametersWrapper = InvocationParametersWrapper.create(invocationParameters);
        //先查询，判断是否与agentId相等
        ScheduledTask task = scheduledTaskService.findById(taskId);
        if (task.getAgentId()!=null && !task.getAgentId().equals(String.valueOf(invocationParametersWrapper.getAgentId()))) {
            return "失败：该任务非该智能体创建，无法停止";
        }
        scheduledTaskService.setEnabled(taskId, 0);
        return "定时任务停止成功";
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.PARAM_REQUIRE_APPROVAL)
    @Tool(value = {"启动代理任务", "启动停止中的定时执行的任务"})
    public String startAgentTask(@P("任务ID") Long taskId,InvocationParameters invocationParameters) {
        InvocationParametersWrapper invocationParametersWrapper = InvocationParametersWrapper.create(invocationParameters);
        //先查询，判断是否与agentId相等
        ScheduledTask task = scheduledTaskService.findById(taskId);
        if (task.getAgentId()!=null && !task.getAgentId().equals(invocationParametersWrapper.getAgentId())) {
            return "失败：该任务非该智能体创建，无法启动";
        }
        scheduledTaskService.setEnabled(taskId, 1);
        return "定时任务启动成功";
    }
    @ToolSecurityLevel(ToolSecurityLevel.Level.ALL_REQUIRE_APPROVAL)
    @Tool(value = {"删除代理任务", "删除定时执行的任务"})
    public String deleteAgentTask(@P("任务ID") Long taskId,InvocationParameters invocationParameters) {
        InvocationParametersWrapper invocationParametersWrapper = InvocationParametersWrapper.create(invocationParameters);
        //先查询，判断是否与agentId相等
        ScheduledTask task = scheduledTaskService.findById(taskId);
        if (task.getAgentId()!=null && !task.getAgentId().equals(invocationParametersWrapper.getAgentId().toString())) {
            return "失败：该任务非该智能体创建，无法删除";
        }
        scheduledTaskService.deleteById(taskId);
        return "定时任务删除成功";
    }
}
