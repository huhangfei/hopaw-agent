package com.agent.hopaw.infra.mapper;

import com.agent.hopaw.infra.model.entity.McpServerConfig;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface McpServerConfigMapper {
    List<McpServerConfig> findAll();
    List<McpServerConfig> findByEnabled(Integer enabled);
    McpServerConfig findById(Long id);
    int insert(McpServerConfig config);
    int update(McpServerConfig config);
    int deleteById(Long id);
}