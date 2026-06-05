package com.agent.hopaw.infra.mapper;

import com.agent.hopaw.infra.model.entity.TtsConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TtsConfigMapper {

    List<TtsConfig> findAll();

    TtsConfig findByVendorCode(@Param("vendorCode") String vendorCode);

    TtsConfig findById(@Param("id") Long id);

    int insert(TtsConfig config);

    int update(TtsConfig config);

    int deleteById(@Param("id") Long id);

    TtsConfig findEnabledByUserId(@Param("userId") String userId);
}