package com.agent.hopaw.infra.mapper;

import com.agent.hopaw.infra.model.entity.SysConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SysConfigMapper {
    List<SysConfig> findAll();

    SysConfig findByKey(@Param("key") String key);

    List<SysConfig> findByKeys(@Param("keys") List<String> keys);

    int insert(SysConfig sysConfig);

    int update(SysConfig sysConfig);

    int deleteById(@Param("id") Long id);

    int deleteByKey(@Param("key") String key);
}
