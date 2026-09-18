package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.mapper.McpServerConfigMapper;
import com.agent.hopaw.infra.model.entity.McpServerConfig;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class McpServerConfigService implements IMcpServerConfigService {

    private final McpServerConfigMapper mapper;

    public McpServerConfigService(McpServerConfigMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<McpServerConfig> findAll() {
        return mapper.findAll();
    }

    @Override
    public List<McpServerConfig> findEnabled() {
        return mapper.findByEnabled(1);
    }

    @Override
    public McpServerConfig findById(Long id) {
        return mapper.findById(id);
    }

    @Override
    @Transactional
    public void insert(McpServerConfig config) {
        if (config.getEnabled() == null) {
            config.setEnabled(0);
        }
        mapper.insert(config);
    }

    @Override
    @Transactional
    public void update(McpServerConfig config) {
        mapper.update(config);
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        mapper.deleteById(id);
    }

    @Override
    @Transactional
    public void setEnabled(Long id, Integer enabled) {
        McpServerConfig config = mapper.findById(id);
        if (config != null) {
            config.setEnabled(enabled);
            mapper.update(config);
        }
    }
}