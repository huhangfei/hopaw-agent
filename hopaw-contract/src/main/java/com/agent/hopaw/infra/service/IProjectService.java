package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.model.dto.FileTreeNode;
import com.agent.hopaw.infra.model.dto.FileUploadItem;
import com.agent.hopaw.infra.model.entity.Project;
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
    /** 查询项目空间文件树 */
    List<FileTreeNode> listProjectFiles(Long projectId, String userId);

    /**
     * 获取项目空间的绝对路径（用于任务提示词注入等场景）。
     * 存储层只保留相对路径，调用方需要绝对路径时通过本方法获取。
     */
    String getProjectSpaceAbsolutePath(Long projectId, String userId);

    /**
     * 在项目空间内创建文件或目录
     * @param relativePath 相对项目空间根目录的路径（如 "subdir/new.txt"）
     * @param isDirectory  true=创建目录，false=创建空文件
     * @return 创建后的文件树
     */
    List<FileTreeNode> createFileEntry(Long projectId, String userId, String relativePath, boolean isDirectory);

    /**
     * 删除项目空间内的文件或目录（目录递归删除）
     * @param relativePath 相对项目空间根目录的路径
     */
    void deleteFileEntry(Long projectId, String userId, String relativePath);

    /**
     * 移动或重命名项目空间内的文件/目录
     * @param fromPath 源相对路径
     * @param toPath   目标相对路径
     */
    void moveFileEntry(Long projectId, String userId, String fromPath, String toPath);

    /**
     * 批量上传文件到项目空间指定目录
     * @param targetDir 目标目录相对路径（空串表示根目录）
     * @param items     上传文件项列表
     * @return 上传后的文件树
     */
    List<FileTreeNode> uploadProjectFiles(Long projectId, String userId, String targetDir, List<FileUploadItem> items);
}
