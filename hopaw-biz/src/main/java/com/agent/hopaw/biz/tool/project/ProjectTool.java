package com.agent.hopaw.biz.tool.project;

import com.agent.hopaw.infra.constant.ProjectStatusEnum;
import com.agent.hopaw.infra.model.entity.Project;
import com.agent.hopaw.infra.service.IProjectService;
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
 * 项目工具集：项目列表查询、项目详情查询、项目保存（新增或更新）、项目删除。
 * 智能体可通过本工具了解和管理用户的项目，所有操作均限定在当前用户自己的项目范围内。
 */
@Component("projectTool")
public class ProjectTool implements AgentTool {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final IProjectService projectService;

    public ProjectTool(IProjectService projectService) {
        this.projectService = projectService;
    }

    @Override
    public String getName() {
        return "projectTool";
    }

    @Override
    public String getDescription() {
        return "项目工具：查询项目列表、查询项目详情、保存项目（新增或更新）、删除项目";
    }

    @Override
    public String getKeyword() {
        return "项目";
    }

    /**
     * 分页查询当前用户的项目列表，支持名称关键字与状态过滤。
     */
    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(value = {"查询项目列表", "分页查询当前用户的项目列表，可按名称关键字和状态过滤"}, searchBehavior = SearchBehavior.ALWAYS_VISIBLE)
    public String findProjects(@P(value = "名称关键字，空表示不过滤", required = false) String keyword,
                               @P(value = "项目状态：planning=规划中/in_progress=进行中/paused=已暂停/completed=已完成/archived=已归档，空表示不过滤", required = false) String status,
                               @P(value = "页码，从1开始，默认1", required = false) Integer page,
                               @P(value = "每页数量，默认20，最大100", required = false) Integer size,
                               InvocationParameters invocationParameters) {
        InvocationParametersWrapper wrapper = InvocationParametersWrapper.create(invocationParameters);
        int pageNo = (page == null || page < 1) ? 1 : page;
        int pageSize = (size == null || size < 1) ? 20 : Math.min(size, 100);
        String kw = keyword == null ? "" : keyword.trim();
        String st = status == null ? "" : status.trim();

        List<Project> projects = projectService.getProjectsPage(wrapper.getUserId(), kw, st, pageNo, pageSize);
        if (projects == null || projects.isEmpty()) {
            return "成功：当前条件下没有项目";
        }
        int total = projectService.countProjects(wrapper.getUserId(), kw, st);
        StringBuilder sb = new StringBuilder();
        sb.append("共 ").append(total).append(" 个项目，当前第 ").append(pageNo).append(" 页（每页 ").append(pageSize).append(" 条）：\n");
        for (Project p : projects) {
            sb.append("项目ID：").append(p.getId())
                    .append("，名称：").append(p.getName())
                    .append("，状态：").append(statusText(p.getStatus()))
                    .append("，描述：").append(brief(p.getDescription(), 50))
                    .append("，创建时间：").append(p.getCreateTime() != null ? p.getCreateTime().format(TIME_FMT) : "")
                    .append("\n");
        }
        return "成功：\n" + sb;
    }

