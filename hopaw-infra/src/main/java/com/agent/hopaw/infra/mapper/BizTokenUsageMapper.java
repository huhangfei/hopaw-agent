package com.agent.hopaw.infra.mapper;

import com.agent.hopaw.infra.model.entity.BizTokenUsage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BizTokenUsageMapper {

    int insert(BizTokenUsage usage);

    /** 项目维度汇总 */
    BizTokenUsage summaryByProject(@Param("projectId") Long projectId);

    /** 任务维度汇总 */
    BizTokenUsage summaryByTask(@Param("taskId") Long taskId);

    /** 项目维度最近记录（柱状图用） */
    List<BizTokenUsage> listByProject(@Param("projectId") Long projectId, @Param("limit") int limit);

    /** 任务维度最近记录（柱状图用） */
    List<BizTokenUsage> listByTask(@Param("taskId") Long taskId, @Param("limit") int limit);
}
