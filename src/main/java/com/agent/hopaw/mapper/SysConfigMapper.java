package com.agent.hopaw.mapper;

import com.agent.hopaw.model.SysConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SysConfigMapper {
    List<SysConfig> findAll();

    SysConfig findByKey(@Param("key") String key);

    int insert(SysConfig sysConfig);

    int update(SysConfig sysConfig);

    int deleteById(@Param("id") Long id);

    int deleteByKey(@Param("key") String key);
}
