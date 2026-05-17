package com.agent.hopaw.infra.mapper;

import com.agent.hopaw.infra.model.entity.ScheduledTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ScheduledTaskMapper {
    List<ScheduledTask> findAll();
    ScheduledTask findById(Long id);
    ScheduledTask findByTaskType(String taskType);
    List<ScheduledTask> findByUserId(String userId);
    List<ScheduledTask> findByAgentId(String agentId);
    List<ScheduledTask> findByUserIdAndAgentId(@Param("userId") String userId, @Param("agentId") String agentId);
    void insert(ScheduledTask task);
    void update(ScheduledTask task);
    void deleteById(Long id);
}
