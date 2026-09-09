package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.model.entity.LoginLog;

import java.util.List;
import java.util.Map;

public interface ILoginLogService {

    void record(LoginLog log);

    Map<String, Object> queryPage(String userId, String ip, String result, int page, int size);

    /** 查询用户最近N条记录，判断是否全部失败 */
    boolean isUserAllFailures(String userId, int count);

    /** 查询IP最近N条记录，判断是否全部失败 */
    boolean isIpAllFailures(String ip, int count);

    void clearAll();
}
