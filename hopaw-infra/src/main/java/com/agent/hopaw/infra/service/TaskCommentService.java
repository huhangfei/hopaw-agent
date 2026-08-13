package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.mapper.TaskCommentMapper;
import com.agent.hopaw.infra.model.entity.TaskComment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class TaskCommentService implements ITaskCommentService {
    private static final Logger logger = LoggerFactory.getLogger(TaskCommentService.class);

    private final TaskCommentMapper taskCommentMapper;

    public TaskCommentService(TaskCommentMapper taskCommentMapper) {
        this.taskCommentMapper = taskCommentMapper;
    }

    @Override
    public TaskComment addComment(Long taskId, String content, String userId) {
        // 用户评论：评论者身份默认为 user
        return addComment(taskId, content, userId, "user", userId);
    }

    @Override
    public TaskComment addComment(Long taskId, String content, String userId, String commenterType, String commenterId) {
        TaskComment comment = new TaskComment();
        comment.setTaskId(taskId);
        comment.setContent(content);
        comment.setUserId(userId);
        comment.setCommenterType(commenterType);
        comment.setCommenterId(commenterId);
        comment.setCreateTime(LocalDateTime.now());
        taskCommentMapper.insert(comment);
        return comment;
    }

    @Override
    public void deleteComment(Long id, String userId) {
        TaskComment existing = taskCommentMapper.findById(id);
        if (existing == null) {
            throw new RuntimeException("评论不存在");
        }
        if (!userId.equals(existing.getUserId())) {
            throw new RuntimeException("无权删除该评论");
        }
        taskCommentMapper.deleteById(id);
    }

    @Override
    public List<TaskComment> getCommentsByTaskId(Long taskId) {
        List<TaskComment> list = taskCommentMapper.findByTaskId(taskId);
        return list != null ? list : new ArrayList<>();
    }
}
