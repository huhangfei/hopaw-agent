package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.model.entity.Project;
import com.agent.hopaw.infra.model.entity.ProjectAttachment;
import com.agent.hopaw.infra.model.entity.WorkflowTask;

import java.util.List;

/**
 * 项目服务接口
 */
public interface IProjectService {
    Project createProject(Project project);
    Project updateProject(Project project, String userId);
    /** 更新项目状态 */
    void updateStatus(Long id, String status, String userId);
    void deleteProject(Long id, String userId);
    Project getProject(Long id, String userId);
    List<Project> getProjectsPage(String userId, String keyword, String status, int page, int size);
    int countProjects(String userId, String keyword, String status);
    List<Project> getAllProjects(String userId);
    /** 查询项目下的任务列表 */
    List<WorkflowTask> getProjectTasks(Long projectId, String userId);
    void bindAttachments(Long projectId, List<Long> attachmentIds);
    void unbindAttachment(Long projectId, Long attachmentId);
    List<ProjectAttachment> getProjectAttachments(Long projectId);
}
