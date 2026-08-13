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
}
