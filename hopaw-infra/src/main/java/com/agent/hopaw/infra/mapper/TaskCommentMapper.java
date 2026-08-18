package com.agent.hopaw.infra.mapper;

import com.agent.hopaw.infra.model.entity.TaskComment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TaskCommentMapper {

    int insert(TaskComment comment);

    TaskComment findById(@Param("id") Long id);

    int deleteById(@Param("id") Long id);

    List<TaskComment> findByTaskId(@Param("taskId") Long taskId);

    /**
     * 查询任务下指定状态的评论
     */
    List<TaskComment> findByTaskIdAndStatus(@Param("taskId") Long taskId, @Param("status") String status);

    /**
     * 按评论ID批量更新状态
     */
    int updateStatusByIds(@Param("ids") List<Long> ids, @Param("status") String status);
}
