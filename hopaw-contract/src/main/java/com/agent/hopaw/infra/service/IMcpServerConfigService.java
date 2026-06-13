package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.model.entity.McpServerConfig;

import java.util.List;

public interface IMcpServerConfigService {
    List<McpServerConfig> findAll();
    List<McpServerConfig> findEnabled();
    McpServerConfig findById(Long id);
    void insert(McpServerConfig config);
    void update(McpServerConfig config);
    void deleteById(Long id);
    void setEnabled(Long id, Integer enabled);
}