    /**
     * 按项目编号查询项目详细信息。
     */
    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(value = {"查询项目详情", "按项目编号查询项目详细信息（名称、状态、描述、空间目录等）"}, searchBehavior = SearchBehavior.ALWAYS_VISIBLE)
    public String getProjectDetail(@P("项目编号") Long projectId,
                                   InvocationParameters invocationParameters) {
        InvocationParametersWrapper wrapper = InvocationParametersWrapper.create(invocationParameters);
        Project p = projectService.getProject(projectId, wrapper.getUserId());
        if (p == null) {
            return "失败：项目不存在或无权访问";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("项目ID：").append(p.getId()).append("\n");
        sb.append("项目名称：").append(p.getName()).append("\n");
        sb.append("项目状态：").append(statusText(p.getStatus())).append("\n");
        sb.append("项目描述：").append(p.getDescription() != null && !p.getDescription().isEmpty() ? p.getDescription() : "无").append("\n");
        sb.append("空间目录：").append(p.getSpaceDir() != null ? p.getSpaceDir() : "未创建").append("\n");
        sb.append("创建时间：").append(p.getCreateTime() != null ? p.getCreateTime().format(TIME_FMT) : "").append("\n");
        sb.append("更新时间：").append(p.getUpdateTime() != null ? p.getUpdateTime().format(TIME_FMT) : "").append("\n");
        return "成功：\n" + sb;
    }

    /**
     * 保存项目：项目编号为空时新增项目，非空时更新已有项目。
     * 更新时仅覆盖传入的非空字段（名称必填），状态需符合流转规则。
     */
    @ToolSecurityLevel(ToolSecurityLevel.Level.PARAM_REQUIRE_APPROVAL)
    @Tool(value = {"保存项目", "保存项目信息：项目编号为空时新增项目，非空时更新已有项目"}, searchBehavior = SearchBehavior.ALWAYS_VISIBLE)
    public String saveProject(@P(value = "项目编号：新增时留空，更新时必填", required = false) Long projectId,
                              @P("项目名称") String name,
                              @P(value = "项目描述", required = false) String description,
                              @P(value = "项目状态：planning/in_progress/paused/completed/archived，仅更新时可指定，且需符合状态流转规则", required = false) String status,
                              InvocationParameters invocationParameters) {
        InvocationParametersWrapper wrapper = InvocationParametersWrapper.create(invocationParameters);
        if (name == null || name.trim().isEmpty()) {
            return "失败：项目名称不能为空";
        }
        if (status != null && !status.trim().isEmpty() && ProjectStatusEnum.fromCode(status.trim()) == null) {
            return "失败：非法的项目状态: " + status;
        }

        try {
            if (projectId == null) {
                // 新增：初始状态固定为规划中，项目空间自动创建
                Project project = new Project();
                project.setName(name.trim());
                project.setDescription(description);
                project.setUserId(wrapper.getUserId());
                Project created = projectService.createProject(project);
                return "成功：项目已创建，项目ID：" + created.getId() + "，状态：" + statusText(created.getStatus());
            }
            // 更新
            Project project = new Project();
            project.setId(projectId);
            project.setName(name.trim());
            project.setDescription(description);
            project.setStatus(status == null || status.trim().isEmpty() ? null : status.trim());
            Project updated = projectService.updateProject(project, wrapper.getUserId());
            return "成功：项目已更新，项目ID：" + updated.getId() + "，状态：" + statusText(updated.getStatus());
        } catch (RuntimeException e) {
            return "失败：" + e.getMessage();
        }
    }

    /**
     * 按项目编号删除项目（危险操作，需用户确认）。
     */
    @ToolSecurityLevel(ToolSecurityLevel.Level.ALL_REQUIRE_APPROVAL)
    @Tool(value = {"删除项目", "按项目编号删除项目，删除后不可恢复"}, searchBehavior = SearchBehavior.ALWAYS_VISIBLE)
    public String deleteProject(@P("项目编号") Long projectId,
                                InvocationParameters invocationParameters) {
        InvocationParametersWrapper wrapper = InvocationParametersWrapper.create(invocationParameters);
        try {
            projectService.deleteProject(projectId, wrapper.getUserId());
            return "成功：项目已删除，项目ID：" + projectId;
        } catch (RuntimeException e) {
            return "失败：" + e.getMessage();
        }
    }

    /** 状态码转中文描述，未知状态原样返回 */
    private String statusText(String code) {
        ProjectStatusEnum e = ProjectStatusEnum.fromCode(code);
        return e != null ? e.getDescription() : (code != null ? code : "未知");
    }

    /** 描述摘要：压缩为单行并截断 */
    private String brief(String text, int max) {
        if (text == null || text.trim().isEmpty()) {
            return "无";
        }
        String t = text.replace("\n", " ").trim();
        return t.length() > max ? t.substring(0, max) + "..." : t;
    }
}
