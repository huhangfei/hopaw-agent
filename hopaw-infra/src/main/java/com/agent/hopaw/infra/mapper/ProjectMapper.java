package com.agent.hopaw.infra.mapper;

import com.agent.hopaw.infra.model.entity.Project;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProjectMapper {

    int insert(Project project);

    int update(Project project);

    int updateStatus(@Param("id") Long id, @Param("status") String status);

    /** 更新项目空间目录路径 */
    int updateSpaceDir(@Param("id") Long id, @Param("spaceDir") String spaceDir);

    int deleteById(@Param("id") Long id);

    Project findById(@Param("id") Long id);

    List<Project> findByUserIdWithFilters(@Param("userId") String userId,
                                          @Param("keyword") String keyword,
                                          @Param("status") String status,
                                          @Param("offset") int offset,
                                          @Param("size") int size);

    int countByUserIdWithFilters(@Param("userId") String userId,
                                 @Param("keyword") String keyword,
                                 @Param("status") String status);

    List<Project> findByUserId(@Param("userId") String userId);

    /** 查询启用自动迭代的进行中项目（已配置项目管理智能体） */
    List<Project> findAutoIterateProjects();

    /** 更新项目管理智能体会话编号 */
    int updateSessionId(@Param("id") Long id, @Param("sessionId") String sessionId);

    /** 按会话编号反查项目 */
    Project findBySessionId(@Param("sessionId") String sessionId);
}
