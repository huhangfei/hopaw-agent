package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.mapper.LoginLogMapper;
import com.agent.hopaw.infra.model.entity.LoginLog;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class LoginLogService implements ILoginLogService {

    private final LoginLogMapper loginLogMapper;

    public LoginLogService(LoginLogMapper loginLogMapper) {
        this.loginLogMapper = loginLogMapper;
    }

    @Override
    public void record(LoginLog log) {
        loginLogMapper.insert(log);
    }

    @Override
    public Map<String, Object> queryPage(String userId, String ip, String result, int page, int size) {
        int offset = (page - 1) * size;
        List<LoginLog> list = loginLogMapper.findPage(userId, ip, result, offset, size);
        int total = loginLogMapper.countPage(userId, ip, result);
        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", total);
        data.put("page", page);
        data.put("size", size);
        return data;
    }

    @Override
    public boolean isUserAllFailures(String userId, int count) {
        if (count <= 0) return false;
        List<LoginLog> recent = loginLogMapper.findRecentByUser(userId, count);
        if (recent.size() < count) return false;
        return recent.stream().allMatch(log -> "failed".equals(log.getResult()));
    }

    @Override
    public boolean isIpAllFailures(String ip, int count) {
        if (count <= 0) return false;
        List<LoginLog> recent = loginLogMapper.findRecentByIp(ip, count);
        if (recent.size() < count) return false;
        return recent.stream().allMatch(log -> "failed".equals(log.getResult()));
    }

    @Override
    public void clearAll() {
        loginLogMapper.deleteAll();
    }
}
