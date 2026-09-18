package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.model.entity.IpBlacklist;

import java.util.List;

public interface IIpBlacklistService {

    List<IpBlacklist> listAll();

    boolean isBlocked(String ip);

    IpBlacklist add(String ip, String remark);

    void remove(Long id);
}
