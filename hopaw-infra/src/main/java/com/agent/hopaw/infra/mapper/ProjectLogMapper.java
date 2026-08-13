package com.agent.hopaw.infra.mapper;

import com.agent.hopaw.infra.model.entity.ProjectLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProjectLogMapper {

    int insert(ProjectLog log);

    /** 按项目ID查询日志，按创建时间正序（最早在前） */
    List<ProjectLog> findByProjectId(@Param("projectId") Long projectId);
}
