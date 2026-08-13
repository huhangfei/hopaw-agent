package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.mapper.ProjectAttachmentMapper;
import com.agent.hopaw.infra.mapper.ProjectMapper;
import com.agent.hopaw.infra.mapper.WorkflowTaskMapper;
import com.agent.hopaw.infra.model.entity.Project;
import com.agent.hopaw.infra.model.entity.ProjectAttachment;
import com.agent.hopaw.infra.model.entity.WorkflowTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ProjectService implements IProjectService {
    private static final Logger logger = LoggerFactory.getLogger(ProjectService.class);

    /** 允许的项目状态值 */
    private static final Set<String> ALLOWED_STATUS = new HashSet<>(Arrays.asList(
            "planning", "in_progress", "paused", "completed", "archived"
    ));
    /** 状态流转规则：key 可流转到 value 集合中的任一状态 */
    private static final java.util.Map<String, Set<String>> TRANSITIONS = new java.util.HashMap<>();
    static {
        TRANSITIONS.put("planning", new HashSet<>(Arrays.asList("in_progress", "archived")));
        TRANSITIONS.put("in_progress", new HashSet<>(Arrays.asList("paused", "completed", "archived")));
        TRANSITIONS.put("paused", new HashSet<>(Arrays.asList("in_progress", "archived")));
        TRANSITIONS.put("completed", new HashSet<>(Arrays.asList("in_progress", "archived")));
        TRANSITIONS.put("archived", new HashSet<>(Arrays.asList("planning")));
    }

    private final ProjectMapper projectMapper;
    private final ProjectAttachmentMapper projectAttachmentMapper;
    private final WorkflowTaskMapper workflowTaskMapper;

    public ProjectService(ProjectMapper projectMapper, ProjectAttachmentMapper projectAttachmentMapper, WorkflowTaskMapper workflowTaskMapper) {
        this.projectMapper = projectMapper;
        this.projectAttachmentMapper = projectAttachmentMapper;
        this.workflowTaskMapper = workflowTaskMapper;
    }

    @Override
    public Project createProject(Project project) {
        project.setStatus("planning");
        LocalDateTime now = LocalDateTime.now();
        project.setCreateTime(now);
        project.setUpdateTime(now);
        projectMapper.insert(project);
        return project;
    }

    @Override
    public Project updateProject(Project project, String userId) {
        Project existing = projectMapper.findById(project.getId());
        if (existing == null) {
            throw new RuntimeException("项目不存在");
        }
        if (!userId.equals(existing.getUserId())) {
            throw new RuntimeException("无权修改该项目");
        }
        existing.setName(project.getName());
        existing.setDescription(project.getDescription());
        // 状态变更走 updateStatus 接口，这里仅当传入了合法状态且与当前不同时校验流转
        if (project.getStatus() != null && !project.getStatus().equals(existing.getStatus())) {
            validateTransition(existing.getStatus(), project.getStatus());
            existing.setStatus(project.getStatus());
        }
        existing.setUpdateTime(LocalDateTime.now());
        projectMapper.update(existing);
        return existing;
    }

    @Override
    public void updateStatus(Long id, String status, String userId) {
        if (!ALLOWED_STATUS.contains(status)) {
            throw new RuntimeException("非法的项目状态: " + status);
        }
        Project existing = projectMapper.findById(id);
        if (existing == null) {
            throw new RuntimeException("项目不存在");
        }
        if (!userId.equals(existing.getUserId())) {
            throw new RuntimeException("无权修改该项目");
        }
        if (status.equals(existing.getStatus())) {
            return; // 状态未变
        }
        validateTransition(existing.getStatus(), status);
        projectMapper.updateStatus(id, status);
    }

    /** 校验状态流转是否合法 */
    private void validateTransition(String from, String to) {
        Set<String> allowed = TRANSITIONS.get(from);
        if (allowed == null || !allowed.contains(to)) {
            throw new RuntimeException("不允许从状态[" + from + "]流转到[" + to + "]");
        }
    }

    @Override
    @Transactional
    public void deleteProject(Long id, String userId) {
        Project existing = projectMapper.findById(id);
        if (existing == null) {
            throw new RuntimeException("项目不存在");
        }
        if (!userId.equals(existing.getUserId())) {
            throw new RuntimeException("无权删除该项目");
        }
        projectAttachmentMapper.deleteByProjectId(id);
        projectMapper.deleteById(id);
    }

    @Override
    public Project getProject(Long id, String userId) {
        Project project = projectMapper.findById(id);
        if (project == null) {
            return null;
        }
        if (!userId.equals(project.getUserId())) {
            return null;
        }
        return project;
    }

    @Override
    public List<Project> getProjectsPage(String userId, String keyword, String status, int page, int size) {
        int offset = (page - 1) * size;
        return projectMapper.findByUserIdWithFilters(userId, keyword, status, offset, size);
    }

    @Override
    public int countProjects(String userId, String keyword, String status) {
        return projectMapper.countByUserIdWithFilters(userId, keyword, status);
    }

    @Override
    public List<Project> getAllProjects(String userId) {
        return projectMapper.findByUserId(userId);
    }

    @Override
    public List<WorkflowTask> getProjectTasks(Long projectId, String userId) {
        Project existing = projectMapper.findById(projectId);
        if (existing == null || !userId.equals(existing.getUserId())) {
            return new ArrayList<>();
        }
        List<WorkflowTask> list = workflowTaskMapper.findByProjectId(projectId);
        return list != null ? list : new ArrayList<>();
    }

    @Override
    @Transactional
    public void bindAttachments(Long projectId, List<Long> attachmentIds) {
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return;
        }
        for (Long attachmentId : attachmentIds) {
            projectAttachmentMapper.insert(projectId, attachmentId);
        }
    }

    @Override
    public void unbindAttachment(Long projectId, Long attachmentId) {
        projectAttachmentMapper.deleteByProjectIdAndAttachmentId(projectId, attachmentId);
    }

    @Override
    public List<ProjectAttachment> getProjectAttachments(Long projectId) {
        List<ProjectAttachment> list = projectAttachmentMapper.findByProjectId(projectId);
        return list != null ? list : new ArrayList<>();
    }
}
