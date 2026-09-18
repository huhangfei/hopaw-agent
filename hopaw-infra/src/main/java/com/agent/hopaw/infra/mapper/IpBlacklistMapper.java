package com.agent.hopaw.infra.mapper;

import com.agent.hopaw.infra.model.entity.IpBlacklist;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface IpBlacklistMapper {

    List<IpBlacklist> findAll();

    IpBlacklist findByIp(@Param("ip") String ip);

    int insert(IpBlacklist ipBlacklist);

    int deleteById(@Param("id") Long id);

    int countAll();
}
