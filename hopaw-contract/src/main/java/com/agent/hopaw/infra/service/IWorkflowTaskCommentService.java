package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.model.entity.WorkflowTaskComment;

import java.util.List;

/**
 * 任务评论服务接口
 */
public interface IWorkflowTaskCommentService {
    /**
     * 用户添加评论
     */
    WorkflowTaskComment addComment(Long taskId, String content, String userId);

    /**
     * 添加评论（区分评论者身份）
     * @param taskId 任务ID
     * @param content 评论内容
     * @param userId 任务所属用户ID（用于权限校验和数据归属）
     * @param commenterType 评论者类型：agent / user
     * @param commenterId 评论者编号：智能体ID 或 用户ID
     */
    WorkflowTaskComment addComment(Long taskId, String content, String userId, String commenterType, String commenterId);

    /**
     * 添加评论（区分评论者身份和评论类型）
     * @param commentType 评论类型：default=普通 / summary=总结
     */
    WorkflowTaskComment addComment(Long taskId, String content, String userId, String commenterType, String commenterId, String commentType);

    void deleteComment(Long id, String userId);

    /**
     * 更新评论类型（default / summary 互转）
     *
     * @param id          评论ID
     * @param commentType 目标评论类型
     * @param userId      当前用户ID（用于权限校验）
     * @throws RuntimeException 评论不存在或无权操作时抛出
     */
    void updateCommentType(Long id, String commentType, String userId);

    /**
     * 查询任务全部评论（按时间正序）
     */
    List<WorkflowTaskComment> getCommentsByTaskId(Long taskId);

    /**
     * 查询任务下待处理评论（status=pending）
     */
    List<WorkflowTaskComment> getPendingCommentsByTaskId(Long taskId);

    /**
     * 将指定评论ID批量标记为已处理（status=processed）
     */
    void markCommentsAsProcessed(List<Long> commentIds);
}
