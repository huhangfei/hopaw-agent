package com.agent.hopaw.service;

import com.agent.hopaw.mapper.SysConfigMapper;
import com.agent.hopaw.model.SysConfig;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SysConfigService {

    private final SysConfigMapper sysConfigMapper;

    public SysConfigService(SysConfigMapper sysConfigMapper) {
        this.sysConfigMapper = sysConfigMapper;
    }

    public List<SysConfig> getAll() {
        return sysConfigMapper.findAll();
    }

    public SysConfig getByKey(String key) {
        return sysConfigMapper.findByKey(key);
    }

    public int save(SysConfig sysConfig) {
        return sysConfigMapper.insert(sysConfig);
    }

    public int update(SysConfig sysConfig) {
        return sysConfigMapper.update(sysConfig);
    }

    public int deleteById(Long id) {
        return sysConfigMapper.deleteById(id);
    }

    public int deleteByKey(String key) {
        return sysConfigMapper.deleteByKey(key);
    }
}
