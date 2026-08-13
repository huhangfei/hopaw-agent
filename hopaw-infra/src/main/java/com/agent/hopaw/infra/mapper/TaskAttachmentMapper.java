package com.agent.hopaw.infra.mapper;

import com.agent.hopaw.infra.model.entity.TaskAttachment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TaskAttachmentMapper {

    int insert(@Param("taskId") Long taskId, @Param("attachmentId") Long attachmentId);

    int deleteByTaskIdAndAttachmentId(@Param("taskId") Long taskId, @Param("attachmentId") Long attachmentId);

    List<TaskAttachment> findByTaskId(@Param("taskId") Long taskId);

    int deleteByTaskId(@Param("taskId") Long taskId);
}
