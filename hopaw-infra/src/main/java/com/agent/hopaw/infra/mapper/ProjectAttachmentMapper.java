package com.agent.hopaw.infra.mapper;

import com.agent.hopaw.infra.model.entity.ProjectAttachment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProjectAttachmentMapper {

    int insert(@Param("projectId") Long projectId, @Param("attachmentId") Long attachmentId);

    int deleteByProjectIdAndAttachmentId(@Param("projectId") Long projectId, @Param("attachmentId") Long attachmentId);

    List<ProjectAttachment> findByProjectId(@Param("projectId") Long projectId);

    int deleteByProjectId(@Param("projectId") Long projectId);
}
