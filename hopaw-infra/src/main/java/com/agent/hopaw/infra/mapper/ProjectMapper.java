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
}
