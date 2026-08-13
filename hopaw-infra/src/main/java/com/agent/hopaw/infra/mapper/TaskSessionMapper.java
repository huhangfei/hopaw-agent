package com.agent.hopaw.infra.mapper;

import com.agent.hopaw.infra.model.entity.TaskSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TaskSessionMapper {

    int insert(@Param("taskId") Long taskId, @Param("sessionId") String sessionId);

    List<TaskSession> findByTaskId(@Param("taskId") Long taskId);

    Long findTaskIdBySessionId(@Param("sessionId") String sessionId);

    int deleteByTaskId(@Param("taskId") Long taskId);
}
