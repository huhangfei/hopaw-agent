package com.agent.hopaw.infra.mapper;

import com.agent.hopaw.infra.model.entity.WorkflowTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface WorkflowTaskMapper {

    int insert(WorkflowTask task);

    int update(WorkflowTask task);

    int deleteById(@Param("id") Long id);

    WorkflowTask findById(@Param("id") Long id);

    List<WorkflowTask> findByUserIdAndStatus(@Param("userId") String userId,
                                             @Param("status") String status);

    List<WorkflowTask> findPendingExecution();

    int updateStatus(@Param("id") Long id,
                     @Param("status") String status,
                     @Param("rejectReason") String rejectReason);

    List<WorkflowTask> findByUserIdWithFilters(@Param("userId") String userId,
                                               @Param("keyword") String keyword,
                                               @Param("status") String status,
                                               @Param("projectId") Long projectId,
                                               @Param("agentId") Long agentId,
                                               @Param("offset") int offset,
                                               @Param("size") int size);

    int countByUserIdWithFilters(@Param("userId") String userId,
                                 @Param("keyword") String keyword,
                                 @Param("status") String status,
                                 @Param("projectId") Long projectId,
                                 @Param("agentId") Long agentId);

    List<WorkflowTask> findByProjectId(@Param("projectId") Long projectId);
}
