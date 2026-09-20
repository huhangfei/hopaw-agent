package com.agent.hopaw.infra.mapper;

import com.agent.hopaw.infra.model.entity.PluginState;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PluginStateMapper {

    List<PluginState> findAll();

    PluginState findByPluginId(@Param("pluginId") String pluginId);

    /** 存在则更新启用状态，不存在则插入。 */
    int upsert(PluginState state);

    int deleteByPluginId(@Param("pluginId") String pluginId);
}
