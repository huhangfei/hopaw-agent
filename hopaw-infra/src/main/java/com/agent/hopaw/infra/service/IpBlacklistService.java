package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.mapper.IpBlacklistMapper;
import com.agent.hopaw.infra.model.entity.IpBlacklist;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class IpBlacklistService implements IIpBlacklistService {

    private final IpBlacklistMapper ipBlacklistMapper;

    public IpBlacklistService(IpBlacklistMapper ipBlacklistMapper) {
        this.ipBlacklistMapper = ipBlacklistMapper;
    }

    @Override
    public List<IpBlacklist> listAll() {
        return ipBlacklistMapper.findAll();
    }

    @Override
    public boolean isBlocked(String ip) {
        return ipBlacklistMapper.findByIp(ip) != null;
    }

    @Override
    public IpBlacklist add(String ip, String remark) {
        if (ipBlacklistMapper.findByIp(ip) != null) {
            return null;
        }
        IpBlacklist entry = new IpBlacklist();
        entry.setIp(ip);
        entry.setRemark(remark);
        ipBlacklistMapper.insert(entry);
        return entry;
    }

    @Override
    public void remove(Long id) {
        ipBlacklistMapper.deleteById(id);
    }
}
