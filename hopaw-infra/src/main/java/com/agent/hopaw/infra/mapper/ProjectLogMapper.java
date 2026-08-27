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

    /** 按项目ID和日志类型查询日志，按创建时间正序（最早在前） */
    List<ProjectLog> findByProjectIdAndLogType(@Param("projectId") Long projectId, @Param("logType") String logType);

    /** 按日志ID查询 */
    ProjectLog findById(@Param("id") Long id);

    /** 更新日志类型 */
    int updateLogType(@Param("id") Long id, @Param("logType") String logType);

    /** 按日志ID删除 */
    int deleteById(@Param("id") Long id);
}
