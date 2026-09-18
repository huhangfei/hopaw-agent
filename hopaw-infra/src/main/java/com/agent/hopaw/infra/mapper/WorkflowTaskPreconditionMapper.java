package com.agent.hopaw.infra.mapper;

import com.agent.hopaw.infra.model.entity.WorkflowTaskPrecondition;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 工作流任务前置条件 Mapper
 */
@Mapper
public interface WorkflowTaskPreconditionMapper {

    int insert(@Param("item") WorkflowTaskPrecondition item);

    /** 查询任务配置的前置条件（JOIN 前置任务取标题与当前状态） */
    List<WorkflowTaskPrecondition> findByTaskId(@Param("taskId") Long taskId);

    /** 删除任务配置的全部前置条件（保存前重建） */
    int deleteByTaskId(@Param("taskId") Long taskId);

    /** 删除以指定任务为前置的关联（前置任务被删除时清理，避免任务永久阻塞） */
    int deleteByPreTaskId(@Param("preTaskId") Long preTaskId);
}
