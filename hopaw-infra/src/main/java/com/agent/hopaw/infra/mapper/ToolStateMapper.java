package com.agent.hopaw.infra.mapper;

import com.agent.hopaw.infra.model.entity.ToolState;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ToolStateMapper {

    List<ToolState> findAll();

    /** 存在则更新启用状态，不存在则插入。 */
    int upsert(ToolState state);

    /** 删除某个工具集的全部状态（工具集级 + 其下所有方法级），卸载时调用。 */
    int deleteByToolSetName(@Param("toolSetName") String toolSetName);

    /** 删除某个工具集的单个方法级状态（置回工具集级默认时也可用）。 */
    int deleteByToolSetNameAndToolName(@Param("toolSetName") String toolSetName,
                                       @Param("toolName") String toolName);
}
